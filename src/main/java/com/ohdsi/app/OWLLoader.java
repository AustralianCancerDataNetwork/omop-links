package com.ohdsi.app;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.apache.commons.cli.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

/*
    todo: this is just a proof of concept. unless we change it to be backed by a database instead of csv files,
          it will be rapidly intractable for more than a handful of vocabs.

    suggested plan:
    0?  review skos and/or rdfs property classes to see if we are better off reusing some of them instead
        of the bespoke ones defined in the property and annotation config files
            - e.g. rdfs:domain could maybe be used for domain and rdfs:type for concept_class
            - possible uses of container classes as well to help with grounding steps?
    1.  remove reference to csv files in favour of postgres version
    2.  create a separate ohdsi ontology that holds all metadata-relevant classes ('in_domain', 'has_class' etc.)
    3.  a) create separate omop_[vocab_id] ontology files referencing one per vocabulary, added as cmdline args
        b) this would also make the in_vocab property redundant so could clean that up
        (ETA: figured out how to condense to prefix at this point now (see: semsql/builder/prefixes/prefixes.csv and
        semsql/builder/prefixes/prefixes_local.csv), but still TBC if we want OMOP-specific prefixes or revert to public
        ones if available?)
        - the file split step is a hard requirement once more than a handful of vocabs in scope - maxing out at current
          vocab list (7 vocabularies - could make bigger owl file currently ~2.8GB, but semSQL conversion approx 10GB and
          crashes Java even with large dedicated resources)
    4.  optionally merge files post-hoc
    5?  can we integrate this with semsql to make a single pipeline instead of managing multiple dependencies to produce the end-goal?
    6?  not super necessary but would be nice to refactor all the writer classes to a common base, as there's a lot of
        redundancy in passing all the object handles around...

    for later context - to run:
        - mvn compile exec:java -Dexec.mainClass="com.ohdsi.app.OWLLoader" -Dexec.args="-d [outdir] -f [outfile] -v [folder with athena files] -r"
    semsql to convert:
        - cp [outfile] [semantic-sql/data]
        - cd semantic-sql
        - poetry env activate
        - semsql make with_codes.db (once larger: ROBOT_JAVA_ARGS='-Xmx24G' semsql make with_loinc_icd_rxnorm.db)
*/
public class OWLLoader {
    public static final IRI omop_iri = IRI.create("https://athena.ohdsi.org/search-terms/terms/");
    private final OWLOntologyManager manager;
    private final OMOPMetadataClasses metadata;
    private final OMOPConcepts concepts;
    private final OMOPAncestry ancestry;
    private final OMOPRelationships relationships;
    private final OMOPSynonyms synonyms;
    private final IRI documentIRI;
    private final OWLOntology o;
    private final PrefixDocumentFormat format;
    private final Map<String, IRI> vocabBaseIRIs = new HashMap<>();

    public OWLLoader(String outdir_path, String outfile_name, String vocab_folder) throws OWLOntologyCreationException {
        this.manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory dataFactory = this.manager.getOWLDataFactory();
        File outdir = new File(outdir_path);
        if (!outdir.exists()) {
            outdir.mkdirs(); // create folder if it doesn't exist
        }
        File outfile = new File(outdir, outfile_name);
        this.documentIRI = IRI.create(outfile);
        SimpleIRIMapper mapper = new SimpleIRIMapper(omop_iri, this.documentIRI);
        this.manager.addIRIMapper(mapper);
        this.manager.addIRIMapper(new AutoIRIMapper(outdir, true));
        this.o = this.manager.createOntology(omop_iri);
        this.format = (PrefixDocumentFormat) manager.getOntologyFormat(o);

        // adding skos prefix so that we can use preferred label / alt label for synonyms
        assert format != null;
        format.setPrefix("skos", "http://www.w3.org/2004/02/skos/core#");

        // **** usage notes ****
        // concept_name is encoded as rdfs:label
        //     <rdfs:label xml:lang="en">Pathological fracture in other disease, left hand</rdfs:label>
        // synonyms end up as skos:altLabel
        //     <skos:altLabel>synonym string</skos:altLabel>
        // concept_code is skos:exactMatch with originating vocab prefix
        //     <skos:exactMatch rdf:resource="http://purl.bioontology.org/ontology/ICD10CM/M84.642"/>
        // maps to relationships also skos:exactMatch but now with omop prefix for concept_id
        //     <skos:exactMatch rdf:resource="https://athena.ohdsi.org/search-terms/terms/4118017"/>
        // ancestry is a direct rdfs:subClassOf
        //     <rdfs:subClassOf rdf:resource="https://athena.ohdsi.org/search-terms/terms/19000637"/>
        // where in_class, in_domain and in_vocabulary are instead subclassed with constraining properties
        // <rdfs:subClassOf>
        //     <owl:Restriction>
        //         <owl:onProperty rdf:resource="https://athena.ohdsi.org/search-terms/terms/in_class"/>
        //         <owl:someValuesFrom rdf:resource="https://athena.ohdsi.org/search-terms/terms/44819063"/>
        //     </owl:Restriction>
        // </rdfs:subClassOf>

        // adding all the target vocabs as prefixes so that we can manage concept codes in their own specific vocabularies
        List<String> target_vocabs = Arrays.asList("SNOMED", "HemOnc", "ICDO3", "Cancer Modifier", "NCIt", "LOINC", "ICD10CM", "RxNorm");
        // todo: if we decide to make an omop per-vocab file structure, we may revert these and use prefixes like omop.loinc etc? tbd
        Map<String, String> existing_iris = Map.ofEntries(
                Map.entry("loinc", "https://loinc.org/"),
                Map.entry("icd10cm", "http://purl.bioontology.org/ontology/ICD10CM/"),
                Map.entry("ncit", "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#"),
                Map.entry("rxnorm", "http://purl.bioontology.org/ontology/RXNORM/"),
                Map.entry("snomed","http://snomed.info/id/")
        );
        for (String vocab : target_vocabs) {
            String key = vocab.replace(" ", "_").toLowerCase();
            String base = "http://www.example.org/" + key + "/";
            if (existing_iris.containsKey(key)) {
                base = existing_iris.get(key);
            }
            format.setPrefix(key, base);
            vocabBaseIRIs.put(key, IRI.create(base));
        }
        manager.setOntologyFormat(o, format);

        this.metadata = new OMOPMetadataClasses(o, dataFactory, vocab_folder, omop_iri);
        try {
            metadata.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.concepts = new OMOPConcepts(o, dataFactory, vocab_folder, format, manager, metadata, omop_iri, vocabBaseIRIs, target_vocabs);
        this.ancestry = new OMOPAncestry(this.o, dataFactory, vocab_folder);
        this.synonyms = new OMOPSynonyms(this.o, dataFactory, vocab_folder, format);
        this.relationships = new OMOPRelationships(this.o, dataFactory, vocab_folder, format);
    }

    public OWLOntology createOHDSIOntology() throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
        System.out.println("Saving ontology to: " + documentIRI);
        concepts.load();
        ancestry.load(concepts);
        relationships.load(concepts, metadata);
        synonyms.load(concepts);
        manager.saveOntology(o, format, documentIRI);
        return o;
    }

    public static void checkRequiredFiles(File folder, String[] requiredFiles) {
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Provided path is not a valid directory: " + folder.getAbsolutePath());
            System.exit(1);
        }

        for (String filename : requiredFiles) {
            File file = new File(folder, filename);
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                System.err.println("Required file missing or not readable: " + filename);
                System.exit(1);
            }
        }
        System.out.println("All required files are present and readable.");
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption("d", "outdir", true, "Output directory");
        options.addOption("f", "outfile", true, "Output file name");
        options.addOption("v", "vocab", true, "Vocabulary folder");
        options.addOption("r", "recreate", false, "Recreate OWL classes");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (!cmd.hasOption("d") || !cmd.hasOption("f") || !cmd.hasOption("v")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("OWLLoader", options);
            System.exit(1);
        }

        String outdir = cmd.getOptionValue("d");
        String outfile = cmd.getOptionValue("f");
        String vocabFolder = cmd.getOptionValue("v");
        boolean recreate = cmd.hasOption("r");

        String[] required = {
                "VOCABULARY.csv",
                "DOMAIN.csv",
                "CONCEPT_CLASS.csv",
                "RELATIONSHIP.csv",
                "CONCEPT.csv",
                "CONCEPT_ANCESTOR.csv",
                "CONCEPT_SYNONYM.csv"
        };
        checkRequiredFiles(new File(vocabFolder), required);

        com.ohdsi.app.OWLLoader loader = new com.ohdsi.app.OWLLoader(outdir, outfile, vocabFolder);

        OWLOntology onto = null;
        try {
            if (recreate) {
                onto = loader.createOHDSIOntology();
            }
        } catch (OWLOntologyStorageException | OWLOntologyCreationException | IOException ex) {
            System.err.println(ex.getMessage());
        } finally {
            if (onto == null) {
                System.out.println("Nothing to do here...");
            }
        }
    }
}


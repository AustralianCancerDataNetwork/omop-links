package com.ohdsi.app;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.apache.commons.cli.*;
import org.apache.commons.csv.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.stream.Stream;


public class OWLLoader {
    public static final IRI omop_iri = IRI.create("https://athena.ohdsi.org/search-terms/terms/omop#");
    private OWLOntologyManager manager;
    private OWLDataFactory dataFactory;
    private OMOPMetadataClasses metadata;
    private OMOPConcepts concepts;
    private OMOPRelationships relationships;
    private File outfile;
    private File outdir;
    private String vocab_folder;
    private IRI documentIRI;
    private SimpleIRIMapper mapper;
    private OWLOntology o;

    public OWLLoader(String outdir_path, String outfile_name, String vocab_folder) throws OWLOntologyCreationException {
        this.manager = OWLManager.createOWLOntologyManager();
        this.dataFactory = this.manager.getOWLDataFactory();
        this.outdir = new File(outdir_path);
        this.vocab_folder = vocab_folder;
        if (!this.outdir.exists()) {
            this.outdir.mkdirs(); // create folder if it doesn't exist
        }
        this.outfile = new File(this.outdir, outfile_name);
        this.documentIRI = IRI.create(this.outfile);
        this.mapper = new SimpleIRIMapper(this.omop_iri, this.documentIRI);
        this.manager.addIRIMapper(this.mapper);
        this.manager.addIRIMapper(new AutoIRIMapper(this.outdir, true));
        this.o = this.manager.createOntology(this.omop_iri);
        this.metadata = new OMOPMetadataClasses(this.o, this.dataFactory, this.vocab_folder, this.manager, this.omop_iri);
        this.concepts = new OMOPConcepts(this.o, this.dataFactory, this.vocab_folder, this.manager, this.omop_iri);
        this.relationships = new OMOPRelationships(this.o, this.dataFactory, this.vocab_folder, this.manager, this.omop_iri);
    }

    public OWLOntology createOHDSIOntology() throws OWLOntologyStorageException, OWLOntologyCreationException, IOException  {

        System.out.println("Saving ontology to: " + documentIRI);

        metadata.load();
        concepts.load(metadata);
        relationships.load(concepts, metadata);
        manager.saveOntology(o, documentIRI);
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

        String[] required = {"VOCABULARY.csv", "DOMAIN.csv", "CONCEPT_CLASS.csv", "RELATIONSHIP.csv", "CONCEPT.csv", "CONCEPT_ANCESTOR.csv"};
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

        public static void printHierarchy(OWLReasoner reasoner, OWLClass owlClass, int level, Set<OWLClass> visited) {
            if (!visited.contains(owlClass) && reasoner.isSatisfiable(owlClass)) {
                visited.add(owlClass);
                for (int i = 0; i < level * 4; i++) {
                    System.out.print(" ");
                }
                System.out.println(labelFor(owlClass, reasoner.getRootOntology()));

                NodeSet<OWLClass> classNodeSet = reasoner.getSubClasses(owlClass, true);
                for (OWLClass child: classNodeSet.getFlattened()) {
                    printHierarchy(reasoner, child, level+1, visited);
                }
            }
        }

        private static String labelFor(OWLClass clazz, OWLOntology o) {
            OWLAnnotationObjectVisitorEx<String> visitor = new OWLAnnotationObjectVisitorEx<String>() {
                String value;
                @Override
                public String visit(OWLAnnotation node) {
                    if (node.getProperty().isLabel()) {
                        return ((OWLLiteral) node.getValue()).getLiteral();
                    }
                    return null;
                }
            };
            return EntitySearcher.getAnnotations(clazz, o)
                    .map(anno -> anno.accept(visitor))
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(clazz.getIRI().toString());
        }
    }


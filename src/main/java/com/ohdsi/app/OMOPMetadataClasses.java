package com.ohdsi.app;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;

import org.semanticweb.owlapi.model.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class OMOPMetadataClasses {
    Map<String, Map<String, OWLClass>> owlClassLookup = new HashMap<>();
    private final String vocab_folder;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;
    private final Map<String, MetadataConfig> refLookup;
    private final IRI omop_iri;
    // private final OWLOntologyManager manager;

    public OMOPMetadataClasses(OWLOntology ontology,
                               OWLDataFactory dataFactory,
                               String vocab_folder,
                               IRI omop_iri){
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.refLookup = new LinkedHashMap<>();
        this.omop_iri = omop_iri;

//        nixed this in preference for adding the prefix in the main load script but leaving
//        these definitions here (including manager instantiation...) if we decide to do
//        some imports down the line just so we don't have to figure it out again :)
//        IRI skos_iri = IRI.create("http://www.w3.org/2004/02/skos/core");
//        OWLImportsDeclaration imports_declaration = dataFactory.getOWLImportsDeclaration(skos_iri);
//        manager.applyChange(new AddImport(ontology, imports_declaration));
//                OWLAnnotationProperty prefLabel = dataFactory.getOWLAnnotationProperty("skos:prefLabel", pm);

        refLookup.put("domain", new MetadataConfig("DOMAIN.csv", "domain_concept_id", "domain_name", "domain_id"));
        refLookup.put("concept_class", new MetadataConfig("CONCEPT_CLASS.csv", "concept_class_concept_id", "concept_class_name", "concept_class_id"));
        refLookup.put("relationship", new MetadataConfig("RELATIONSHIP.csv", "relationship_concept_id", "relationship_name", "relationship_id"));
        refLookup.put("vocabulary", new MetadataConfig("VOCABULARY.csv", "vocabulary_concept_id", "vocabulary_name", "vocabulary_id"));
    }

    public void load() throws IOException {
        System.out.println("Creating OWL classes for OMOP metadata types");
        for (Map.Entry<String, MetadataConfig> entry : refLookup.entrySet()) {
            String className = entry.getKey();
            MetadataConfig config = entry.getValue();
            Map<String, OWLClass> inner_map = new HashMap<>();

            System.out.println("Reading " + config.filename + "...");
            File targetFile = new File(vocab_folder, config.filename);
            CSVChunkIterable iterable = new CSVChunkIterable(targetFile, 1000);

            OWLClass c = dataFactory.getOWLClass(omop_iri + className);
            OWLDeclarationAxiom da = dataFactory.getOWLDeclarationAxiom(c);
            ontology.add(da);

            for (List<Map<String, String>> chunk : iterable) {
                if (!chunk.isEmpty()) {
                    for (Map<String, String> row : chunk) {
                        OWLClass v = dataFactory.getOWLClass(omop_iri +  row.get(config.conceptIdColumn));
                        ontology.add(dataFactory.getOWLSubClassOfAxiom(v, c));
                        OWLAnnotation lab = dataFactory.getOWLAnnotation(
                                dataFactory.getRDFSLabel(),
                                dataFactory.getOWLLiteral(row.get(config.idColumn), "en")
                        );
                        OWLAxiom ax1 = dataFactory.getOWLAnnotationAssertionAxiom(v.getIRI(), lab);
                        ontology.add(ax1);
                        inner_map.put(row.get(config.idColumn), v);
                    }
                }
            }
            owlClassLookup.put(className, inner_map);
        }
    }

    public OWLClass getByLabel(String label, String family) {
        Map<String, OWLClass> c = owlClassLookup.get(family);
        if (c != null) {
            return c.get(label);
        }
        return null;
    }

    public Map<String, OWLClass> getFamily(String family) {
        return owlClassLookup.get(family);
    }
}
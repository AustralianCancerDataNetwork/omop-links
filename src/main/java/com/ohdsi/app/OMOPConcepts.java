package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;

import org.apache.commons.cli.*;
import org.apache.commons.csv.*;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class OMOPConcepts {

    private final Map<String, OWLClass> idToClass = new HashMap<>();
    private String vocab_folder;
    private OWLOntology ontology;
    private OWLDataFactory dataFactory;
    private OWLOntologyManager manager;
    private IRI omop_iri;

    public OMOPConcepts(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder, OWLOntologyManager manager, IRI omop_iri) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.manager = manager;
        this.omop_iri = omop_iri;
    }

    public void load(OMOPMetadataClasses metadata) throws IOException {
        System.out.println("Creating OWL classes for OMOP concepts");

        int chunkSize = 1000;
        System.out.println("Reading CONCEPT.csv...");
        File conceptFile = new File(vocab_folder, "CONCEPT.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptFile, chunkSize);

        OWLObjectProperty in_vocabulary = dataFactory.getOWLObjectProperty(omop_iri + "in_vocabulary");
        OWLObjectProperty in_class = dataFactory.getOWLObjectProperty(omop_iri + "in_class");
        OWLObjectProperty in_domain = dataFactory.getOWLObjectProperty(omop_iri + "in_domain");
        OWLAnnotationProperty hasCode = dataFactory.getOWLAnnotationProperty(omop_iri + "has_code");

        // this whole thing feels kind of brute force - divide and conquer on vocabs with some pre-processing?
        List<String> target_vocabs = Arrays.asList("SNOMED", "HemOnc", "ICDO3", "Cancer Modifier");

        // ok at least if we keep the metadata concepts in memory then the mappings by class become tractable...
        Map<String, OWLClass> vv = metadata.getFamily("vocabulary");
        Map<String, OWLClass> cc = metadata.getFamily("concept_class");
        Map<String, OWLClass> dd = metadata.getFamily("domain");

        for (List<Map<String, String>> chunk : iterable) {
            if (!chunk.isEmpty()) {
                for (Map<String, String> row : chunk) {
                    if (target_vocabs.contains(row.get("vocabulary_id"))) {

                        OWLClass concept = dataFactory.getOWLClass(
                                IRI.create(
                                        omop_iri +
                                        row.get("vocabulary_id").replace(" ", "_").toLowerCase() +
                                        "_" + row.get("concept_id")
                                )
                        );

                        OWLClass voc = vv.get(row.get("vocabulary_id"));
                        OWLClass ccl = cc.get(row.get("concept_class_id"));
                        OWLClass dom = dd.get(row.get("domain_id"));

                        OWLSubClassOfAxiom v_ax = dataFactory.getOWLSubClassOfAxiom(
                                concept,
                                dataFactory.getOWLObjectSomeValuesFrom(in_vocabulary, voc)
                        );
                        OWLSubClassOfAxiom c_ax = dataFactory.getOWLSubClassOfAxiom(
                                concept,
                                dataFactory.getOWLObjectSomeValuesFrom(in_class, ccl)
                        );
                        OWLSubClassOfAxiom d_ax = dataFactory.getOWLSubClassOfAxiom(
                                concept,
                                dataFactory.getOWLObjectSomeValuesFrom(in_domain, dom)
                        );
                        ontology.add(v_ax);
                        ontology.add(c_ax);
                        ontology.add(d_ax);
                        OWLAnnotation lab = dataFactory.getOWLAnnotation(
                                dataFactory.getRDFSLabel(),
                                dataFactory.getOWLLiteral(row.get("concept_name"), "en")
                        );
                        OWLAnnotation code = dataFactory.getOWLAnnotation(
                                hasCode,
                                dataFactory.getOWLLiteral(row.get("concept_code"))
                        );
                        OWLAxiom lab_ax = dataFactory.getOWLAnnotationAssertionAxiom(concept.getIRI(), lab);
                        OWLAxiom code_ax = dataFactory.getOWLAnnotationAssertionAxiom(concept.getIRI(), code);
                        manager.applyChange(new AddAxiom(ontology, lab_ax));
                        manager.applyChange(new AddAxiom(ontology, code_ax));
                        idToClass.put(row.get("concept_id"), concept);
                    }
                }
            }
        }
        System.out.println("Number of items in the map: " + idToClass.size());
    }

    public OWLClass getByID(String id) {
        return idToClass.get(id);
    }
}

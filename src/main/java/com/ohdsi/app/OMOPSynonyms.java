package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OMOPSynonyms {
    private final String vocab_folder;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;
    private final OWLAnnotationProperty synonym_of;

    public OMOPSynonyms(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder, PrefixDocumentFormat pm) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.synonym_of = dataFactory.getOWLAnnotationProperty("skos:altLabel", pm);
    }

    public void load(OMOPConcepts concepts) throws IOException {
        System.out.println("Creating alternative labels for OMOP synonyms");
        int chunkSize = 1000;
        System.out.println("Reading CONCEPT_SYNONYM.csv...");
        File conceptSynonymFile = new File(vocab_folder, "CONCEPT_SYNONYM.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptSynonymFile, chunkSize);

        for (List<Map<String, String>> chunk : iterable) {
            if (!chunk.isEmpty()) {
                for (Map<String, String> row : chunk) {
                    // just doing English language for now
                    if (row.get("language_concept_id").equals("4180186")) {
                        OWLClass c = concepts.getByID(row.get("concept_id"));
                        if (c != null) {
                            OWLAnnotation synonym = dataFactory.getOWLAnnotation(
                                    synonym_of,
                                    dataFactory.getOWLLiteral(row.get("concept_synonym_name"))
                            );
                            OWLAxiom syn_ax = dataFactory.getOWLAnnotationAssertionAxiom(c.getIRI(), synonym);
                            ontology.add(syn_ax);
                        }
                    }
                }
            }
        }
    }
}

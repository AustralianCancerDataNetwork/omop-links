package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OMOPAncestry {

    private final String vocab_folder;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;

    // TODO: need to create a superclass here because I am repeating myself all over the place
    public OMOPAncestry(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
    }

    public void load(OMOPConcepts concepts) throws IOException {
        System.out.println("Creating OWL axioms for OMOP subsumption relationships");
        int chunkSize = 1000;
        System.out.println("Reading CONCEPT_ANCESTOR.csv...");
        File conceptAncestorFile = new File(vocab_folder, "CONCEPT_ANCESTOR.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptAncestorFile, chunkSize);

        for (List<Map<String, String>> chunk : iterable) {
            if (!chunk.isEmpty()) {
                for (Map<String, String> row : chunk) {
                    // todo: assuming only implemented for immediate parent because others will be handled by the reasoner
                   if (row.get("min_levels_of_separation").equals("1")) {
                        OWLClass parent = concepts.getByID(row.get("ancestor_concept_id"));
                        OWLClass child = concepts.getByID(row.get("descendant_concept_id"));
                        if (parent != null && child != null) {
                            ontology.add(dataFactory.getOWLSubClassOfAxiom(child, parent));
                        }
                    }
                }
            }
        }
    }
}

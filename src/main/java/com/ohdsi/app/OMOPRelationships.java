package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;

import org.apache.commons.cli.*;
import org.apache.commons.csv.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OMOPRelationships {

    private String vocab_folder;
    private OWLOntology ontology;
    private OWLDataFactory dataFactory;
    private OWLOntologyManager manager;
    private IRI omop_iri;

    // TODO: need to create a superclass here because I am repeating myself all over the place
    public OMOPRelationships(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder, OWLOntologyManager manager, IRI omop_iri) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.manager = manager;
        this.omop_iri = omop_iri;
    }

    public void load(OMOPConcepts concepts, OMOPMetadataClasses metadata) throws IOException {
        System.out.println("Creating OWL axioms for OMOP subsumption relationships");
        Map<String, OWLClass> rr = metadata.getFamily("relationship");
        // todo: only implemented subsumption relationship for now
        OWLClass subsumes = rr.get("Subsumes");
        int chunkSize = 100;
        System.out.println("Reading CONCEPT_ANCESTOR.csv...");
        File conceptRelationshipFile = new File(vocab_folder, "CONCEPT_ANCESTOR.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptRelationshipFile, chunkSize);

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

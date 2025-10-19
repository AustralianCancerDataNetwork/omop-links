package com.ohdsi.app;

import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class OMOPRelationships {

    private final String vocab_folder;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;
    private final OWLAnnotationProperty maps_to;


    // TODO: need to create a superclass here because I am repeating myself all over the place
    public OMOPRelationships(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder, PrefixDocumentFormat pm) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.maps_to = dataFactory.getOWLAnnotationProperty("skos:exactMatch", pm);
    }

    public void load(OMOPConcepts concepts, OMOPMetadataClasses metadata) throws IOException {
        System.out.println("Creating OWL axioms for OMOP maps to relationships");
        Map<String, OWLClass> rr = metadata.getFamily("relationship");
        // todo: only implemented non-standard to standard relationship for now

        int chunkSize = 1000;
        System.out.println("Reading CONCEPT_RELATIONSHIP.csv...");
        File conceptRelationshipFile = new File(vocab_folder, "CONCEPT_RELATIONSHIP.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptRelationshipFile, chunkSize);
        for (List<Map<String, String>> chunk : iterable) {
            if (!chunk.isEmpty()) {
                for (Map<String, String> row : chunk) {
                   if (row.get("relationship_id").equals("Maps to")) {
                        OWLClass non_standard = concepts.getByID(row.get("concept_id_1"));
                        OWLClass standard = concepts.getByID(row.get("concept_id_2"));
                        if (non_standard != null && standard != null) {
                            OWLAnnotation mapping = dataFactory.getOWLAnnotation(
                                    maps_to,
                                    IRI.create(standard.getIRI().toString())
                            );
                            OWLAxiom map_ax = dataFactory.getOWLAnnotationAssertionAxiom(non_standard.getIRI(), mapping);
                            ontology.add(map_ax);
                        }
                    }
                }
            }
        }
    }
}

package com.ohdsi.app;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class OMOPConcepts {

    private final Map<String, OWLClass> idToClass = new HashMap<>();
    private final String vocab_folder;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;
    private final Map<String, AnnotationConfig> annotation_lookup;
    private final Map<String, PropertyConfig> property_lookup;
    private final PrefixDocumentFormat pm;
    private final IRI omop_iri;
    private final OWLAnnotationProperty maps_to;

    public OMOPConcepts(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder,
                        PrefixDocumentFormat pm, OWLOntologyManager manager, OMOPMetadataClasses metadata,
                        IRI omop_iri) {
        this.omop_iri = omop_iri;
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.annotation_lookup = new LinkedHashMap<>();
        this.property_lookup = new LinkedHashMap<>();

        this.pm = pm;
        this.maps_to = dataFactory.getOWLAnnotationProperty("skos:exactMatch", this.pm);

        Map<String, OWLClass> vv = metadata.getFamily("vocabulary");
        Map<String, OWLClass> cc = metadata.getFamily("concept_class");
        Map<String, OWLClass> dd = metadata.getFamily("domain");

        property_lookup.put("domain", new PropertyConfig("in_domain", "domain_id", dataFactory, omop_iri, ontology, manager, dd));
        property_lookup.put("concept_class", new PropertyConfig("in_class", "concept_class_id", dataFactory, omop_iri, ontology, manager, cc));
        property_lookup.put("vocabulary", new PropertyConfig("in_vocabulary", "vocabulary_id", dataFactory, omop_iri, ontology, manager, vv));

        // moving this to skos:exactMatch so that we can search by code+vocab easily not just concept id
        // this should be done better if we can get curi forms for all included vocabs, but at least now it works for letting
        // you get started with searching by vocab, code pairs...
        // annotation_lookup.put("code", new AnnotationConfig("has_code", "concept_code", dataFactory, ontology, omop_iri, manager));
        annotation_lookup.put("invalid", new AnnotationConfig("invalid", "invalid", dataFactory, ontology, omop_iri, manager));
        annotation_lookup.put("standard", new AnnotationConfig("standard_concept", "standard_concept", dataFactory, ontology, omop_iri, manager));
    }

    public void load() throws IOException {
        System.out.println("Creating OWL classes for OMOP concepts");

        int chunkSize = 5000;
        System.out.println("Reading CONCEPT.csv...");
        File conceptFile = new File(vocab_folder, "CONCEPT.csv");
        CSVChunkIterable iterable = new CSVChunkIterable(conceptFile, chunkSize);
        // this whole thing feels kind of brute force - divide and conquer on vocabs with some pre-processing?
        List<String> target_vocabs = Arrays.asList("SNOMED", "HemOnc", "ICDO3", "Cancer Modifier", "NCIt", "LOINC", "ICD10CM");
        // List<String> target_vocabs = Arrays.asList("Cancer Modifier");
        // List<String> target_vocabs = Arrays.asList("OMOP Genomic");

        for (List<Map<String, String>> chunk : iterable) {
            if (!chunk.isEmpty()) {
                for (Map<String, String> row : chunk) {
                    if (target_vocabs.contains(row.get("vocabulary_id"))) {
                        OWLClass concept = dataFactory.getOWLClass(
                                omop_iri + row.get("concept_id") //row.get("vocabulary_id").replace(" ", "_").toLowerCase() + "_" +
                        );
                        String v = row.get("vocabulary_id").replace(" ", "_").toLowerCase() + ":";
                        String c = row.get("concept_code");
                        OWLAnnotation mapping = dataFactory.getOWLAnnotation(
                                maps_to,
                                dataFactory.getOWLLiteral(v + c)
                        );
                        OWLAxiom map_ax = dataFactory.getOWLAnnotationAssertionAxiom(concept.getIRI(), mapping);
                        ontology.add(map_ax);

                        for (Map.Entry<String, AnnotationConfig> entry : annotation_lookup.entrySet()) {
                            AnnotationConfig annotator = entry.getValue();
                            String label = row.get(annotator.annotation_source);
                            if (label != null && !label.trim().isEmpty()) {
                                OWLAnnotationAssertionAxiom annotation_axiom = dataFactory.getOWLAnnotationAssertionAxiom(
                                        annotator.annotation,
                                        concept.getIRI(),
                                        dataFactory.getOWLLiteral(label)
                                );
                                ontology.addAxiom(annotation_axiom);
                            }
                        }
                        for (Map.Entry<String, PropertyConfig> entry : property_lookup.entrySet()) {
                            PropertyConfig property = entry.getValue();
                            String label = row.get(property.property_source);
                            if (label != null && !label.trim().isEmpty()) {
                                OWLClass prop = property.property_lookup.get(label);
                                OWLClassExpression expression = dataFactory.getOWLObjectSomeValuesFrom(property.property, prop);
                                OWLAxiom subclass_axiom = dataFactory.getOWLSubClassOfAxiom(concept, expression);
                                ontology.addAxiom(subclass_axiom);
                            }
                        }
                        OWLAnnotation label = dataFactory.getOWLAnnotation(
                                dataFactory.getRDFSLabel(),
                                dataFactory.getOWLLiteral(row.get("concept_name"), "en")
                        );
                        OWLAxiom lab = dataFactory.getOWLAnnotationAssertionAxiom(concept.getIRI(), label);
                        ontology.add(lab);
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

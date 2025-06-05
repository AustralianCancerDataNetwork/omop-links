package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class OMOPMetadataClasses {
    Map<String, Map<String, OWLClass>> owlClassLookup = new HashMap<>();
    private String vocab_folder;
    private OWLOntology ontology;
    private OWLDataFactory dataFactory;
    private Map<String, MetadataConfig> refLookup;
    private OWLOntologyManager manager;
    private IRI omop_iri;

    public OMOPMetadataClasses(OWLOntology ontology, OWLDataFactory dataFactory, String vocab_folder, OWLOntologyManager manager, IRI omop_iri) {
        this.ontology = ontology;
        this.dataFactory = dataFactory;
        this.vocab_folder = vocab_folder;
        this.manager = manager;
        this.omop_iri = omop_iri;
        this.refLookup = new LinkedHashMap<>();

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

            OWLClass c = dataFactory.getOWLClass(IRI.create(omop_iri + className));
            OWLDeclarationAxiom da = dataFactory.getOWLDeclarationAxiom(c);
            ontology.add(da);

            for (List<Map<String, String>> chunk : iterable) {
                if (!chunk.isEmpty()) {
                    for (Map<String, String> row : chunk) {
                        OWLClass v = dataFactory.getOWLClass(IRI.create(omop_iri + row.get(config.conceptIdColumn)));
                        ontology.add(dataFactory.getOWLSubClassOfAxiom(v, c));
                        OWLAnnotation lab = dataFactory.getOWLAnnotation(
                                dataFactory.getRDFSLabel(),
                                dataFactory.getOWLLiteral(row.get(config.idColumn), "en")
                        );
                        OWLAxiom ax1 = dataFactory.getOWLAnnotationAssertionAxiom(v.getIRI(), lab);
                        manager.applyChange(new AddAxiom(ontology, ax1));
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


// this works but is silly-slow once >1 small vocab :)
//public Optional<OWLClass> findClassByLabel(OWLOntology ontology, String parentClassIRI, String targetLabel) {
//    OWLClass parentClass = dataFactory.getOWLClass(parentClassIRI);
//
//    Set<OWLClass> allClasses = ontology.getClassesInSignature();
//    for (OWLClass candidate : allClasses) {
//        // Check if it's a subclass of the target parent
//        for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSubClass(candidate)) {
//            if (axiom.getSuperClass().equals(parentClass)) {
//                // Now check rdfs:label
//                Stream<OWLAnnotation> annotations = EntitySearcher.getAnnotations(candidate, ontology, dataFactory.getRDFSLabel());
//                for (OWLAnnotation annotation : (Iterable<OWLAnnotation>) annotations::iterator) {
//                    if (annotation.getValue() instanceof OWLLiteral literal) {
//                        if (literal.getLiteral().equalsIgnoreCase(targetLabel)) {
//                            return Optional.of(candidate);  // Match found
//                        }
//                    }
//                }
//            }
//        }
//    }
//    return Optional.empty();  // Not found
//}
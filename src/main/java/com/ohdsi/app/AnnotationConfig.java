package com.ohdsi.app;

import org.semanticweb.owlapi.model.*;

public class AnnotationConfig {

    public String annotation_name;
    public String annotation_source;
    public OWLAnnotationProperty annotation;
    public AnnotationConfig(String annotation_name, String annotation_source,
                            OWLDataFactory dataFactory, OWLOntology ontology,
                            IRI omop_iri, OWLOntologyManager manager) {
        this.annotation_name = annotation_name;
        this.annotation_source = annotation_source;
        this.annotation = dataFactory.getOWLAnnotationProperty(omop_iri + annotation_name);
        manager.addAxiom(ontology, dataFactory.getOWLDeclarationAxiom(annotation));
    }
}



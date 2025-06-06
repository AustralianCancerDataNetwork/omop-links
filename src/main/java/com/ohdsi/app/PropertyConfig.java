package com.ohdsi.app;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;

import org.semanticweb.owlapi.model.*;
        import java.util.*;

public class PropertyConfig {

    public String property_name;
    public String property_source;
    public OWLObjectProperty property;
    public Map<String, OWLClass> property_lookup;

    public PropertyConfig(String annotation_name, String annotation_source,
                            OWLDataFactory dataFactory, PrefixDocumentFormat pm, OWLOntology ontology,
                            OWLOntologyManager manager, Map<String, OWLClass> property_lookup) {
        this.property_name = annotation_name;
        this.property_source = annotation_source;
        this.property_lookup = property_lookup;
        this.property = dataFactory.getOWLObjectProperty("omop:" + annotation_name, pm);
        manager.addAxiom(ontology, dataFactory.getOWLDeclarationAxiom(property));
    }
}


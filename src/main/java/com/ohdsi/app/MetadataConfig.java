package com.ohdsi.app;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

public class MetadataConfig {
    public String filename;
    public String conceptIdColumn;
    public String labelColumn;
    public String idColumn;

    public MetadataConfig(String filename, String conceptIdColumn, String labelColumn, String idColumn) {
        this.filename = filename;
        this.conceptIdColumn = conceptIdColumn;
        this.labelColumn = labelColumn;
        this.idColumn = idColumn;
    }
}

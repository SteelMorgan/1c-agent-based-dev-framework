package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [10.07.2026 23:24:00]
/** Контракт атомарного удаления точно адресованных компонентов существующей СКД. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdRemoveComponentsDsl {
    private String ifAbsent;
    private List<StructureItem> structureItems;
    private List<GroupTemplate> groupTemplates;
    private List<DataSetLink> dataSetLinks;
    private List<DataSet> dataSets;
    private List<DataSetField> dataSetFields;

    /** Direct field declaration selector scoped to one root dataSet. */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSetField {
        private String dataSet;
        private String type;
        private String dataPath;
    }

    /** Root dataSet selector; type is an optional fail-closed guard. */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSet {
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StructureItem {
        private String variant;
        private List<String> path;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupTemplate {
        private String groupName;
        private String groupField;
        private String templateType;
        private String template;
    }

    /** Mapping-level selector compatible with upsert-dataset-link identity. */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSetLink {
        private String sourceDataSet;
        private String destinationDataSet;
        private String sourceExpression;
        private String parameter;
        private String destinationExpression;
    }
}
//--agent TASK-174

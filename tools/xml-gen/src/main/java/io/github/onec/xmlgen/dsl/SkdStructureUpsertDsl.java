package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [10.07.2026 18:25:00]
/** JSON-контракт lossless upsert именованных групп структуры существующего варианта СКД. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdStructureUpsertDsl {
    private String variant;
    private String dataSet;
    private List<Item> items;
    private List<String> topLevelOrder;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String kind;
        private String name;
        private List<String> groupItems;
        private List<OrderItem> order;
        private List<String> selection;
        private List<OutputParameter> outputParameters;
        private List<Item> children;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderItem {
        private String field;
        private String direction;
    }

    //++agent TASK-174 [12.07.2026 03:25:00]
    /** Scalar SettingsParameterValue direct-блока outputParameters группы. */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutputParameter {
        private String parameter;
        private String valueType;
        private String value;
        private Boolean use;
    }
    //--agent TASK-174
}
//++agent TASK-174

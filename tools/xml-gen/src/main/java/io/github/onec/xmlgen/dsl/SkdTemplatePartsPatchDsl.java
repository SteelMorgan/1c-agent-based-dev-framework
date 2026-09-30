package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [11.07.2026 11:00:00]
/** Контракт атомарной lossless-правки параметров AreaTemplate и их group bindings. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdTemplatePartsPatchDsl {
    private String ifAbsent;
    //++agent TASK-174 [12.07.2026 03:00:00]
    private List<RootParameter> parameters;
    //--agent TASK-174
    private List<ParameterExpression> parameterExpressions;
    private List<GroupBinding> groupBindings;

    //++agent TASK-174 [12.07.2026 03:00:00]
    /** Точечная замена default value и флагов существующего корневого параметра СКД. */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RootParameter {
        private String name;
        private String value;
        private String xsiType;
        private Boolean useRestriction;
        private Boolean availableAsField;
    }
    //--agent TASK-174

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParameterExpression {
        private String template;
        private String parameter;
        private String expression;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupBinding {
        private String action;
        private String groupName;
        private String templateType;
        private String template;
        private String newGroupName;
        private String newTemplateType;
        private String newTemplate;
    }
}
//++agent TASK-174

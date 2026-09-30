package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [11.07.2026 09:45:00]
/** Контракт lossless-переименования direct field declarations существующего root dataSet. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdDataSetFieldRenameDsl {
    private String ifAbsent;
    private String referencePolicy;
    private List<Field> fields;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Field {
        private String dataSet;
        private String type;
        private String oldDataPath;
        private String oldField;
        private String dataPath;
        private String field;
    }
}
//--agent TASK-174

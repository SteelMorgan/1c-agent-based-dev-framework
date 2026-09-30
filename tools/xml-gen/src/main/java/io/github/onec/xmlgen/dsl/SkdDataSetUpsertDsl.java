package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [11.07.2026 21:57:04]
/** JSON-контракт атомарной lossless-вставки или замены целых root dataSet. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdDataSetUpsertDsl {
    private String mode;
    private String ifAbsent;
    private String sourceFile;
    private List<DataSet> dataSets;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSet {
        private String name;
        private String type;
        private String xml;
        private String sourceFile;
    }
}
//++agent TASK-174

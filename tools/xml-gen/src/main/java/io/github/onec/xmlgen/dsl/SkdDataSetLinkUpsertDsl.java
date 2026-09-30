package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [10.07.2026 22:00:00]
/** JSON-контракт atomic/lossless upsert отношений наборов данных существующей СКД. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdDataSetLinkUpsertDsl {
    private List<Link> links;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {
        private String source;
        private String destination;
        private List<Mapping> mappings;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Mapping {
        private String sourceExpression;
        private String destinationExpression;
        private String parameter;
        private Boolean parameterListAllowed;
    }
}
//--agent TASK-174

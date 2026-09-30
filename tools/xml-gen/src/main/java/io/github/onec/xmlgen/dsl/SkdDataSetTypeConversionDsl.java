package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

//++agent TASK-174 [13.07.2026 00:00:00]
/** Контракт атомарной миграции типа root dataSet и exact cleanup direct filters. */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdDataSetTypeConversionDsl {
    private String ifAbsent;
    private List<DataSet> dataSets;
    private List<Filter> removeFilters;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSet {
        private String name;
        private String fromType;
        private String toType;
        private String objectName;
        private Integer expectedFieldCount;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Filter {
        private String variant;
        private String structurePath;
        private String field;
        private String comparison;
        private BigDecimal rightValue;
    }
}
//++agent TASK-174

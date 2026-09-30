package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//++agent TASK-174 [10.07.2026 17:19:29]
/**
 * Payload точечного импорта областей и их привязок в существующую СКД.
 * Использует тот же canonical templates DSL, что и {@code skd compile}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdTemplateImportDsl {
    private String ifAbsent;
    private List<ExactTemplate> exactTemplates;
    private List<SkdDsl.Template> templates;
    private List<SkdDsl.GroupTemplate> groupTemplates;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExactTemplate {
        private String sourceFile;
        private String sourceName;
        private String targetName;
    }
}
//++agent TASK-174

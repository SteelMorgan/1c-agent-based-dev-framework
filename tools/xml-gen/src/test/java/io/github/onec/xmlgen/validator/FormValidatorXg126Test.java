package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-225 [28.09.2026] XG-126
/**
 * FORM-129: платформа 1С регистронезависима к именам событий формы.
 * 'onCreateAtServer' — валидное имя, ERROR не должен выдаваться.
 * ERROR сохраняется только для неизвестных lower-case имён.
 */
class FormValidatorXg126Test {

    private static final String FORM_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core">
                <Events>
                    <Event name="%s">Обработчик</Event>
                </Events>
                <ChildItems>
                    <InputField name="Поле1">
                        <Events>
                            <Event name="%s">ОбработчикПоля</Event>
                        </Events>
                    </InputField>
                </ChildItems>
            </Form>
            """;

    private static List<ValidationIssue> form129Issues(String formEvent, String elemEvent)
            throws Exception {
        Path tmp = Files.createTempFile("xg126", ".xml");
        Files.writeString(tmp, FORM_TEMPLATE.formatted(formEvent, elemEvent));
        try {
            XmlDocument doc = new XmlStructureReader().parse(tmp);
            return new FormValidator().validate(doc, ValidationLevel.SEMANTIC).stream()
                    .filter(i -> "FORM-129".equals(i.getCode()))
                    .collect(Collectors.toList());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OnCreateAtServer", "onCreateAtServer", "ONCREATEATSERVER"})
    void knownEventAnyCaseHasNoForm129(String name) throws Exception {
        assertThat(form129Issues(name, "onChange")).isEmpty();
    }

    @Test
    void unknownLowercaseEventStillErrors() throws Exception {
        assertThat(form129Issues("onBogusEvent", "noSuchEvent"))
                .extracting(ValidationIssue::getCode)
                .contains("FORM-129");
    }
}

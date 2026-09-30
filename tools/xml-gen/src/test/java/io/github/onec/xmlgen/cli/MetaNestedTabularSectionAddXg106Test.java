package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MetaNestedTabularSectionAddXg106Test {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-XG106 object-form dry-run reports two exact nested additions without write")
    void objectFormDryRunReportsExactOperationsAndDiffWithoutWrite() throws Exception {
        Path object = createFixture("dry-run");
        Path patch = writePatch("dry-run.json", objectFormPatch());
        byte[] before = Files.readAllBytes(object);

        ProcessResult result = runMain("meta", "edit", object.toString(),
                "--batch", patch.toString(), "--dry-run");

        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout())
                .contains("Batch: Document.XG106Document, 2 operations")
                .contains("  Added:     2")
                .contains("[DRY-RUN] Batch not saved: " + object);
        assertThat(Files.readAllBytes(object)).containsExactly(before);
    }

    @Test
    @DisplayName("integr-XG106 object-form apply emits canonical qualifiers and repeat is byte no-op")
    void objectFormApplyEmitsCanonicalAttributesAndRepeatIsNoOp() throws Exception {
        Path object = createFixture("object-apply");
        Path patch = writePatch("object-apply.json", objectFormPatch());

        ProcessResult first = runMain("meta", "edit", object.toString(), "--batch", patch.toString());

        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertCanonicalAttribute(object, "tradeId", "xs:string", "100", null, null, "ShowError");
        assertCanonicalAttribute(object, "fillTime", "xs:decimal", null, "13", "Nonnegative", "DontCheck");
        byte[] afterFirst = Files.readAllBytes(object);

        ProcessResult repeat = runMain("meta", "edit", object.toString(), "--batch", patch.toString());

        assertThat(repeat.exitCode()).as(repeat.stderr()).isZero();
        assertThat(repeat.stdout()).contains("0 operations").contains("No changes applied");
        assertThat(Files.readAllBytes(object)).containsExactly(afterFirst);
    }

    @Test
    @DisplayName("integr-XG106 documented shorthand nested add emits canonical attributes")
    void shorthandNestedAddEmitsCanonicalAttributes() throws Exception {
        Path object = createFixture("shorthand");
        Path patch = writePatch("shorthand.json", """
                {"modify":{"tabularSections":{"ЗаполнениеОрдера":{"add":[
                  "tradeId: String(100)",
                  "fillTime: Number(13,0) | nonneg"
                ]}}}}
                """);

        ProcessResult result = runMain("meta", "edit", object.toString(), "--batch", patch.toString());

        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertCanonicalAttribute(object, "tradeId", "xs:string", "100", null, null, "DontCheck");
        assertCanonicalAttribute(object, "fillTime", "xs:decimal", null, "13", "Nonnegative", "DontCheck");
    }

    @Test
    @DisplayName("unit-XG106 missing tabular section fails before write")
    void missingTabularSectionFailsBeforeWrite() throws Exception {
        assertAtomicFailure("missing-ts", """
                {"modify":{"tabularSections":{"НесуществующаяТЧ":{"add":["tradeId: String(100)"]}}}}
                """);
    }

    @Test
    @DisplayName("unit-XG106 duplicate nested attribute in one patch fails before write")
    void duplicateNestedAttributeFailsBeforeWrite() throws Exception {
        assertAtomicFailure("duplicate", """
                {"modify":{"tabularSections":{"ЗаполнениеОрдера":{"add":[
                  "tradeId: String(100)", "tradeId: String(20)"
                ]}}}}
                """);
    }

    @Test
    @DisplayName("unit-XG106 invalid nested attribute type fails before write")
    void invalidNestedAttributeTypeFailsBeforeWrite() throws Exception {
        assertAtomicFailure("invalid-type", """
                {"modify":{"tabularSections":{"ЗаполнениеОрдера":{"add":[
                  {"name":"tradeId","type":"UnknownType(100)"}
                ]}}}}
                """);
    }

    @Test
    @DisplayName("reg-XG106 nested remove and reapply preserves CRLF and parseable canonical XML")
    void nestedRemoveAndReapplyPreservesCrLfAndCanonicalXml() throws Exception {
        Path object = createFixture("remove-reapply");
        Path addPatch = writePatch("remove-reapply-add.json", objectFormPatch());
        Path removePatch = writePatch("remove-reapply-remove.json", """
                {"operations":[{"op":"modify-tabularSection","name":"ЗаполнениеОрдера",
                  "operations":[
                    {"op":"remove-attribute","name":"tradeId"},
                    {"op":"remove-attribute","name":"fillTime"}
                  ]}]}
                """);

        assertThat(runMain("meta", "edit", object.toString(), "--batch", addPatch.toString()).exitCode())
                .isZero();
        assertThat(runMain("meta", "edit", object.toString(), "--batch", removePatch.toString()).exitCode())
                .isZero();
        ProcessResult reapply = runMain("meta", "edit", object.toString(), "--batch", addPatch.toString());

        assertThat(reapply.exitCode()).as(reapply.stderr()).isZero();
        assertCanonicalAttribute(object, "fillTime", "xs:decimal", null, "13",
                "Nonnegative", "DontCheck");
        String xml = Files.readString(object, StandardCharsets.UTF_8);
        assertThat(xml.replace("\r\n", "")).doesNotContain("\n");
    }

    private String objectFormPatch() {
        return """
                {"modify":{"tabularSections":{"ЗаполнениеОрдера":{"add":[
                  {"name":"tradeId","type":"String(100)","fillChecking":"ShowError"},
                  {"name":"fillTime","type":"Number(13,0)","allowedSign":"Nonnegative"}
                ]}}}}
                """;
    }

    private void assertAtomicFailure(String suffix, String patchJson) throws Exception {
        Path object = createFixture(suffix);
        Path patch = writePatch(suffix + ".json", patchJson);
        byte[] before = Files.readAllBytes(object);

        ProcessResult result = runMain("meta", "edit", object.toString(), "--batch", patch.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readAllBytes(object)).containsExactly(before);
    }

    private void assertCanonicalAttribute(Path object, String name, String type,
                                          String stringLength, String numberDigits,
                                          String allowedSign, String fillChecking) throws Exception {
        Element attribute = findTsAttribute(object, "ЗаполнениеОрдера", name);
        assertThat(attribute).isNotNull();
        UUID.fromString(attribute.getAttribute("uuid"));
        Element properties = direct(attribute, "Properties");
        assertThat(directNames(properties)).containsExactly(
                "Name", "Synonym", "Comment", "Type", "PasswordMode", "Format", "EditFormat",
                "ToolTip", "MarkNegatives", "Mask", "MultiLine", "ExtendedEdit", "MinValue",
                "MaxValue", "FillChecking", "ChoiceFoldersAndItems", "ChoiceParameterLinks",
                "ChoiceParameters", "QuickChoice", "CreateOnInput", "ChoiceForm", "LinkByType",
                "ChoiceHistoryOnInput", "Indexing", "FullTextSearch");
        assertThat(direct(direct(properties, "Type"), "Type").getTextContent()).isEqualTo(type);
        if (stringLength != null) {
            Element qualifiers = direct(direct(properties, "Type"), "StringQualifiers");
            assertThat(directNames(qualifiers)).containsExactly("Length", "AllowedLength");
            assertThat(direct(qualifiers, "Length").getTextContent()).isEqualTo(stringLength);
            assertThat(direct(qualifiers, "AllowedLength").getTextContent()).isEqualTo("Variable");
        }
        if (numberDigits != null) {
            Element qualifiers = direct(direct(properties, "Type"), "NumberQualifiers");
            assertThat(directNames(qualifiers)).containsExactly("Digits", "FractionDigits", "AllowedSign");
            assertThat(direct(qualifiers, "Digits").getTextContent()).isEqualTo(numberDigits);
            assertThat(direct(qualifiers, "FractionDigits").getTextContent()).isEqualTo("0");
            assertThat(direct(qualifiers, "AllowedSign").getTextContent()).isEqualTo(allowedSign);
            Element minValue = direct(properties, "MinValue");
            assertThat(minValue.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"))
                    .isEqualTo("true");
            assertThat(directNames(minValue)).isEmpty();
        }
        assertThat(direct(properties, "FillChecking").getTextContent()).isEqualTo(fillChecking);
    }

    private Element findTsAttribute(Path object, String tsName, String attributeName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(object.toFile());
        for (Element ts : elements(document.getDocumentElement(), "TabularSection")) {
            if (!tsName.equals(direct(direct(ts, "Properties"), "Name").getTextContent())) {
                continue;
            }
            for (Element attribute : elements(direct(ts, "ChildObjects"), "Attribute")) {
                if (attributeName.equals(direct(direct(attribute, "Properties"), "Name").getTextContent())) {
                    return attribute;
                }
            }
        }
        return null;
    }

    private List<Element> elements(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (localName.equals(element.getLocalName())) {
                    result.add(element);
                }
                result.addAll(elements(element, localName));
            }
        }
        return result;
    }

    private Element direct(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private List<String> directNames(Element parent) {
        List<String> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                result.add(element.getLocalName());
            }
        }
        return result;
    }

    private Path createFixture(String suffix) throws Exception {
        Path object = tempDir.resolve("XG106Document-" + suffix + ".xml");
        try (InputStream input = getClass().getResourceAsStream("/xg106/document-existing-ts.xml")) {
            assertThat(input).isNotNull();
            String fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\n", "\r\n");
            Files.writeString(object, fixture, StandardCharsets.UTF_8);
        }
        return object;
    }

    private Path writePatch(String filename, String json) throws Exception {
        Path patch = tempDir.resolve(filename);
        Files.writeString(patch, json, StandardCharsets.UTF_8);
        return patch;
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile()).start();
        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as("xml-gen child process timed out").isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

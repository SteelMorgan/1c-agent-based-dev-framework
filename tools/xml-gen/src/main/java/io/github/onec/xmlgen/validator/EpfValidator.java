package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.model.ConfigurationXmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Валидатор для корневого XML внешней обработки (ExternalDataProcessor)
 * и внешнего отчёта (ExternalReport).
 * <p>
 * Level 1 (Structure): EPF-001..006
 * Level 2 (Semantic):  EPF-007..010
 *
 * <p>Дополнительно (EPF-007..010, parity с Python epf-validate):
 * <ul>
 *   <li>EPF-007 — дубли child-имён в одном parent.</li>
 *   <li>EPF-008 — identifier pattern (латиница+кириллица+цифры+underscore, не начинается с цифры).</li>
 *   <li>EPF-009 — Form.xml существует для declared Form.</li>
 *   <li>EPF-010 — uuid / ClassId имеет формат GUID (8-4-4-4-12 hex).</li>
 *   <li>EPF-011 — посторонний прямой потомок MetaDataObject кроме
 *       ExternalDataProcessor/ExternalReport (блок вне объекта; XG-04).</li>
 *   <li>EPF-012 — каталог формы/макета лежит на диске, но объект НЕ объявлен в
 *       ChildObjects (фантомное объявление наоборот; прецедент runaway-памяти; XG-04).</li>
 *   <li>EPF-013 — объектные реквизиты/табличные части ошибочно помещены в контейнеры
 *       Attributes/TabularSections вместо ChildObjects (XG-25).</li>
 *   <li>EPF-014 — пути Default*Form/MainDataCompositionSchema используют не тот внешний
 *       тип объекта (ExternalReport vs ExternalDataProcessor).</li>
 *   <li>EPF-015 — версия формата дочерних XML-файлов формы/макета не совпадает с
 *       версией корневого файла.</li>
 *   <li>EPF-016 — MainDataCompositionSchema внешнего отчёта не ссылается на объявленный
 *       DCS-макет.</li>
 *   <li>EPF-017 — свойства внешнего отчёта попали во внешнюю обработку.</li>
 * </ul>
 */
public class EpfValidator implements XmlValidator {

    private static final String EPF_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";
    private static final String ERF_CLASS_ID = "e41aff26-25cf-4bb6-b6c1-3f478a75f374";

    private static final Pattern GUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Идентификатор 1С: начинается с буквы (рус/англ) или {@code _}, далее буквы/цифры/{@code _}. */
    private static final Pattern IDENT_RE = Pattern.compile(
            "^[A-Za-z\u0410-\u042f\u0430-\u044f\u0401\u0451_][A-Za-z0-9\u0410-\u042f\u0430-\u044f\u0401\u0451_]*$");

    private static final List<String> CHILD_OBJECT_ORDER = List.of(
            "Attribute", "TabularSection", "Form", "Template");

    private static final List<String> REPORT_ONLY_PROPERTIES = List.of(
            "MainDataCompositionSchema",
            "DefaultSettingsForm",
            "AuxiliarySettingsForm",
            "DefaultVariantForm",
            "VariantsStorage",
            "SettingsStorage");

    @Override
    public String objectType() {
        return "epf";
    }

    @Override
    public boolean supports(XmlDocument document) {
        String root = document.getRootElement();
        if ("ExternalDataProcessor".equals(root) || "ExternalReport".equals(root)) {
            return true;
        }
        if ("MetaDataObject".equals(root)) {
            XmlNode mdo = document.getRoot();
            return mdo.child("ExternalDataProcessor") != null
                    || mdo.child("ExternalReport") != null;
        }
        return false;
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateStructure(document, issues);
        if (level == ValidationLevel.SEMANTIC) {
            validateSemantic(document, issues);
        }
        return issues;
    }

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Навигация: MetaDataObject → ExternalDataProcessor/ExternalReport
        XmlNode epfNode;
        boolean isReport;
        if ("MetaDataObject".equals(root.getName())) {
            epfNode = root.child("ExternalDataProcessor");
            isReport = false;
            if (epfNode == null) {
                epfNode = root.child("ExternalReport");
                isReport = true;
            }
            if (epfNode == null) {
                issues.add(ValidationIssue.error("EPF-001",
                        "MetaDataObject missing <ExternalDataProcessor> or <ExternalReport> child",
                        root.getLine(), "/MetaDataObject"));
                return;
            }
            //++agent TASK-174 [05.06.2026 00:00:00]
            // EPF-011 (XG-04): валидный корневой XML обработки/отчёта имеет РОВНО одного прямого
            // потомка MetaDataObject — сам ExternalDataProcessor/ExternalReport. Обход «латинский
            // плейсхолдер + edit replace-text» (XG-03) порождал блок <Attributes> (и другие)
            // СНАРУЖИ </ExternalDataProcessor> — это прямой потомок MetaDataObject, на котором
            // Designer-batch падает XDTO-ошибкой "anyType не соответствует ExternalDataProcessor",
            // а старый валидатор давал PASS. Ловим любой посторонний прямой потомок.
            for (XmlNode sibling : root.getChildren()) {
                String sName = sibling.getName();
                if (!"ExternalDataProcessor".equals(sName) && !"ExternalReport".equals(sName)) {
                    issues.add(ValidationIssue.error("EPF-011",
                            "Unexpected element <" + sName + "> directly under MetaDataObject; only "
                                    + "<ExternalDataProcessor> or <ExternalReport> is allowed at this level "
                                    + "(a block placed outside the object will fail Designer with an XDTO error)",
                            sibling.getLine(), "/MetaDataObject/" + sName));
                }
            }
            //++agent TASK-174
        } else if ("ExternalDataProcessor".equals(root.getName())) {
            epfNode = root;
            isReport = false;
        } else if ("ExternalReport".equals(root.getName())) {
            epfNode = root;
            isReport = true;
        } else {
            issues.add(ValidationIssue.error("EPF-001",
                    "Expected root element 'MetaDataObject', 'ExternalDataProcessor' or 'ExternalReport', found '"
                            + root.getName() + "'",
                    root.getLine(), "/"));
            return;
        }

        String elementName = isReport ? "ExternalReport" : "ExternalDataProcessor";
        String expectedClassId = isReport ? ERF_CLASS_ID : EPF_CLASS_ID;
        String formatVersion = rootFormatVersion(root);

        // EPF-001: uuid присутствует
        String uuid = epfNode.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            issues.add(ValidationIssue.error("EPF-001",
                    elementName + " missing uuid attribute",
                    epfNode.getLine(), "/" + elementName));
        }

        // Навигация: Properties → Name / InternalInfo → ClassId
        XmlNode properties = epfNode.child("Properties");

        if (properties != null) {
            // EPF-002: Name непустой
            String name = properties.childText("Name");
            if (name == null || name.isEmpty()) {
                issues.add(ValidationIssue.error("EPF-002",
                        "Missing or empty <Name> in Properties",
                        properties.getLine(), "/" + elementName + "/Properties/Name"));
            }

            // EPF-003: ClassId
            // TASK-171: InternalInfo — СОСЕД Properties (прямой ребёнок epfNode), а НЕ ребёнок
            // Properties. Раньше навигация properties.child("InternalInfo") всегда возвращала null,
            // из-за чего проверка соответствия ClassId была мёртвым кодом и не ловила подмену вида
            // EPF↔ERF. Берём InternalInfo от epfNode.
            XmlNode internalInfo = epfNode.child("InternalInfo");
            if (internalInfo != null) {
                String classId = findClassId(internalInfo);
                if (classId != null && !classId.isEmpty()) {
                    if (!expectedClassId.equals(classId)) {
                        issues.add(ValidationIssue.error("EPF-003",
                                "Expected ClassId '" + expectedClassId + "', found '" + classId + "'",
                                internalInfo.getLine(), "/" + elementName + "/InternalInfo/ClassId"));
                    }
                }
            }
        } else {
            issues.add(ValidationIssue.error("EPF-002",
                    "Missing <Properties> element",
                    epfNode.getLine(), "/" + elementName));
        }

        // EPF-004: ChildObjects присутствует
        XmlNode childObjects = epfNode.child("ChildObjects");
        if (childObjects == null) {
            issues.add(ValidationIssue.error("EPF-004",
                    "Missing <ChildObjects> element",
                    epfNode.getLine(), "/" + elementName));
            return;
        }

        //++agent TASK-174 [07.06.2026 16:20:00]
        // XG-25: старый epf edit создавал синтетические контейнеры <Attributes> и
        // <TabularSections> прямо внутри ExternalDataProcessor/ExternalReport. В формате
        // Designer реквизиты объекта и ТЧ являются детьми <ChildObjects>, иначе структура
        // расходится с MDClasses-схемой и ломает загрузку из файлов.
        for (String invalidContainer : List.of("Attributes", "TabularSections")) {
            XmlNode invalid = epfNode.child(invalidContainer);
            if (invalid != null) {
                issues.add(ValidationIssue.error("EPF-013",
                        "Unexpected <" + invalidContainer + "> container inside " + elementName
                                + "; EPF/ERF attributes and tabular sections must be declared as "
                                + "<Attribute>/<TabularSection> children of <ChildObjects>",
                        invalid.getLine(), "/" + elementName + "/" + invalidContainer));
            }
        }
        //++agent TASK-174

        // EPF-005: полный канонический порядок ChildObjects:
        // Attribute → TabularSection → Form → Template.
        int lastKnownOrder = -1;
        String lastKnownKind = null;
        for (XmlNode child : childObjects.getChildren()) {
            int order = childObjectOrder(child.getName());
            if (order < 0) {
                continue;
            }
            if (order < lastKnownOrder) {
                issues.add(ValidationIssue.warning("EPF-005",
                        "ChildObjects order violation: <" + child.getName()
                                + "> appears after <" + lastKnownKind
                                + ">. Expected order: Attribute -> TabularSection -> Form -> Template",
                        childObjects.getLine(), "/" + elementName + "/ChildObjects"));
                break;
            }
            lastKnownOrder = order;
            lastKnownKind = child.getName();
        }

        // EPF-006: Файлы из ChildObjects существуют (если документ — файл на диске)
        //++agent TASK-171 [01.06.2026 21:45:00]
        // Канон Designer: дочерние Forms/Templates лежат под <docDir>/<имяОбъекта>/,
        // а НЕ прямо в <docDir>. Имя объекта = имя корневого файла без .xml (Designer
        // всегда именует <Имя>.xml рядом с каталогом <Имя>/). Прежний резолв от docDir
        // давал ложное EPF-006 на ЛЮБОМ валидном EPF/ERF с макетами (включая вывод
        // флага init --with-skd). Исправлено: база = docDir/<имяОбъекта>.
        Path docFile = document.getFile();
        Path docDir = docFile != null ? docFile.getParent() : null;
        if (docDir != null) {
            String fileName = docFile.getFileName().toString();
            String objectName = fileName.endsWith(".xml")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;
            Path childBase = docDir.resolve(objectName);
            validateChildFiles(childObjects, childBase, elementName, formatVersion, issues);
            //++agent TASK-174 [05.06.2026 00:00:00]
            // EPF-012 (XG-04): обратная проверка — каталог формы/макета лежит на диске под
            // <objectName>/Forms|Templates, но объект НЕ объявлен в <ChildObjects>. Designer при
            // загрузке из файлов наталкивается на форму без записи в метаданных → известный
            // прецедент runaway-памяти (memory project_phantom_form_load_runaway). EPF-006/009
            // ловят обратное (объявлено, файла нет) — здесь закрываем зеркальную дыру.
            validateUndeclaredChildDirs(childObjects, childBase, elementName, issues);
        }
        //++agent TASK-171
    }

    //++agent TASK-174 [05.06.2026 00:00:00]
    /**
     * EPF-012: каталоги форм/макетов на диске, не объявленные в ChildObjects.
     *
     * <p>Сканируем {@code <objectName>/Forms/*} и {@code <objectName>/Templates/*}; каждый
     * подкаталог (= потенциальная форма/макет) должен иметь соответствующую запись
     * {@code <Form>}/{@code <Template>} в ChildObjects. Незаявленный каталог — структурный
     * дефект (фантом).
     */
    private void validateUndeclaredChildDirs(XmlNode childObjects, Path baseDir, String elementName,
                                             List<ValidationIssue> issues) {
        Set<String> declaredForms = new HashSet<>();
        Set<String> declaredTemplates = new HashSet<>();
        for (XmlNode child : childObjects.getChildren()) {
            String objName = child.getText();
            if (objName == null || objName.isEmpty()) continue;
            if ("Form".equals(child.getName())) {
                declaredForms.add(objName);
            } else if ("Template".equals(child.getName())) {
                declaredTemplates.add(objName);
            }
        }
        checkUndeclaredDirs(baseDir.resolve("Forms"), declaredForms, "Form",
                childObjects, elementName, issues);
        checkUndeclaredDirs(baseDir.resolve("Templates"), declaredTemplates, "Template",
                childObjects, elementName, issues);
        checkUndeclaredMetadataFiles(baseDir.resolve("Forms"), declaredForms, "Form",
                childObjects, elementName, issues);
        checkUndeclaredMetadataFiles(baseDir.resolve("Templates"), declaredTemplates, "Template",
                childObjects, elementName, issues);
    }

    private void checkUndeclaredDirs(Path dir, Set<String> declared, String kind,
                                     XmlNode childObjects, String elementName,
                                     List<ValidationIssue> issues) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).forEach(sub -> {
                String dirName = sub.getFileName().toString();
                if (!declared.contains(dirName)) {
                    issues.add(ValidationIssue.error("EPF-012",
                            kind + " directory '" + dirName + "' exists on disk (" + sub
                                    + ") but is not declared in <ChildObjects> (phantom; will break "
                                    + "Designer load from files)",
                            childObjects.getLine(),
                            "/" + elementName + "/ChildObjects"));
                }
            });
        } catch (java.io.IOException e) {
            // Каталог нечитаем — не структурный дефект EPF, молча пропускаем (диск/права).
        }
    }

    private void checkUndeclaredMetadataFiles(Path dir, Set<String> declared, String kind,
                                              XmlNode childObjects, String elementName,
                                              List<ValidationIssue> issues) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        String objectName = fileName.substring(0, fileName.length() - 4);
                        if (!declared.contains(objectName)) {
                            issues.add(ValidationIssue.error("EPF-012",
                                    kind + " metadata file '" + fileName + "' exists on disk (" + file
                                            + ") but is not declared in <ChildObjects> (phantom; will break "
                                            + "Designer load from files)",
                                    childObjects.getLine(),
                                    "/" + elementName + "/ChildObjects"));
                        }
                    });
        } catch (java.io.IOException e) {
            // Каталог нечитаем — не структурный дефект EPF, молча пропускаем (диск/права).
        }
    }
    //++agent TASK-174

    private String findClassId(XmlNode internalInfo) {
        // TASK-171: ищем (xr:)ClassId рекурсивно — в реальной структуре он лежит на уровень
        // глубже: InternalInfo > xr:ContainedObject > xr:ClassId (см. EpfWriter.writeInternalInfo).
        // Прежний обход только прямых детей не находил вложенный xr:ClassId.
        for (XmlNode child : internalInfo.getChildren()) {
            if (child.getName().equals("ClassId") || child.getName().endsWith(":ClassId")) {
                return child.getText();
            }
            String nested = findClassId(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private void validateChildFiles(XmlNode childObjects, Path baseDir, String elementName,
                                    String formatVersion, List<ValidationIssue> issues) {
        for (XmlNode child : childObjects.getChildren()) {
            String childName = child.getName();
            String objName = child.getText();
            if (objName == null || objName.isEmpty()) continue;

            // Проверяем наличие каталога дочернего объекта
            Path childDir;
            Path childMeta;
            if ("Form".equals(childName)) {
                childDir = baseDir.resolve("Forms").resolve(objName);
                childMeta = baseDir.resolve("Forms").resolve(objName + ".xml");
            } else if ("Template".equals(childName)) {
                childDir = baseDir.resolve("Templates").resolve(objName);
                childMeta = baseDir.resolve("Templates").resolve(objName + ".xml");
            } else {
                continue;
            }

            if (!Files.isRegularFile(childMeta)) {
                issues.add(ValidationIssue.error("EPF-006",
                        childName + " '" + objName + "' metadata file not found: " + childMeta,
                        child.getLine(), "/" + elementName + "/ChildObjects/" + childName));
            } else {
                validateChildFormatVersion(childMeta, formatVersion, childName, objName,
                        child.getLine(), "/" + elementName + "/ChildObjects/" + childName, issues);
            }

            if (!Files.exists(childDir)) {
                issues.add(ValidationIssue.error("EPF-006",
                        childName + " '" + objName + "' directory not found: " + childDir,
                        child.getLine(), "/" + elementName + "/ChildObjects/" + childName));
            }

            // EPF-009: для Form — также проверим наличие Form.xml внутри каталога
            if ("Form".equals(childName) && Files.exists(childDir)) {
                //**agent TASK-174 [05.06.2026 00:00:00]
                // XG-04: канон Designer — описание формы лежит в <Имя>/Ext/Form.xml, а НЕ в
                // <Имя>/Ext/Form/Form.xml (проверено на src/xml/DataProcessors/**/Forms/*/Ext/Form.xml
                // и на выводе самого epf add-form). Прежний путь Ext/Form/Form.xml давал ложный
                // EPF-009 на КАЖДОЙ корректно сгенерированной обработке с формой — из-за этого
                // приёмочный validate для XG-03 не мог дать PASS. В Ext/Form/ лежит Module.bsl, а
                // не Form.xml.
                //Path formXml = childDir.resolve("Ext").resolve("Form").resolve("Form.xml");
                Path formXml = childDir.resolve("Ext").resolve("Form.xml");
                if (!Files.exists(formXml)) {
                    issues.add(ValidationIssue.error("EPF-009",
                            "Form '" + objName + "' declared but Form.xml not found: " + formXml,
                            child.getLine(), "/" + elementName + "/ChildObjects/Form"));
                } else {
                    validateChildFormatVersion(formXml, formatVersion, "Form.xml", objName,
                            child.getLine(), "/" + elementName + "/ChildObjects/Form", issues);
                }
                //**agent TASK-174
            }
        }
    }

    private String rootFormatVersion(XmlNode root) {
        String version = root.attr("version");
        return version == null || version.isBlank() ? null : version.trim();
    }

    private void validateChildFormatVersion(Path file, String expectedVersion, String kind,
                                            String objectName, int line, String element,
                                            List<ValidationIssue> issues) {
        if (expectedVersion == null || expectedVersion.isBlank()) {
            return;
        }
        String actualVersion = readVersionAttribute(file);
        if (actualVersion == null || actualVersion.isBlank()) {
            issues.add(ValidationIssue.error("EPF-015",
                    kind + " '" + objectName + "' file " + file
                            + " has no root version attribute; expected version '" + expectedVersion + "'",
                    line, element));
        } else if (!expectedVersion.equals(actualVersion)) {
            issues.add(ValidationIssue.error("EPF-015",
                    kind + " '" + objectName + "' file " + file
                            + " has format version '" + actualVersion + "', expected '" + expectedVersion + "'",
                    line, element));
        }
    }

    private String readVersionAttribute(Path file) {
        try {
            String content = ConfigurationXmlReader.readContent(file);
            Matcher matcher = Pattern.compile("<(?:\\w+:)?(?:MetaDataObject|Form)\\b[^>]*\\bversion=\"([^\"]+)\"",
                    Pattern.DOTALL).matcher(content);
            return matcher.find() ? matcher.group(1) : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();
        XmlNode epfNode;
        if ("MetaDataObject".equals(root.getName())) {
            epfNode = root.child("ExternalDataProcessor");
            if (epfNode == null) epfNode = root.child("ExternalReport");
            if (epfNode == null) return;
        } else {
            epfNode = root;
        }
        String elementName = epfNode.getName();

        // EPF-010: uuid — GUID-формат
        String uuid = epfNode.attr("uuid");
        if (uuid != null && !uuid.isEmpty() && !GUID_RE.matcher(uuid).matches()) {
            issues.add(ValidationIssue.error("EPF-010",
                    elementName + " uuid '" + uuid + "' is not a valid GUID (expected 8-4-4-4-12 hex)",
                    epfNode.getLine(), "/" + elementName));
        }

        // EPF-010: ClassId — тоже GUID
        // TASK-171: InternalInfo — сосед Properties (прямой ребёнок epfNode), а не его ребёнок.
        // Раньше props.child("InternalInfo") давал null → GUID-проверка ClassId не срабатывала.
        XmlNode internalInfo = epfNode.child("InternalInfo");
        if (internalInfo != null) {
            String classId = findClassId(internalInfo);
            if (classId != null && !classId.isEmpty() && !GUID_RE.matcher(classId).matches()) {
                issues.add(ValidationIssue.error("EPF-010",
                        "ClassId '" + classId + "' is not a valid GUID",
                        internalInfo.getLine(),
                        "/" + elementName + "/InternalInfo/ClassId"));
            }
        }

        // EPF-007, EPF-008: child name uniqueness + identifier pattern
        XmlNode childObjects = epfNode.child("ChildObjects");
        if (childObjects != null) {
            Map<String, Set<String>> seenByKind = new HashMap<>();
            for (XmlNode child : childObjects.getChildren()) {
                String kind = child.getName();
                String objName = childObjectName(child);
                if (objName == null || objName.isEmpty()) continue;

                // EPF-008: identifier pattern
                if (!IDENT_RE.matcher(objName).matches()) {
                    issues.add(ValidationIssue.error("EPF-008",
                            kind + " '" + objName + "' is not a valid 1C identifier",
                            child.getLine(),
                            "/" + elementName + "/ChildObjects/" + kind));
                }

                // EPF-007: duplicates per kind
                Set<String> seen = seenByKind.computeIfAbsent(kind, k -> new HashSet<>());
                if (!seen.add(objName)) {
                    issues.add(ValidationIssue.error("EPF-007",
                            "Duplicate " + kind + " name '" + objName + "' in ChildObjects",
                            child.getLine(),
                            "/" + elementName + "/ChildObjects/" + kind));
                }
            }
        }

        // EPF-008: Name (Properties) — тоже identifier
        XmlNode props = epfNode.child("Properties");
        if (props != null) {
            String name = props.childText("Name");
            if (name != null && !name.isEmpty() && !IDENT_RE.matcher(name).matches()) {
                issues.add(ValidationIssue.error("EPF-008",
                        "Object Name '" + name + "' is not a valid 1C identifier",
                        props.getLine(), "/" + elementName + "/Properties/Name"));
            }
            validateExternalObjectPaths(props, elementName, name, issues);
            if ("ExternalDataProcessor".equals(elementName)) {
                validateNoReportOnlyProperties(props, elementName, issues);
            } else if ("ExternalReport".equals(elementName)) {
                validateMainDataCompositionSchema(document, epfNode, props, name, issues);
            }
        }
    }

    private String childObjectName(XmlNode child) {
        String name = child.getText();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        XmlNode props = child.child("Properties");
        if (props == null) {
            return null;
        }
        String propName = props.childText("Name");
        return propName == null || propName.isBlank() ? null : propName.trim();
    }

    private void validateExternalObjectPaths(XmlNode props, String elementName, String objectName,
                                             List<ValidationIssue> issues) {
        if (objectName == null || objectName.isEmpty()) {
            return;
        }

        String expectedFormPrefix = elementName + "." + objectName + ".Form.";
        for (String tag : List.of("DefaultForm", "AuxiliaryForm",
                "DefaultSettingsForm", "AuxiliarySettingsForm", "DefaultVariantForm")) {
            String value = props.childText(tag);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!value.startsWith(expectedFormPrefix)) {
                issues.add(ValidationIssue.error("EPF-014",
                        tag + " '" + value + "' must start with '" + expectedFormPrefix
                                + "' for <" + elementName + ">",
                        props.getLine(), "/" + elementName + "/Properties/" + tag));
            }
        }

        String mainDcs = props.childText("MainDataCompositionSchema");
        if (mainDcs != null && !mainDcs.isEmpty()) {
            String expectedTemplatePrefix = elementName + "." + objectName + ".Template.";
            if (!mainDcs.startsWith(expectedTemplatePrefix)) {
                issues.add(ValidationIssue.error("EPF-014",
                        "MainDataCompositionSchema '" + mainDcs + "' must start with '"
                                + expectedTemplatePrefix + "' for <" + elementName + ">",
                        props.getLine(), "/" + elementName + "/Properties/MainDataCompositionSchema"));
            }
        }
    }

    private static int childObjectOrder(String kind) {
        return CHILD_OBJECT_ORDER.indexOf(kind);
    }

    private void validateNoReportOnlyProperties(XmlNode props, String elementName,
                                                List<ValidationIssue> issues) {
        for (String property : REPORT_ONLY_PROPERTIES) {
            if (props.child(property) != null) {
                issues.add(ValidationIssue.error("EPF-017",
                        "Property <" + property + "> is valid for <ExternalReport> only; "
                                + "<ExternalDataProcessor> has no report-specific properties",
                        props.getLine(), "/" + elementName + "/Properties/" + property));
            }
        }
    }

    private void validateMainDataCompositionSchema(XmlDocument document, XmlNode epfNode,
                                                   XmlNode props, String objectName,
                                                   List<ValidationIssue> issues) {
        String mainDcs = props.childText("MainDataCompositionSchema");
        if (mainDcs == null || mainDcs.isBlank() || objectName == null || objectName.isBlank()) {
            return;
        }

        String expectedPrefix = "ExternalReport." + objectName + ".Template.";
        if (!mainDcs.startsWith(expectedPrefix)) {
            // EPF-014 already reports the wrong prefix/type. Avoid cascading EPF-016 noise.
            return;
        }

        String templateName = mainDcs.substring(expectedPrefix.length());
        if (templateName.isBlank()) {
            issues.add(ValidationIssue.error("EPF-016",
                    "MainDataCompositionSchema '" + mainDcs
                            + "' does not contain a template name after '" + expectedPrefix + "'",
                    props.getLine(), "/ExternalReport/Properties/MainDataCompositionSchema"));
            return;
        }

        XmlNode childObjects = epfNode.child("ChildObjects");
        boolean declared = false;
        if (childObjects != null) {
            for (XmlNode child : childObjects.children("Template")) {
                if (templateName.equals(child.getText() != null ? child.getText().trim() : "")) {
                    declared = true;
                    break;
                }
            }
        }
        if (!declared) {
            issues.add(ValidationIssue.error("EPF-016",
                    "MainDataCompositionSchema points to template '" + templateName
                            + "', but <Template>" + templateName
                            + "</Template> is not declared in <ChildObjects>",
                    props.getLine(), "/ExternalReport/Properties/MainDataCompositionSchema"));
            return;
        }

        Path docFile = document.getFile();
        Path docDir = docFile != null ? docFile.getParent() : null;
        if (docDir == null) {
            return;
        }
        String fileName = docFile.getFileName().toString();
        String externalName = fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        Path templateMeta = docDir.resolve(externalName)
                .resolve("Templates")
                .resolve(templateName + ".xml");
        if (!Files.isRegularFile(templateMeta)) {
            // EPF-006 reports the missing metadata file for declared templates.
            return;
        }

        String templateType = readTemplateType(templateMeta);
        if (templateType != null && !"DataCompositionSchema".equals(templateType)) {
            issues.add(ValidationIssue.error("EPF-016",
                    "MainDataCompositionSchema template '" + templateName
                            + "' has TemplateType '" + templateType
                            + "', expected DataCompositionSchema",
                    props.getLine(), "/ExternalReport/Properties/MainDataCompositionSchema"));
        }
    }

    private String readTemplateType(Path templateMeta) {
        try {
            String content = ConfigurationXmlReader.readContent(templateMeta);
            Matcher matcher = Pattern.compile("<TemplateType>([^<]+)</TemplateType>")
                    .matcher(content);
            return matcher.find() ? matcher.group(1).trim() : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }
}

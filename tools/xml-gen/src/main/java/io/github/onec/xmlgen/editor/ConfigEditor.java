package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.model.MetadataTypeRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Редактор Configuration.xml — модификация свойств, ChildObjects, DefaultRoles.
 * <p>
 * Использует строковые операции для сохранения оригинального форматирования.
 */
public class ConfigEditor {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Канонический порядок типов в ChildObjects. */
    private static final List<String> CHILD_TYPE_ORDER = List.of(
            "Language", "Subsystem", "StyleItem", "Style", "CommonPicture",
            "SessionParameter", "Role", "CommonTemplate", "FilterCriterion",
            "CommonModule", "CommonAttribute", "ExchangePlan", "XDTOPackage",
            "WebService", "HTTPService", "WSReference", "EventSubscription",
            "ScheduledJob", "SettingsStorage", "FunctionalOption",
            "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
            "CommandGroup", "Constant", "CommonForm", "Catalog", "Document",
            "DocumentNumerator", "Sequence", "DocumentJournal", "Enum",
            "Report", "DataProcessor", "InformationRegister",
            "AccumulationRegister", "ChartOfCharacteristicTypes",
            "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
            "CalculationRegister", "BusinessProcess", "Task",
            "IntegrationService"
    );

    private final Path configXmlPath;
    private String content;
    private boolean hasBom;
    private boolean skipFileCheck = false;

    public ConfigEditor(Path configXmlPath) throws IOException {
        this.configXmlPath = configXmlPath;
        byte[] raw = Files.readAllBytes(configXmlPath);
        this.hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        this.content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Изменить скалярное или enum свойство в Properties.
     * Формат: "PropName=Value ;; PropName2=Value2"
     */
    public void modifyProperty(String spec) {
        for (String pair : spec.split("\\s*;;\\s*")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) throw new IllegalArgumentException("Invalid property spec: " + pair);
            String prop = kv[0].trim();
            String val = kv[1].trim();
            setSimpleProperty(prop, val);
        }
    }

    /**
     * Добавить объект(ы) в ChildObjects.
     * Формат: "Type.Name ;; Type.Name2"
     */
    public void addChildObject(String spec) {
        for (String item : spec.split("\\s*;;\\s*")) {
            String trimmed = item.trim();
            int dot = trimmed.indexOf('.');
            if (dot <= 0) throw new IllegalArgumentException("Expected Type.Name format: " + trimmed);
            String type = trimmed.substring(0, dot);
            String name = trimmed.substring(dot + 1);
            insertChildObject(type, name);
        }
    }

    /**
     * Удалить объект(ы) из ChildObjects.
     * Формат: "Type.Name ;; Type.Name2"
     */
    public void removeChildObject(String spec) {
        for (String item : spec.split("\\s*;;\\s*")) {
            String trimmed = item.trim();
            int dot = trimmed.indexOf('.');
            if (dot <= 0) throw new IllegalArgumentException("Expected Type.Name format: " + trimmed);
            String type = trimmed.substring(0, dot);
            String name = trimmed.substring(dot + 1);
            removeChildEntry(type, name);
        }
    }

    /**
     * Добавить роль в DefaultRoles.
     */
    public void addDefaultRole(String roleName) {
        String escapedName = escapeXml(roleName);
        String entry = "<xr:Item xsi:type=\"xr:MDObjectRef\">Role." + escapedName + "</xr:Item>";

        // Skip if already present
        if (content.contains(">Role." + escapedName + "</xr:Item>")) return;

        // Check if DefaultRoles is empty/self-closing
        if (content.contains("<DefaultRoles/>")) {
            content = content.replace("<DefaultRoles/>",
                    "<DefaultRoles>\n\t\t\t\t" + entry + "\n\t\t\t</DefaultRoles>");
        } else if (content.contains("<DefaultRoles></DefaultRoles>")) {
            content = content.replace("<DefaultRoles></DefaultRoles>",
                    "<DefaultRoles>\n\t\t\t\t" + entry + "\n\t\t\t</DefaultRoles>");
        } else {
            // Append before </DefaultRoles>
            content = content.replace("</DefaultRoles>",
                    "\t" + entry + "\n\t\t\t</DefaultRoles>");
        }
    }

    /**
     * Удалить роль из DefaultRoles.
     */
    public void removeDefaultRole(String roleName) {
        String escaped = escapeXml(roleName);
        Pattern p = Pattern.compile("\\s*<xr:Item[^>]*>Role\\." + Pattern.quote(escaped) + "</xr:Item>\\s*");
        content = p.matcher(content).replaceAll("\n");
    }

    /**
     * Заменить список DefaultRoles целиком.
     * Формат: "RoleName1 ;; RoleName2"
     */
    public void setDefaultRoles(String spec) {
        // Build new content
        StringBuilder sb = new StringBuilder("<DefaultRoles>\n");
        for (String role : spec.split("\\s*;;\\s*")) {
            String r = role.trim();
            if (!r.isEmpty()) {
                sb.append("\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">Role.")
                        .append(escapeXml(r)).append("</xr:Item>\n");
            }
        }
        sb.append("\t\t\t</DefaultRoles>");

        // Replace existing DefaultRoles
        Pattern p = Pattern.compile("<DefaultRoles[^/]*/>|<DefaultRoles>.*?</DefaultRoles>",
                Pattern.DOTALL);
        content = p.matcher(content).replaceFirst(Matcher.quoteReplacement(sb.toString()));
    }

    public void save() throws IOException {
        //++agent TASK-172 [02.06.2026 07:22:00]
        // Канон Designer (_Демо) — CRLF. Правка Configuration.xml строковыми заменами
        // вставляет \n-фрагменты; нормализуем итог к CRLF идемпотентно, BOM-решение сохраняем.
        byte[] contentBytes = io.github.onec.xmlgen.io.Crlf.normalize(content).getBytes(StandardCharsets.UTF_8);
        if (hasBom) {
            byte[] result = new byte[BOM.length + contentBytes.length];
            System.arraycopy(BOM, 0, result, 0, BOM.length);
            System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
            Files.write(configXmlPath, result);
        } else {
            Files.write(configXmlPath, contentBytes);
        }
        //++agent TASK-172
    }

    // --- Internal ---

    /** LocalString свойства, требующие замены v8:content. */
    private static final Set<String> LOCAL_STRING_PROPS = Set.of(
            "Synonym", "BriefInformation", "DetailedInformation", "Copyright",
            "VendorInformationAddress", "ConfigurationInformationAddress");

    private void setSimpleProperty(String propName, String value) {
        // LocalString: replace <v8:content> inside the property block
        if (LOCAL_STRING_PROPS.contains(propName)) {
            setLocalStringProperty(propName, value);
            return;
        }

        // Try replace existing non-empty property
        Pattern nonEmpty = Pattern.compile(
                "<" + Pattern.quote(propName) + ">([^<]*)</" + Pattern.quote(propName) + ">");
        Matcher m = nonEmpty.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("<" + propName + ">" + Matcher.quoteReplacement(escapeXml(value))
                    + "</" + propName + ">");
            return;
        }

        // Try replace self-closing
        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        m = selfClose.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("<" + propName + ">" + Matcher.quoteReplacement(escapeXml(value))
                    + "</" + propName + ">");
            return;
        }

        // Try replace empty pair
        Pattern emptyPair = Pattern.compile(
                "<" + Pattern.quote(propName) + "></" + Pattern.quote(propName) + ">");
        m = emptyPair.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("<" + propName + ">" + Matcher.quoteReplacement(escapeXml(value))
                    + "</" + propName + ">");
        }
    }

    private void setLocalStringProperty(String propName, String value) {
        // Case 1: already has <v8:content>...</v8:content> inside this property
        Pattern contentPat = Pattern.compile(
                "(<" + Pattern.quote(propName) + ">.*?<v8:content>)[^<]*(</v8:content>)",
                Pattern.DOTALL);
        Matcher m = contentPat.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(m.group(1))
                    + Matcher.quoteReplacement(escapeXml(value))
                    + Matcher.quoteReplacement(m.group(2)));
            return;
        }

        // Case 2: self-closing or empty — expand to full LocalString block
        String replacement = "<" + propName + ">\n"
                + "\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t<v8:content>" + escapeXml(value) + "</v8:content>\n"
                + "\t\t\t\t</v8:item>\n"
                + "\t\t\t</" + propName + ">";

        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        m = selfClose.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(replacement));
            return;
        }

        Pattern emptyPair = Pattern.compile(
                "<" + Pattern.quote(propName) + "></" + Pattern.quote(propName) + ">");
        m = emptyPair.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(replacement));
        }
    }

    /**
     * Отключить guard «файл объекта существует» перед регистрацией в ChildObjects.
     * По умолчанию guard включён: без файла объекта получается "висящая" регистрация
     * в Configuration.xml, конфигуратор упадёт при загрузке.
     */
    public void setSkipFileCheck(boolean skip) {
        this.skipFileCheck = skip;
    }

    private void insertChildObject(String type, String name) {
        // Validate type
        if (!CHILD_TYPE_ORDER.contains(type)) {
            throw new IllegalArgumentException("Unknown ChildObject type: '" + type
                    + "'. Valid types: " + CHILD_TYPE_ORDER);
        }

        if (!skipFileCheck) {
            ensureObjectFileExists(type, name);
        }

        String entry = "<" + type + ">" + escapeXml(name) + "</" + type + ">";

        // Check if already exists
        if (content.contains(entry)) return;

        // Find canonical insertion position
        int typeOrder = CHILD_TYPE_ORDER.indexOf(type);

        // Find </ChildObjects> or <ChildObjects/>
        if (content.contains("<ChildObjects/>")) {
            content = content.replace("<ChildObjects/>",
                    "<ChildObjects>\n\t\t\t" + entry + "\n\t\t</ChildObjects>");
            return;
        }

        // Find best position: after last element of same type, or before first element of later type
        String indent = "\t\t\t";

        // Try to find last element of same type to insert after (alphabetical order)
        Pattern sameType = Pattern.compile("<" + Pattern.quote(type) + ">[^<]+</" + Pattern.quote(type) + ">");
        Matcher m = sameType.matcher(content);
        int lastEnd = -1;
        String lastName = null;
        while (m.find()) {
            String existingName = content.substring(m.start() + type.length() + 2,
                    content.indexOf("</", m.start()));
            if (name.compareTo(existingName) > 0) {
                lastEnd = m.end();
                lastName = existingName;
            } else if (lastEnd == -1) {
                // Insert before this element (name comes first alphabetically)
                int lineStart = content.lastIndexOf('\n', m.start());
                if (lineStart >= 0) {
                    content = content.substring(0, lineStart + 1) + indent + entry + "\n"
                            + content.substring(lineStart + 1);
                    return;
                }
            }
        }

        if (lastEnd >= 0) {
            // Insert after lastEnd
            int nlPos = content.indexOf('\n', lastEnd);
            if (nlPos >= 0) {
                content = content.substring(0, nlPos + 1) + indent + entry + "\n"
                        + content.substring(nlPos + 1);
            } else {
                content = content.substring(0, lastEnd) + "\n" + indent + entry
                        + content.substring(lastEnd);
            }
            return;
        }

        // No existing elements of this type — find position among types
        if (typeOrder >= 0) {
            // Find first element of a later type
            for (int i = typeOrder + 1; i < CHILD_TYPE_ORDER.size(); i++) {
                String laterType = CHILD_TYPE_ORDER.get(i);
                Pattern laterPat = Pattern.compile("<" + Pattern.quote(laterType) + ">");
                Matcher laterM = laterPat.matcher(content);
                if (laterM.find()) {
                    int lineStart = content.lastIndexOf('\n', laterM.start());
                    if (lineStart >= 0) {
                        content = content.substring(0, lineStart + 1) + indent + entry + "\n"
                                + content.substring(lineStart + 1);
                        return;
                    }
                }
            }
        }

        // Fallback: insert before </ChildObjects>
        content = content.replace("</ChildObjects>", indent + entry + "\n\t\t</ChildObjects>");
    }

    private void removeChildEntry(String type, String name) {
        String escaped = Pattern.quote(escapeXml(name));
        Pattern p = Pattern.compile("[ \t]*<" + Pattern.quote(type) + ">" + escaped
                + "</" + Pattern.quote(type) + ">[ \t]*\\r?\\n?");
        content = p.matcher(content).replaceFirst("");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void ensureObjectFileExists(String type, String name) {
        Path configDir = configXmlPath.getParent();
        if (configDir == null) return;
        String dir = resolveDirectoryForType(type);
        Path objFile = configDir.resolve(dir).resolve(name + ".xml");
        if (!Files.exists(objFile)) {
            throw new IllegalArgumentException(
                    "Object file not found: " + objFile
                    + ". Создайте объект (meta/subsystem/role compile) или отключите проверку через --no-file-check.");
        }
    }

    private static String resolveDirectoryForType(String type) {
        MetadataTypeRegistry.TypeDescriptor td = MetadataTypeRegistry.get(type);
        if (td != null) return td.directory();
        // Fallback для типов вне основного реестра (Subsystem, Role, Language и др.)
        return switch (type) {
            case "Subsystem" -> "Subsystems";
            case "Role" -> "Roles";
            case "Language" -> "Languages";
            case "Style" -> "Styles";
            case "StyleItem" -> "StyleItems";
            case "CommonPicture" -> "CommonPictures";
            case "SessionParameter" -> "SessionParameters";
            case "CommonTemplate" -> "CommonTemplates";
            case "FilterCriterion" -> "FilterCriteria";
            case "CommonAttribute" -> "CommonAttributes";
            case "XDTOPackage" -> "XDTOPackages";
            case "WSReference" -> "WSReferences";
            case "SettingsStorage" -> "SettingsStorages";
            case "FunctionalOption" -> "FunctionalOptions";
            case "FunctionalOptionsParameter" -> "FunctionalOptionsParameters";
            case "CommonCommand" -> "CommonCommands";
            case "CommandGroup" -> "CommandGroups";
            case "CommonForm" -> "CommonForms";
            case "DocumentNumerator" -> "DocumentNumerators";
            case "Sequence" -> "Sequences";
            case "IntegrationService" -> "IntegrationServices";
            default -> type + "s";
        };
    }
}

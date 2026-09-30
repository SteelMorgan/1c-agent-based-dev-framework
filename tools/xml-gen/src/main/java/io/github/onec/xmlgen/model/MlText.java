package io.github.onec.xmlgen.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Представление MLText (многоязычного текста) 1С.
 *
 * JSON-формат: {@code {"ru": "Текст", "en": "Text"}}
 *
 * XML-формат (в Properties объекта):
 * <pre>
 *   &lt;Synonym&gt;
 *     &lt;v8:item&gt;
 *       &lt;v8:lang&gt;ru&lt;/v8:lang&gt;
 *       &lt;v8:content&gt;Текст&lt;/v8:content&gt;
 *     &lt;/v8:item&gt;
 *   &lt;/Synonym&gt;
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MlText {

    /** Карта lang → content. */
    private final Map<String, String> entries = new LinkedHashMap<>();

    /** Jackson: десериализует произвольные поля {"ru": "...", "en": "..."} */
    @JsonAnySetter
    public void setLang(String lang, String content) {
        entries.put(lang, content);
    }

    public Map<String, String> getEntries() {
        return entries;
    }

    /**
     * Serialise this MlText into an XML block for a named property tag.
     *
     * @param propName  tag name (e.g. "Synonym")
     * @param baseIndent  indentation prefix for the outer tag
     * @return XML string without trailing newline
     */
    public String toXml(String propName, String baseIndent) {
        if (entries.isEmpty()) {
            return baseIndent + "<" + propName + "/>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(baseIndent).append("<").append(propName).append(">\n");
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append(baseIndent).append("\t<v8:item>\n");
            sb.append(baseIndent).append("\t\t<v8:lang>").append(esc(e.getKey())).append("</v8:lang>\n");
            sb.append(baseIndent).append("\t\t<v8:content>").append(esc(e.getValue())).append("</v8:content>\n");
            sb.append(baseIndent).append("\t</v8:item>\n");
        }
        sb.append(baseIndent).append("</").append(propName).append(">");
        return sb.toString();
    }

    /**
     * Apply this MlText to an existing XML block that already contains {@code <propName>}.
     *
     * Strategy:
     * <ul>
     *   <li>For each lang in this MlText:
     *     <ul>
     *       <li>If a v8:item with that lang exists — replace its v8:content.</li>
     *       <li>Otherwise — add a new v8:item before {@code </propName>}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * If the property tag doesn't exist or is self-closing, the block is replaced entirely.
     *
     * @param block    XML text of the element containing propName
     * @param propName tag name (e.g. "Synonym")
     * @return modified XML
     */
    public String applyToBlock(String block, String propName) {
        // Case 1: self-closing <PropName/>
        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        Matcher m = selfClose.matcher(block);
        if (m.find()) {
            // Detect indent from position in block
            int pos = m.start();
            int lineStart = block.lastIndexOf('\n', pos);
            String indent = lineStart >= 0 ? block.substring(lineStart + 1, pos) : "";
            String replacement = buildFullXmlBlock(propName, indent);
            return m.replaceFirst(Matcher.quoteReplacement(replacement));
        }

        // Case 2: full <PropName>...</PropName> — patch lang by lang
        Pattern openTag = Pattern.compile("<" + Pattern.quote(propName) + ">");
        Matcher openM = openTag.matcher(block);
        if (!openM.find()) {
            return block; // tag not found, return unchanged
        }
        int propStart = openM.start();
        int propEnd = block.indexOf("</" + propName + ">", propStart);
        if (propEnd < 0) return block;
        propEnd += ("</" + propName + ">").length();

        String propBlock = block.substring(propStart, propEnd);

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String lang = entry.getKey();
            String content = entry.getValue();

            // Try to replace existing lang entry
            Pattern langPattern = Pattern.compile(
                    "(<v8:item>\\s*<v8:lang>" + Pattern.quote(lang) + "</v8:lang>\\s*<v8:content>)"
                    + "[^<]*"
                    + "(</v8:content>)",
                    Pattern.DOTALL);
            Matcher langM = langPattern.matcher(propBlock);
            if (langM.find()) {
                propBlock = langM.replaceFirst(
                        Matcher.quoteReplacement(langM.group(1))
                        + esc(content)
                        + Matcher.quoteReplacement(langM.group(2)));
            } else {
                // Insert a new item before closing tag
                String closeTag = "</" + propName + ">";
                int closeIdx = propBlock.lastIndexOf(closeTag);
                if (closeIdx >= 0) {
                    // Detect indent from surrounding items
                    int itemIdx = propBlock.lastIndexOf("<v8:item>");
                    String itemIndent = "\t\t\t\t\t"; // default
                    if (itemIdx >= 0) {
                        int iLineStart = propBlock.lastIndexOf('\n', itemIdx);
                        if (iLineStart >= 0) {
                            itemIndent = propBlock.substring(iLineStart + 1, itemIdx);
                        }
                    }
                    String newItem = itemIndent + "<v8:item>\n"
                            + itemIndent + "\t<v8:lang>" + esc(lang) + "</v8:lang>\n"
                            + itemIndent + "\t<v8:content>" + esc(content) + "</v8:content>\n"
                            + itemIndent + "</v8:item>\n";
                    propBlock = propBlock.substring(0, closeIdx) + newItem + propBlock.substring(closeIdx);
                }
            }
        }

        return block.substring(0, propStart) + propBlock + block.substring(propEnd);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private String buildFullXmlBlock(String propName, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(propName).append(">\n");
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append(indent).append("\t<v8:item>\n");
            sb.append(indent).append("\t\t<v8:lang>").append(esc(e.getKey())).append("</v8:lang>\n");
            sb.append(indent).append("\t\t<v8:content>").append(esc(e.getValue())).append("</v8:content>\n");
            sb.append(indent).append("\t</v8:item>\n");
        }
        sb.append(indent).append("</").append(propName).append(">");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Генератор/редактор файла {@code Ext/Predefined.xml} (TASK-171 D-1, D-6).
 *
 * <p>Канон (эталон {@code Catalogs/big_BarTypes/Ext/Predefined.xml}):
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <PredefinedData xmlns="http://v8.1c.ru/8.3/xcf/predef"
 *     xmlns:v8="http://v8.1c.ru/8.1/data/core"
 *     xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
 *     xmlns:xs="http://www.w3.org/2001/XMLSchema"
 *     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *     xsi:type="CatalogPredefinedItems" version="2.20">
 *     <Item id="<uuid>">
 *         <Name>M15</Name>
 *         <Code>000000004</Code>
 *         <Description>15m</Description>
 *         <IsFolder>false</IsFolder>
 *     </Item>
 * </PredefinedData>
 * }</pre>
 *
 * <p>Версия формата ({@code version}) ОБЯЗАНА совпадать с версией конфигурации,
 * иначе full-load падает «Версия формата ... отличается» (D-6) — поэтому она
 * приходит параметром, а не хардкодится.
 */
public final class PredefinedXmlWriter {

    private PredefinedXmlWriter() {}

    /** Дефолтная длина кода предопределённого элемента (как CodeLength справочника). */
    public static final int DEFAULT_CODE_WIDTH = 9;

    private static final Pattern CODE_PATTERN = Pattern.compile("<Code>([^<]*)</Code>");

    /** Описание одного предопределённого элемента. */
    public record Item(
            String name,
            String code,
            String description,
            boolean isFolder,
            List<Item> childItems,
            List<String> types,
            String accountType,
            Boolean offBalance,
            String order,
            Map<String, Boolean> accountingFlags,
            List<ExtDimensionType> extDimensionTypes,
            Boolean actionPeriodIsBase,
            List<String> displaced
    ) {
        public Item(String name, String code, String description, boolean isFolder) {
            this(name, code, description, isFolder, List.of(), List.of(), null, null, null,
                    Map.of(), List.of(), null, List.of());
        }

        public Item {
            childItems = childItems == null ? List.of() : List.copyOf(childItems);
            types = types == null ? List.of() : List.copyOf(types);
            accountingFlags = accountingFlags == null ? Map.of() : new LinkedHashMap<>(accountingFlags);
            extDimensionTypes = extDimensionTypes == null ? List.of() : List.copyOf(extDimensionTypes);
            displaced = displaced == null ? List.of() : List.copyOf(displaced);
        }
    }

    public record ExtDimensionType(String name, boolean turnover, Map<String, Boolean> accountingFlags) {
        public ExtDimensionType {
            accountingFlags = accountingFlags == null ? Map.of() : new LinkedHashMap<>(accountingFlags);
        }
    }

    /**
     * {@code xsi:type} корня по XML-элементу объекта. {@code null} — тип не
     * поддерживает предопределённые элементы.
     */
    public static String xsiTypeFor(String xmlElement) {
        return switch (xmlElement) {
            case "Catalog" -> "CatalogPredefinedItems";
            case "ChartOfCharacteristicTypes" -> "PlanOfCharacteristicKindPredefinedItems";
            case "ChartOfAccounts" -> "ChartOfAccountsPredefinedItems";
            case "ChartOfCalculationTypes" -> "CalculationTypePredefinedItems";
            default -> null;
        };
    }

    /** Поддерживает ли тип объекта предопределённые элементы. */
    public static boolean supports(String xmlElement) {
        return xsiTypeFor(xmlElement) != null;
    }

    /** Полный файл Predefined.xml с заданными элементами (без BOM — добавляет вызывающий). */
    public static String buildFile(String xsiType, String formatVersion, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<PredefinedData xmlns=\"http://v8.1c.ru/8.3/xcf/predef\"")
                .append(" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"")
                .append(" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"")
                .append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" xsi:type=\"").append(xsiType).append("\"")
                .append(" version=\"").append(formatVersion).append("\">\n");
        for (Item item : items) {
            sb.append(renderItem(item, 1));
        }
        sb.append("</PredefinedData>\n");
        return sb.toString();
    }

    /** Один {@code <Item>...</Item>} с отступом в 1 таб. */
    public static String renderItem(Item item) {
        return renderItem(item, 1);
    }

    private static String renderItem(Item item, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(tabs(indent)).append("<Item id=\"").append(UuidGenerator.generate()).append("\">\n");
        element(sb, indent + 1, "Name", item.name());
        codeElement(sb, indent + 1, item.code());
        element(sb, indent + 1, "Description", item.description());
        if (!item.types().isEmpty()) {
            sb.append(tabs(indent + 1)).append("<Type>\n");
            for (String type : item.types()) {
                sb.append(tabs(indent + 2))
                        .append("<v8:Type xmlns:d4p1=\"http://v8.1c.ru/8.1/data/enterprise/current-config\">")
                        .append(esc(type))
                        .append("</v8:Type>\n");
            }
            sb.append(tabs(indent + 1)).append("</Type>\n");
        }
        if (item.accountType() != null) {
            element(sb, indent + 1, "AccountType", item.accountType());
            element(sb, indent + 1, "OffBalance", String.valueOf(Boolean.TRUE.equals(item.offBalance())));
            element(sb, indent + 1, "Order", item.order() == null ? item.code() : item.order());
            renderAccountingFlags(sb, indent + 1, item.accountingFlags());
            renderExtDimensionTypes(sb, indent + 1, item.extDimensionTypes());
        } else if (item.actionPeriodIsBase() != null) {
            element(sb, indent + 1, "ActionPeriodIsBase", String.valueOf(item.actionPeriodIsBase()));
            if (!item.displaced().isEmpty()) {
                sb.append(tabs(indent + 1)).append("<Displaced>\n");
                for (String calculationType : item.displaced()) {
                    element(sb, indent + 2, "CalculationType", calculationType);
                }
                sb.append(tabs(indent + 1)).append("</Displaced>\n");
            }
        } else {
            element(sb, indent + 1, "IsFolder", String.valueOf(item.isFolder()));
        }
        if (!item.childItems().isEmpty()) {
            sb.append(tabs(indent + 1)).append("<ChildItems>\n");
            for (Item child : item.childItems()) {
                sb.append(renderItem(child, indent + 2));
            }
            sb.append(tabs(indent + 1)).append("</ChildItems>\n");
        }
        sb.append(tabs(indent)).append("</Item>\n");
        return sb.toString();
    }

    private static void renderAccountingFlags(StringBuilder sb, int indent, Map<String, Boolean> flags) {
        if (flags.isEmpty()) {
            return;
        }
        sb.append(tabs(indent)).append("<AccountingFlags>\n");
        for (Map.Entry<String, Boolean> entry : ordered(flags).entrySet()) {
            sb.append(tabs(indent + 1)).append("<Flag ref=\"").append(esc(entry.getKey())).append("\">")
                    .append(entry.getValue()).append("</Flag>\n");
        }
        sb.append(tabs(indent)).append("</AccountingFlags>\n");
    }

    private static void renderExtDimensionTypes(StringBuilder sb, int indent, List<ExtDimensionType> dimensions) {
        if (dimensions.isEmpty()) {
            sb.append(tabs(indent)).append("<ExtDimensionTypes/>\n");
            return;
        }
        sb.append(tabs(indent)).append("<ExtDimensionTypes>\n");
        for (ExtDimensionType dimension : dimensions) {
            sb.append(tabs(indent + 1)).append("<ExtDimensionType name=\"")
                    .append(esc(dimension.name())).append("\">\n");
            element(sb, indent + 2, "Turnover", String.valueOf(dimension.turnover()));
            renderAccountingFlags(sb, indent + 2, dimension.accountingFlags());
            sb.append(tabs(indent + 1)).append("</ExtDimensionType>\n");
        }
        sb.append(tabs(indent)).append("</ExtDimensionTypes>\n");
    }

    private static Map<String, Boolean> ordered(Map<String, Boolean> flags) {
        return flags instanceof LinkedHashMap<String, Boolean> ? flags : new LinkedHashMap<>(flags);
    }

    private static void element(StringBuilder sb, int indent, String name, String value) {
        sb.append(tabs(indent)).append("<").append(name).append(">")
                .append(esc(value)).append("</").append(name).append(">\n");
    }

    private static void codeElement(StringBuilder sb, int indent, String code) {
        if (code == null || code.isEmpty()) {
            sb.append(tabs(indent)).append("<Code/>\n");
        } else {
            element(sb, indent, "Code", code);
        }
    }

    private static String tabs(int indent) {
        return "\t".repeat(indent);
    }

    /**
     * Вставить {@code <Item>} перед закрывающим {@code </PredefinedData>} в
     * существующем содержимом файла (BOM сохраняет вызывающий через ByteSafe).
     */
    public static String appendItem(String content, Item item) {
        String entry = renderItem(item);
        int idx = content.lastIndexOf("</PredefinedData>");
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Некорректный Predefined.xml: нет закрывающего </PredefinedData>");
        }
        return content.substring(0, idx) + entry + content.substring(idx);
    }

    /** Следующий числовой код (max существующих + 1), либо 1, если кодов нет. */
    public static int nextCodeNumber(String content) {
        int max = 0;
        Matcher m = CODE_PATTERN.matcher(content);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1).trim());
                if (v > max) max = v;
            } catch (NumberFormatException ignored) {
                // нечисловой код — пропускаем
            }
        }
        return max + 1;
    }

    /** Ширина кода по первому {@code <Code>} в содержимом, иначе {@code defaultWidth}. */
    public static int detectCodeWidth(String content, int defaultWidth) {
        Matcher m = CODE_PATTERN.matcher(content);
        if (m.find()) {
            String code = m.group(1).trim();
            if (!code.isEmpty()) return code.length();
        }
        return defaultWidth;
    }

    /** Код в формате 1С: целое, дополненное нулями слева до {@code width} (минимум — без обрезки). */
    public static String formatCode(int number, int width) {
        String s = String.valueOf(number);
        if (s.length() >= width) return s;
        return "0".repeat(width - s.length()) + s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

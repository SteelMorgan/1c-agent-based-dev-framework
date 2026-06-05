package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.util.List;
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
    public record Item(String name, String code, String description, boolean isFolder) {}

    /**
     * {@code xsi:type} корня по XML-элементу объекта. {@code null} — тип не
     * поддерживает предопределённые элементы.
     */
    public static String xsiTypeFor(String xmlElement) {
        return switch (xmlElement) {
            case "Catalog" -> "CatalogPredefinedItems";
            case "ChartOfCharacteristicTypes" -> "ChartOfCharacteristicTypesPredefinedItems";
            case "ChartOfAccounts" -> "ChartOfAccountsPredefinedItems";
            case "ChartOfCalculationTypes" -> "ChartOfCalculationTypesPredefinedItems";
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
            sb.append(renderItem(item));
        }
        sb.append("</PredefinedData>\n");
        return sb.toString();
    }

    /** Один {@code <Item>...</Item>} с отступом в 1 таб. */
    public static String renderItem(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append("\t<Item id=\"").append(UuidGenerator.generate()).append("\">\n");
        sb.append("\t\t<Name>").append(esc(item.name())).append("</Name>\n");
        sb.append("\t\t<Code>").append(esc(item.code())).append("</Code>\n");
        sb.append("\t\t<Description>").append(esc(item.description())).append("</Description>\n");
        sb.append("\t\t<IsFolder>").append(item.isFolder()).append("</IsFolder>\n");
        sb.append("\t</Item>\n");
        return sb.toString();
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

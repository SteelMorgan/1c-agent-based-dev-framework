package io.github.onec.xmlgen.writer;

import java.util.List;

public final class ExchangePlanContentWriter {

    private ExchangePlanContentWriter() {}

    public record Item(String metadata, String autoRecord) {}

    public static String buildFile(String formatVersion, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ExchangePlanContent xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"")
                .append(" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"")
                .append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" version=\"").append(esc(formatVersion)).append("\">\n");
        for (Item item : items) {
            sb.append(renderItem(item));
        }
        sb.append("</ExchangePlanContent>\n");
        return sb.toString();
    }

    public static String appendItem(String content, Item item) {
        int idx = content.lastIndexOf("</ExchangePlanContent>");
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Invalid Content.xml: missing closing </ExchangePlanContent>");
        }
        return content.substring(0, idx) + renderItem(item) + content.substring(idx);
    }

    public static String renderItem(Item item) {
        String autoRecord = item.autoRecord() == null || item.autoRecord().isBlank()
                ? "Deny" : item.autoRecord();
        return "\t<Item>\n"
                + "\t\t<Metadata>" + esc(item.metadata()) + "</Metadata>\n"
                + "\t\t<AutoRecord>" + esc(autoRecord) + "</AutoRecord>\n"
                + "\t</Item>\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

package io.github.onec.xmlgen.form.fromobject;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Определяет purpose формы (item/list/choice/folder/record/default) по имени папки формы
 * в OutputPath вида {@code .../Forms/<FormName>/Ext/Form.xml}.
 */
public final class PurposeResolver {

    private PurposeResolver() {}

    public static String resolve(Path outputPath, String objectType) {
        String formName = extractFormFolderName(outputPath);
        if (formName != null) {
            String p = matchPurpose(formName);
            if (p != null) return p;
        }
        return defaultPurpose(objectType);
    }

    /** Имя папки формы — сегмент сразу под "Forms". */
    public static String extractFormFolderName(Path outputPath) {
        if (outputPath == null) return null;
        Path p = outputPath.toAbsolutePath().normalize();
        int count = p.getNameCount();
        for (int i = 0; i < count - 1; i++) {
            if ("Forms".equalsIgnoreCase(p.getName(i).toString())) {
                return p.getName(i + 1).toString();
            }
        }
        return null;
    }

    /** Имя объекта = папка над Forms. */
    public static String extractObjectName(Path outputPath) {
        if (outputPath == null) return null;
        Path p = outputPath.toAbsolutePath().normalize();
        int count = p.getNameCount();
        for (int i = 0; i < count; i++) {
            if ("Forms".equalsIgnoreCase(p.getName(i).toString()) && i > 0) {
                return p.getName(i - 1).toString();
            }
        }
        return null;
    }

    /** Папка типа (Catalogs/Documents/…) — две папки над Forms. */
    public static String extractTypePlural(Path outputPath) {
        if (outputPath == null) return null;
        Path p = outputPath.toAbsolutePath().normalize();
        int count = p.getNameCount();
        for (int i = 0; i < count; i++) {
            if ("Forms".equalsIgnoreCase(p.getName(i).toString()) && i > 1) {
                return p.getName(i - 2).toString();
            }
        }
        return null;
    }

    private static String matchPurpose(String formName) {
        String n = formName.toLowerCase(Locale.ROOT);
        if (n.contains("формасписка") || n.contains("listform") || n.equals("list")) return "list";
        if (n.contains("формавыбора") || n.contains("choiceform") || n.equals("choice")) return "choice";
        if (n.contains("формагруппы") || n.contains("folderform") || n.equals("folder")) return "folder";
        if (n.contains("формазаписи") || n.contains("recordform") || n.equals("record")) return "record";
        if (n.contains("формадокумента") || n.contains("формаэлемента")
                || n.contains("itemform") || n.contains("documentform") || n.equals("item")) return "item";
        return null;
    }

    public static String defaultPurpose(String objectType) {
        if (objectType == null) return "item";
        switch (objectType) {
            case "InformationRegister": return "record";
            case "AccumulationRegister": return "list";
            case "DataProcessor":
            case "Report": return "default";
            default: return "item";
        }
    }
}

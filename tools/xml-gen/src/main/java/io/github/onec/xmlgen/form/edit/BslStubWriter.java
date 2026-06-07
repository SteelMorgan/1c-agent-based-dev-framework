package io.github.onec.xmlgen.form.edit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Дописывает пустые заглушки BSL-обработчиков в {@code Ext/Form/Module.bsl}
 * рядом с Form.xml. По запросу пользователя: это <b>формальный</b> stub
 * (не код), чтобы 1С не жаловался на отсутствие обработчиков.
 *
 * <p>Формат заглушки для клиентского события:</p>
 * <pre>
 * &НаКлиенте
 * Процедура ПолеОнChange(Элемент)
 *
 * КонецПроцедуры
 * </pre>
 *
 * <p>Если процедура с таким именем уже существует в модуле — заглушка не дописывается.</p>
 */
public class BslStubWriter {

    private final Path modulePath;

    public BslStubWriter(Path formXmlPath) {
        this.modulePath = resolveModulePath(formXmlPath);
    }

    /**
     * Для канонической формы {@code .../Forms/FormName/Ext/Form.xml}
     * модуль находится по пути {@code .../Forms/FormName/Ext/Form/Module.bsl}.
     *
     * <p>Старую тестовую раскладку {@code .../Ext/Form/Form.xml} тоже поддерживаем:
     * там модуль лежит рядом с файлом.</p>
     */
    public static Path resolveModulePath(Path formXml) {
        if (formXml == null) return null;
        Path parent = formXml.getParent();
        if (parent == null) return null;
        Path fileName = formXml.getFileName();
        Path parentName = parent.getFileName();
        if (fileName != null
                && "Form.xml".equalsIgnoreCase(fileName.toString())
                && parentName != null
                && "Ext".equalsIgnoreCase(parentName.toString())) {
            return parent.resolve("Form").resolve("Module.bsl");
        }
        return parent.resolve("Module.bsl");
    }

    public Path getModulePath() {
        return modulePath;
    }

    /**
     * Дописать стабы для всех указанных handler-refs. Возвращает список имён
     * реально добавленных процедур (т.е. ранее отсутствовавших в модуле).
     */
    public List<String> appendStubs(List<FormEventsWriter.HandlerRef> handlers) throws IOException {
        List<String> added = new ArrayList<>();
        if (handlers == null || handlers.isEmpty() || modulePath == null) return added;

        String existing = readOrEmpty(modulePath);
        StringBuilder appended = new StringBuilder();

        for (FormEventsWriter.HandlerRef ref : handlers) {
            if (procedureExists(existing, ref.name) || containsInAppended(appended.toString(), ref.name)) {
                continue;
            }
            EventSignature.Signature sig = ref.formLevel
                    ? EventSignature.forForm(ref.eventName)
                    : EventSignature.forElement(ref.eventName);

            appended.append("\n");
            appended.append(sig.directive.asPragma()).append('\n');
            appended.append("\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u0430 ")
                    .append(ref.name)
                    .append('(')
                    .append(sig.parameters)
                    .append(")\n\n")
                    .append("\u041a\u043e\u043d\u0435\u0446\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u044b\n");
            added.add(ref.name);
        }

        if (appended.length() > 0) {
            String newContent = existing;
            if (!newContent.isEmpty() && !newContent.endsWith("\n")) {
                newContent = newContent + "\n";
            }
            newContent = newContent + appended;
            writeText(modulePath, newContent);
        }
        return added;
    }

    private static String readOrEmpty(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return "";
        byte[] bytes = Files.readAllBytes(path);
        // Strip UTF-8 BOM если есть
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeText(Path path, String content) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        //++agent TASK-172 [02.06.2026 07:19:00]
        // Канон Designer (_Демо): .bsl-модули — BOM + CRLF. Stub дописывается к
        // существующему модулю (литералы \n), нормализуем итог к CRLF идемпотентно.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    /**
     * Поиск {@code Процедура|Функция <name>(} в модуле (регистр кириллицы учитываем
     * как есть; в BSL имена процедур case-insensitive по спецификации, но в файле
     * обычно написаны единообразно — поэтому сначала ищем точное совпадение,
     * затем case-insensitive fallback).
     */
    private static boolean procedureExists(String moduleText, String procName) {
        if (moduleText == null || moduleText.isEmpty()) return false;
        String procKw = "\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u0430";
        String funcKw = "\u0424\u0443\u043d\u043a\u0446\u0438\u044f";
        Pattern p = Pattern.compile(
                "(?im)^\\s*(?:" + procKw + "|" + funcKw + ")\\s+" + Pattern.quote(procName) + "\\s*\\(");
        return p.matcher(moduleText).find();
    }

    private static boolean containsInAppended(String text, String procName) {
        return text.contains(" " + procName + "(");
    }
}

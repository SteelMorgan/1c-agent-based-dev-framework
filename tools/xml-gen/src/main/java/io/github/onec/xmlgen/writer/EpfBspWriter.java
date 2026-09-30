package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.BspCommandType;
import io.github.onec.xmlgen.model.BspKind;
import io.github.onec.xmlgen.model.BspTarget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Рендерит BSL-фрагменты для БСП-обвязки EPF/ERF: функцию {@code СведенияОВнешнейОбработке()},
 * процедуры-обработчики и блок отдельной команды.
 * <p>
 * Чистый рендерер — не редактирует файлы и не валидирует контекст. Это даёт чёткое разделение
 * между «знанием шаблонов» и «знанием BSL-модуля» (последнее в {@link io.github.onec.xmlgen.editor.BslModuleEditor}).
 */
public class EpfBspWriter {

    private static final String TPL_INFO = "info-function.bsl.template";
    private static final String TPL_SERVER_GLOBAL = "handler-server.bsl.template";
    private static final String TPL_SERVER_ASSIGN = "handler-server-assignable.bsl.template";
    private static final String TPL_PRINT = "handler-print.bsl.template";
    private static final String TPL_CLIENT_GLOBAL = "handler-client-global.bsl.template";
    private static final String TPL_CLIENT_ASSIGN = "handler-client-assignable.bsl.template";
    private static final String TPL_COMMAND_BLOCK = "command-block.bsl.template";

    public static final String DEFAULT_API_VERSION = "2.2.2.1";
    public static final String DEFAULT_VERSION = "1.0";

    private final Map<String, String> templateCache = new HashMap<>();

    /**
     * Рендер функции {@code СведенияОВнешнейОбработке()}.
     *
     * @param kind       вид обработки
     * @param targets    объекты назначения (для назначаемых видов)
     * @param cmdType    тип команды (если {@code null} — берётся {@code kind.defaultCommandType()})
     * @param apiVersion версия API БСП (если {@code null} — {@link #DEFAULT_API_VERSION})
     * @param version    версия обработки ({@code null} → {@link #DEFAULT_VERSION})
     * @return текст функции (без обрамляющих пустых строк), с табами как отступ
     */
    public String renderInfoFunction(BspKind kind,
                                     List<BspTarget> targets,
                                     BspCommandType cmdType,
                                     String apiVersion,
                                     String version) {
        if (kind == null) throw new IllegalArgumentException("kind required");
        BspCommandType effectiveType = cmdType != null ? cmdType : kind.defaultCommandType();
        String api = apiVersion != null && !apiVersion.isBlank() ? apiVersion : DEFAULT_API_VERSION;
        String ver = version != null && !version.isBlank() ? version : DEFAULT_VERSION;

        String naznachenie = "";
        if (kind.requiresTarget()) {
            if (targets == null || targets.isEmpty()) {
                throw new IllegalArgumentException(
                        "Kind '" + kind + "' is assignable and requires at least one target");
            }
            StringBuilder sb = new StringBuilder("\n");
            for (BspTarget t : targets) {
                sb.append("\tПараметрыРегистрации.Назначение.Добавить(\"")
                        .append(t.asBslString()).append("\");\n");
            }
            naznachenie = sb.toString();
        }

        String modifier = "";
        if (kind.usesPrintModifier()) {
            modifier = "\tНоваяКоманда.Модификатор          = \"ПечатьMXL\";\n";
        }

        String tpl = loadTemplate(TPL_INFO);
        return tpl
                .replace("{{ВидОбработки}}", kind.apiMethodName())
                .replace("{{ТипКоманды}}", effectiveType.apiMethodName())
                .replace("{{СЕКЦИЯ_НАЗНАЧЕНИЕ}}", naznachenie)
                .replace("{{СЕКЦИЯ_МОДИФИКАТОР}}", modifier)
                .replace("{{API_Версия}}", api)
                .replace("{{Версия}}", ver);
    }

    /**
     * Рендер процедуры-обработчика для серверных видов (вызывается из ObjectModule).
     * <p>
     * Поведение:
     * <ul>
     *     <li>{@code ПечатнаяФорма}                            → {@code Процедура Печать(...)};</li>
     *     <li>{@code ЗаполнениеОбъекта/СозданиеСвязанныхОбъектов} → {@code Процедура ВыполнитьКоманду} с {@code ОбъектыНазначения};</li>
     *     <li>прочие глобальные при серверном вызове         → {@code Процедура ВыполнитьКоманду} без {@code ОбъектыНазначения}.</li>
     * </ul>
     * Если {@code cmdType} не серверный — возвращает {@code null} (обработчик не в объектном модуле).
     */
    public String renderHandlerProcedure(BspKind kind, BspCommandType cmdType) {
        BspCommandType effective = cmdType != null ? cmdType : kind.defaultCommandType();
        if (!effective.isServerHandler()) {
            return null;
        }
        if (kind == BspKind.ПечатнаяФорма) {
            return loadTemplate(TPL_PRINT);
        }
        if (kind.requiresTarget()) {
            return loadTemplate(TPL_SERVER_ASSIGN);
        }
        return loadTemplate(TPL_SERVER_GLOBAL);
    }

    /**
     * Рендер клиентского обработчика (для модуля формы), с подстановкой идентификатора первой команды.
     */
    public String renderClientHandler(BspKind kind, String identifier) {
        String tpl = kind.requiresTarget()
                ? loadTemplate(TPL_CLIENT_ASSIGN)
                : loadTemplate(TPL_CLIENT_GLOBAL);
        return tpl.replace("{{Идентификатор}}", identifier);
    }

    /**
     * Рендер блока {@code НоваяКоманда = ...} для add-command (вставляется перед {@code Возврат}).
     * Для {@code ПечатнаяФорма} добавляет строку {@code Модификатор = "ПечатьMXL"}.
     */
    public String renderCommandBlock(String identifier, String label, BspCommandType cmdType, BspKind kind) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier required");
        }
        if (label == null) label = identifier;
        String tpl = loadTemplate(TPL_COMMAND_BLOCK);
        String body = tpl
                .replace("{{Представление}}", label)
                .replace("{{Идентификатор}}", identifier)
                .replace("{{ТипКоманды}}", cmdType.apiMethodName());
        if (kind != null && kind.usesPrintModifier()) {
            // дополнительная строка модификатора (без удвоенной \n)
            StringBuilder sb = new StringBuilder(body);
            if (!body.endsWith("\n")) sb.append("\n");
            sb.append("\tНоваяКоманда.Модификатор          = \"ПечатьMXL\";\n");
            body = sb.toString();
        }
        return body;
    }

    /** Загрузка ресурса-шаблона из classpath. */
    private String loadTemplate(String name) {
        return templateCache.computeIfAbsent(name, this::loadTemplateImpl);
    }

    private String loadTemplateImpl(String name) {
        String path = "/templates/bsp/" + name;
        try (InputStream in = EpfBspWriter.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Template not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read template " + path, e);
        }
    }
}

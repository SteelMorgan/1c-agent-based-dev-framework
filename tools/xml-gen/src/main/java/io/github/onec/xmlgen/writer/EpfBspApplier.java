package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.editor.BslModuleEditor;
import io.github.onec.xmlgen.model.BspCommandType;
import io.github.onec.xmlgen.model.BspKind;
import io.github.onec.xmlgen.model.BspTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Высокоуровневая операция применения BSP-обвязки к файловой системе EPF/ERF:
 * находит {@code ObjectModule.bsl}, использует {@link EpfBspWriter} для рендеринга
 * и {@link BslModuleEditor} для текстовой модификации модуля.
 * <p>
 * Реализует контракт SKILL §1 (bsp-init) и §2 (bsp-add-command).
 */
public class EpfBspApplier {

    public static final String REGION_PROGRAMMATIC_INTERFACE = "ПрограммныйИнтерфейс";
    private static final String INFO_FUNCTION = "СведенияОВнешнейОбработке";
    private static final String PROC_VYPOLNIT = "ВыполнитьКоманду";
    private static final String PROC_PECHAT = "Печать";

    private final EpfBspWriter writer;

    public EpfBspApplier() {
        this.writer = new EpfBspWriter();
    }

    public EpfBspApplier(EpfBspWriter writer) {
        this.writer = writer;
    }

    /** Параметры для {@code epf bsp-init}. */
    public static final class InitOptions {
        public BspKind kind;
        public List<BspTarget> targets;
        public BspCommandType commandType;     // optional, default by kind
        public String apiVersion;              // optional, default 2.2.2.1
        public String version;                 // optional, default 1.0
    }

    /** Параметры для {@code epf bsp-add-command}. */
    public static final class AddCommandOptions {
        public String identifier;
        public String label;
        public BspCommandType type;            // optional, derived from kind in module
        public String form;                    // required for ВызовКлиентскогоМетода
    }

    /**
     * Применить bsp-init к каталогу EPF/ERF.
     *
     * @param epfDir каталог обработки (где лежит {@code Ext/ObjectModule.bsl})
     * @param opts   опции
     */
    public void init(Path epfDir, InitOptions opts) throws IOException {
        Path module = resolveObjectModule(epfDir);
        BslModuleEditor editor = new BslModuleEditor(module);
        if (editor.findFunction(INFO_FUNCTION).isPresent()) {
            throw new IllegalStateException(
                    "Function " + INFO_FUNCTION + "() already exists in "
                            + module + ". Use 'epf bsp-add-command' to add additional commands.");
        }
        // edge case: глобальный с --target → warning + игнор
        List<BspTarget> effectiveTargets = opts.targets;
        if (!opts.kind.requiresTarget() && opts.targets != null && !opts.targets.isEmpty()) {
            System.err.println("Warning: kind '" + opts.kind + "' is global; ignoring --target");
            effectiveTargets = List.of();
        }
        // edge case: assignable без target — обработается EpfBspWriter
        String infoFn = writer.renderInfoFunction(
                opts.kind, effectiveTargets, opts.commandType, opts.apiVersion, opts.version);

        editor.insertIntoRegion(
                REGION_PROGRAMMATIC_INTERFACE,
                infoFn,
                BslModuleEditor.InsertPosition.END);

        // Если серверный вид — добавляем обработчик в ту же область, после функции.
        BspCommandType effectiveCmd = opts.commandType != null
                ? opts.commandType : opts.kind.defaultCommandType();
        String handler = writer.renderHandlerProcedure(opts.kind, effectiveCmd);
        if (handler != null) {
            editor.insertIntoRegion(
                    REGION_PROGRAMMATIC_INTERFACE,
                    handler,
                    BslModuleEditor.InsertPosition.END);
        }
        editor.save();
    }

    /**
     * Применить bsp-add-command.
     */
    public void addCommand(Path epfDir, AddCommandOptions opts) throws IOException {
        if (opts.identifier == null || opts.identifier.isBlank()) {
            throw new IllegalArgumentException("--id is required");
        }
        if (opts.label == null || opts.label.isBlank()) {
            throw new IllegalArgumentException("--label is required");
        }
        Path module = resolveObjectModule(epfDir);
        BslModuleEditor editor = new BslModuleEditor(module);
        if (editor.findFunction(INFO_FUNCTION).isEmpty()) {
            throw new IllegalStateException(
                    "Function " + INFO_FUNCTION + "() not found in " + module
                            + ". Run 'epf bsp-init' first.");
        }
        // Определим вид из существующего кода
        BspKind kind = detectKindFromModule(editor);
        BspCommandType cmdType = opts.type != null ? opts.type : kind.defaultCommandType();

        // Шаг 1: вставить блок команды перед "Возврат"
        String commandBlock = writer.renderCommandBlock(opts.identifier, opts.label, cmdType, kind);
        editor.appendBeforeReturn(INFO_FUNCTION, commandBlock);

        // Шаг 2: обработчик
        if (cmdType.isServerHandler()) {
            applyServerHandler(editor, kind, opts.identifier);
        } else if (cmdType.isClientHandler()) {
            if (opts.form == null || opts.form.isBlank()) {
                editor.save();
                throw new IllegalArgumentException(
                        "--form is required for ВызовКлиентскогоМетода");
            }
            Path formModule = resolveFormModule(epfDir, opts.form);
            if (!Files.exists(formModule)) {
                editor.save(); // основной модуль уже изменён — сохраним прогресс по команде
                throw new IllegalStateException(
                        "Form module not found: " + formModule);
            }
            applyClientHandler(formModule, kind, opts.identifier);
        }
        editor.save();
    }

    private void applyServerHandler(BslModuleEditor editor, BspKind kind, String identifier) {
        if (kind == BspKind.ПечатнаяФорма) {
            applyPrintHandler(editor, identifier);
            return;
        }
        applyExecuteCommandHandler(editor, kind, identifier);
    }

    private void applyExecuteCommandHandler(BslModuleEditor editor, BspKind kind, String identifier) {
        if (editor.findProcedure(PROC_VYPOLNIT).isPresent()) {
            // Добавить ветку
            String body = "\t\t// TODO: Реализация " + identifier;
            String cond = "ИдентификаторКоманды = \"" + identifier + "\"";
            editor.appendBranchToIfChain(PROC_VYPOLNIT, cond, body);
        } else {
            // Создать процедуру с одной веткой Если
            String params = kind.requiresTarget()
                    ? "ИдентификаторКоманды, ОбъектыНазначения, ПараметрыВыполненияКоманды"
                    : "ИдентификаторКоманды, ПараметрыВыполненияКоманды";
            String src = "Процедура ВыполнитьКоманду(" + params + ") Экспорт\n"
                    + "\n"
                    + "\tЕсли ИдентификаторКоманды = \"" + identifier + "\" Тогда\n"
                    + "\t\t// TODO: Реализация " + identifier + "\n"
                    + "\tКонецЕсли;\n"
                    + "\n"
                    + "КонецПроцедуры\n";
            editor.findOrCreateProcedure(PROC_VYPOLNIT, src, REGION_PROGRAMMATIC_INTERFACE);
        }
    }

    private void applyPrintHandler(BslModuleEditor editor, String identifier) {
        String block = "\tПечатнаяФорма = УправлениеПечатью.СведенияОПечатнойФорме(КоллекцияПечатныхФорм, \""
                + identifier + "\");\n"
                + "\tЕсли ПечатнаяФорма <> Неопределено Тогда\n"
                + "\t\tПечатнаяФорма.ТабличныйДокумент = Сформировать" + identifier
                + "(МассивОбъектов, ОбъектыПечати);\n"
                + "\t\tПечатнаяФорма.СинонимМакета = НСтр(\"ru = '" + identifier + "'\");\n"
                + "\tКонецЕсли;\n";
        if (editor.findProcedure(PROC_PECHAT).isPresent()) {
            // Вставить перед КонецПроцедуры (используем appendBeforeReturn-like, но для процедуры — нет возврата → вставка перед концом)
            BslModuleEditor.Range proc = editor.findProcedure(PROC_PECHAT).orElseThrow();
            // вставим через insert: для простоты используем «временно appendBeforeReturn» — он
            // вставит перед концом, если нет Возврат
            editor.appendBeforeReturn(PROC_PECHAT, block);
        } else {
            String src = "Процедура Печать(МассивОбъектов, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода) Экспорт\n"
                    + "\n"
                    + block
                    + "\n"
                    + "КонецПроцедуры\n";
            editor.findOrCreateProcedure(PROC_PECHAT, src, REGION_PROGRAMMATIC_INTERFACE);
        }
    }

    private void applyClientHandler(Path formModulePath, BspKind kind, String identifier) throws IOException {
        BslModuleEditor formEditor = new BslModuleEditor(formModulePath);
        if (formEditor.findProcedure(PROC_VYPOLNIT).isPresent()) {
            String body = "\t\t// TODO: Реализация " + identifier;
            String cond = "ИдентификаторКоманды = \"" + identifier + "\"";
            formEditor.appendBranchToIfChain(PROC_VYPOLNIT, cond, body);
        } else {
            String handler = writer.renderClientHandler(kind, identifier);
            formEditor.findOrCreateProcedure(PROC_VYPOLNIT, handler, null);
        }
        formEditor.save();
    }

    /**
     * Распознать вид обработки из существующего модуля (по строке {@code ВидОбработки...()}).
     */
    private BspKind detectKindFromModule(BslModuleEditor editor) {
        for (String line : editor.lines()) {
            for (BspKind k : BspKind.values()) {
                if (line.contains(k.apiMethodName() + "(")) {
                    return k;
                }
            }
        }
        throw new IllegalStateException(
                "Cannot detect BspKind from existing module: no 'ВидОбработки...()' call found");
    }

    /** Найти ObjectModule.bsl. Поддерживает Designer-формат. */
    public static Path resolveObjectModule(Path epfDir) {
        // Designer: <epfDir>/Ext/ObjectModule.bsl
        Path designer = epfDir.resolve("Ext").resolve("ObjectModule.bsl");
        if (Files.exists(designer)) return designer;
        // Если передан путь прямо к файлу
        if (Files.exists(epfDir) && Files.isRegularFile(epfDir)
                && epfDir.getFileName().toString().equals("ObjectModule.bsl")) {
            return epfDir;
        }
        // EDT: <epfDir>/ObjectModule.bsl
        Path edt = epfDir.resolve("ObjectModule.bsl");
        if (Files.exists(edt)) return edt;
        throw new IllegalStateException(
                "ObjectModule.bsl not found under " + epfDir
                        + " (tried Ext/ObjectModule.bsl and ObjectModule.bsl)");
    }

    /** Путь к Module.bsl формы. */
    public static Path resolveFormModule(Path epfDir, String formName) {
        Path p1 = epfDir.resolve("Forms").resolve(formName).resolve("Ext").resolve("Form").resolve("Module.bsl");
        if (Files.exists(p1)) return p1;
        Path p2 = epfDir.resolve("Forms").resolve(formName).resolve("Module.bsl");
        return p2;
    }
}

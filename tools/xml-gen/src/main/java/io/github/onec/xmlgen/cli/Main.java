package io.github.onec.xmlgen.cli;

import java.util.Arrays;

/**
 * Entry point для xml-gen CLI.
 */
public class Main {
    /** Fallback-версия, если в манифесте jar нет Implementation-Version. */
    private static final String FALLBACK_VERSION = "0.1.1-SNAPSHOT";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        // TASK-171 D-11a: --version / -V печатает версию и выходит с кодом 0.
        if ("--version".equals(args[0]) || "-V".equals(args[0])) {
            System.out.println("xml-gen " + version());
            System.exit(0);
        }
        if ("--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            System.exit(0);
        }

        // TASK-155 A1 (UX-долг): --debug флаг как идеоматичная альтернатива XML_GEN_DEBUG env.
        // Вынимаем флаг из args ДО маршрутизации, чтобы он не попал в Commands.execute и не
        // ломал доменные парсеры. Флаг можно ставить в любом месте командной строки.
        boolean debug = System.getenv("XML_GEN_DEBUG") != null;
        if (Arrays.asList(args).contains("--debug")) {
            debug = true;
            args = Arrays.stream(args).filter(a -> !"--debug".equals(a)).toArray(String[]::new);
            if (args.length == 0) {
                printUsage();
                System.exit(1);
            }
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            Commands.execute(command, commandArgs);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            if (debug) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        } catch (Exception e) {
            // TASK-155 A1: unified CLI exception envelope — clean exit=1 without stack trace.
            // Stack trace is printed only when --debug flag is passed OR XML_GEN_DEBUG env is set.
            // exit=2 is reserved for JVM/infrastructure failures (missing JAR, JVM crash), not domain errors.
            String msg = e.getMessage();
            System.err.println("ERROR: " + (msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName()));
            if (debug) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    /** Версия из манифеста jar (Implementation-Version), иначе fallback. */
    static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return (v != null && !v.isBlank()) ? v : FALLBACK_VERSION;
    }

    private static void printUsage() {
        System.out.println("xml-gen " + version() + " - 1C XML metadata generator & editor");
        System.out.println();
        // TASK-171 D-11c: идиоматичное имя CLI вместо "java -jar xml-gen.jar".
        System.out.println("Usage: xml-gen <command> [options] <input> <output>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  meta compile          - Compile metadata object (Catalog/Document/Enum/CommonModule/...) from JSON DSL");
        System.out.println("  meta edit             - Edit metadata object (add-attribute/add-enumValue/add-predefined/...)");
        System.out.println("  meta info             - Inspect metadata object");
        System.out.println("  meta validate         - Validate metadata object");
        System.out.println("  meta remove           - Remove metadata object");
        System.out.println("  config init           - Scaffold a new Configuration.xml");
        System.out.println("  config edit           - Edit Configuration.xml (properties, ChildObjects, DefaultRoles)");
        System.out.println("  config info|validate  - Inspect / validate Configuration.xml");
        System.out.println("  epf init              - Create new EPF structure");
        System.out.println("  epf add-form          - Add form to EPF");
        System.out.println("  epf add-template      - Add template to EPF");
        System.out.println("  epf add-attribute     - Add attribute to EPF");
        System.out.println("  epf add-tabular-section - Add tabular section to EPF");
        System.out.println("  form compile          - Compile form from JSON DSL or metadata (--from-object)");
        System.out.println("  form edit --json      - Apply JSON spec of mutations to existing form");
        System.out.println("  form add-attribute    - Add attribute to form");
        System.out.println("  form add-element      - Add element to form");
        System.out.println("  form add-command      - Add command to form");
        System.out.println("  form remove-element   - Remove element from form");
        System.out.println("  form move-element     - Move element in form");
        System.out.println("  role compile          - Compile role from JSON DSL");
        System.out.println("  role add-object       - Add object rights to role");
        System.out.println("  role add-right        - Add right to object in role");
        System.out.println("  skd compile           - Compile SKD from JSON DSL");
        System.out.println("  skd add-parameter     - Add parameter to SKD");
        System.out.println("  skd add-field         - Add field to SKD dataset");
        System.out.println("  mxl compile           - Compile MXL from JSON DSL");
        System.out.println("  subsystem compile|info|edit|validate - Subsystems & CommandInterface");
        System.out.println("  interface ...         - CommandInterface operations");
        System.out.println("  extension ...         - Extension (CFE) operations: init/borrow/diff/validate");
        System.out.println("  template add          - Add template / help to a metadata object");
        System.out.println("  help                  - BSP help operations");
        System.out.println("  edit replace-text     - Byte-safe text replacement in XML files");
        System.out.println("  validate              - Validate 1C XML files");
        System.out.println("  support check|info    - Inspect 1C vendor support state and guard mutations");
        System.out.println("  oracle mxl            - Run behavioral oracle for MXL DSL and CLI reconstruction modes");
        System.out.println("  oracle demo           - Run parallel validation audit for _Демо XML classes");
        System.out.println("  oracle predefined-data - Run behavioral oracle for Ext/Predefined.xml via public meta CLI");
        System.out.println("  oracle exchange-plan-content - Run behavioral oracle for Ext/Content.xml via public meta CLI");
        System.out.println("  oracle mine-rules     - Mine structural rule candidates from canonical Designer XML corpus");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version, -V            - Print version and exit");
        System.out.println("  --help, -h               - Print this help and exit");
        System.out.println("  --format <designer|edt>  - Output format (default: designer)");
        System.out.println("  --verbose                - Verbose output");
        System.out.println("  --validate               - Validate JSON DSL only");
        System.out.println("  --debug                  - Print stack traces on error");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  xml-gen epf init --format designer --name МояОбработка output/");
        System.out.println("  xml-gen form compile --format designer form.json output/");
        System.out.println("  xml-gen meta compile catalog.json src/xml/");
        System.out.println("  xml-gen oracle mxl --source src/xml --out build/oracle --mode both");
        System.out.println("  xml-gen oracle mxl --source src/xml --out build/oracle --mode both --include-all");
        System.out.println("  xml-gen oracle demo --source src/xml --out build/oracle-demo --threads 8");
        System.out.println("  xml-gen oracle predefined-data --source src/xml --out build/oracle-predefined-data");
        System.out.println("  xml-gen oracle exchange-plan-content --source src/xml --out build/oracle-exchange-plan-content");
        System.out.println("  xml-gen oracle mine-rules --source src/xml --out build/oracle-rule-mining --min-support 2 --digest-limit 500 --disposition rules.json");
    }
}

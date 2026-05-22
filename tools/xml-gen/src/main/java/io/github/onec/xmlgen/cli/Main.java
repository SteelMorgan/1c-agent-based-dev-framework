package io.github.onec.xmlgen.cli;

import java.util.Arrays;

/**
 * Entry point для xml-gen CLI.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            Commands.execute(command, commandArgs);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            // TASK-155 A1: unified CLI exception envelope — clean exit=1 without stack trace
            // Stack trace is printed only when XML_GEN_DEBUG env variable is set (optional debug mode).
            // exit=2 is reserved for JVM/infrastructure failures (missing JAR, JVM crash), not domain errors.
            String msg = e.getMessage();
            System.err.println("ERROR: " + (msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName()));
            if (System.getenv("XML_GEN_DEBUG") != null) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("xml-gen - 1C XML metadata generator & editor");
        System.out.println();
        System.out.println("Usage: java -jar xml-gen.jar <command> [options] <input> <output>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  epf init              - Create new EPF structure");
        System.out.println("  epf add-form          - Add form to EPF");
        System.out.println("  epf add-template      - Add template to EPF");
        System.out.println("  epf add-attribute     - Add attribute to EPF");
        System.out.println("  epf add-tabular-section - Add tabular section to EPF");
        System.out.println("  form compile          - Compile form from JSON DSL or metadata (--from-object)");
        System.out.println("  form edit --json      - Apply JSON spec of mutations to existing form (replaces form-edit.py)");
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
        System.out.println("  edit replace-text     - Byte-safe text replacement in XML files");
        System.out.println("  validate              - Validate 1C XML files");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --format <designer|edt>  - Output format (default: designer)");
        System.out.println("  --verbose                - Verbose output");
        System.out.println("  --validate               - Validate JSON DSL only");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar xml-gen.jar epf init --format designer --name МояОбработка output/");
        System.out.println("  java -jar xml-gen.jar form compile --format designer form.json output/");
    }
}

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
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("xml-gen - 1C XML metadata generator");
        System.out.println();
        System.out.println("Usage: java -jar xml-gen.jar <command> [options] <input> <output>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  epf init          - Create new EPF structure");
        System.out.println("  epf add-form      - Add form to EPF");
        System.out.println("  epf add-template  - Add template to EPF");
        System.out.println("  form compile      - Compile form from JSON DSL");
        System.out.println("  role compile      - Compile role from JSON DSL");
        System.out.println("  mxl compile       - Compile MXL from JSON DSL");
        System.out.println("  skd compile       - Compile SKD from JSON DSL");
        System.out.println("  validate          - Validate 1C XML files");
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

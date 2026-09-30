package io.github.onec.xmlgen.oracle;

import io.github.onec.xmlgen.cli.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandPlanExecutor {

    private final Duration timeout;

    public CommandPlanExecutor() {
        this(Duration.ofSeconds(30));
    }

    public CommandPlanExecutor(Duration timeout) {
        this.timeout = timeout;
    }

    public ExecutionResult execute(CommandPlan plan) throws IOException, InterruptedException {
        Files.createDirectories(plan.sandbox());
        Path logs = plan.sandbox().resolve("logs");
        Files.createDirectories(logs);
        List<ExecutionStepResult> results = new ArrayList<>();
        for (CommandStep step : plan.steps()) {
            ExecutionStepResult result = runStep(plan, step, logs);
            results.add(result);
            if (!result.passed()) {
                return new ExecutionResult(plan, List.copyOf(results), false, step.id(), result.message());
            }
        }
        return new ExecutionResult(plan, List.copyOf(results), true, null, "");
    }

    private ExecutionStepResult runStep(CommandPlan plan, CommandStep step, Path logs)
            throws IOException, InterruptedException {
        List<String> realCommand = toJavaMainCommand(step.command());
        Process process = new ProcessBuilder(realCommand)
                .directory(plan.sandbox().toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        byte[] stdoutBytes = process.getInputStream().readAllBytes();
        byte[] stderrBytes = process.getErrorStream().readAllBytes();
        Path stdout = logs.resolve(step.id() + ".stdout.txt");
        Path stderr = logs.resolve(step.id() + ".stderr.txt");
        Files.write(stdout, stdoutBytes);
        Files.write(stderr, stderrBytes);
        int exitCode = exited ? process.exitValue() : -1;
        String assertionError = exited ? assertionError(plan, step, exitCode) : "timeout after " + timeout;
        boolean passed = exited && assertionError == null;
        return new ExecutionStepResult(step.id(), step.command(), exitCode, stdout, stderr, passed,
                assertionError == null ? "" : assertionError);
    }

    private List<String> toJavaMainCommand(List<String> displayCommand) {
        if (displayCommand == null || displayCommand.isEmpty()) {
            throw new IllegalArgumentException("CommandPlan step has empty command");
        }
        int offset = "xml-gen".equals(displayCommand.get(0)) ? 1 : 0;
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(absoluteClasspath());
        command.add(Main.class.getName());
        command.addAll(displayCommand.subList(offset, displayCommand.size()));
        return command;
    }

    private String absoluteClasspath() {
        String separator = System.getProperty("path.separator");
        String[] entries = System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(separator));
        List<String> absolute = new ArrayList<>();
        Path userDir = Path.of(System.getProperty("user.dir"));
        for (String entry : entries) {
            Path path = Path.of(entry);
            absolute.add(path.isAbsolute() ? path.toString() : userDir.resolve(path).normalize().toString());
        }
        return String.join(separator, absolute);
    }

    private String assertionError(CommandPlan plan, CommandStep step, int exitCode) throws IOException {
        if (step.assertions() == null) {
            return null;
        }
        for (CommandAssertion assertion : step.assertions()) {
            if ("exitCode".equals(assertion.type())) {
                int expected = assertion.value() == null ? 0 : assertion.value();
                if (exitCode != expected) {
                    return "expected exitCode " + expected + ", got " + exitCode;
                }
            } else if ("exitCodes".equals(assertion.type())) {
                List<Integer> expected = assertion.values() == null ? List.of(0) : assertion.values();
                if (!expected.contains(exitCode)) {
                    return "expected exitCode one of " + expected + ", got " + exitCode;
                }
            } else if ("fileExists".equals(assertion.type())) {
                Path file = plan.sandbox().resolve(assertion.path()).normalize();
                if (!Files.exists(file)) {
                    return "expected file to exist: " + assertion.path();
                }
            } else {
                return "unknown assertion type: " + assertion.type();
            }
        }
        return null;
    }
}

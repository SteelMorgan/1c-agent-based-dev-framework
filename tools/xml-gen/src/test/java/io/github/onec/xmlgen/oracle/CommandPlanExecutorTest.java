package io.github.onec.xmlgen.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPlanExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void executesPublicXmlGenCommandAndChecksAssertions() throws Exception {
        CommandPlan plan = new CommandPlan(
                "test",
                "cli",
                tempDir,
                List.of(new CommandStep("init",
                        List.of("xml-gen", "epf", "init", "--name", "OracleHost", "out/"),
                        List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists("out/OracleHost.xml")))),
                "out/OracleHost.xml"
        );

        ExecutionResult result = new CommandPlanExecutor().execute(plan);

        assertThat(result.passed()).as(result.message()).isTrue();
        assertThat(tempDir.resolve("logs/init.stdout.txt")).exists();
        assertThat(tempDir.resolve("out/OracleHost.xml")).exists();
    }

    @Test
    void acceptsAnyConfiguredExitCode() throws Exception {
        CommandPlan plan = new CommandPlan(
                "test",
                "cli",
                tempDir,
                List.of(new CommandStep("help",
                        List.of("xml-gen", "--help"),
                        List.of(CommandAssertion.exitCodes(0, 2)))),
                ""
        );

        ExecutionResult result = new CommandPlanExecutor().execute(plan);

        assertThat(result.passed()).as(result.message()).isTrue();
    }
}

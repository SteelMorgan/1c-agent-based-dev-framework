package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-174 XG-32: проверки реального CLI-пути role compile.
 */
class RoleCompileTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void roleCompile_shorthandAndRussianAliases_succeeds() throws Exception {
        Path json = tempDir.resolve("role.json");
        Files.writeString(json, """
                {
                  "name": "РольCliXG32",
                  "objects": [
                    "Справочник.Контрагенты: @view",
                    {
                      "name": "Документ.ЗаказКлиента",
                      "rights": {"Чтение": true, "Просмотр": true}
                    }
                  ]
                }
                """);

        Commands.execute("role", new String[]{"compile", json.toString(), tempDir.toString()});

        String rights = Files.readString(tempDir.resolve("Roles/РольCliXG32/Ext/Rights.xml"));
        assertThat(rights)
                .contains("<name>Catalog.Контрагенты</name>")
                .contains("<name>Document.ЗаказКлиента</name>")
                .contains("<name>Read</name>")
                .contains("<name>View</name>");
    }

    @Test
    void roleCompile_invalidMapRight_failsFast() throws Exception {
        Path json = tempDir.resolve("bad-role.json");
        Files.writeString(json, """
                {
                  "name": "РольCliBadRight",
                  "objects": [
                    {
                      "name": "Catalog.Товары",
                      "rights": {"view": true}
                    }
                  ]
                }
                """);

        assertThatThrownBy(() ->
                Commands.execute("role", new String[]{"compile", json.toString(), tempDir.toString()}))
                .hasMessageContaining("Invalid right name 'view'");
        assertThat(tempDir.resolve("Roles/РольCliBadRight/Ext/Rights.xml")).doesNotExist();
    }
}

package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportCliContractTest {
    private static final String ROOT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String LOCKED_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String VENDOR_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BLOCK_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @TempDir
    Path tempDir;

    @Test
    void supportInfoDoesNotFailButSupportCheckFailsOnLockedObject() throws Exception {
        Path locked = createLockedCatalog();

        Commands.execute("support", new String[] {"info", locked.toString()});

        assertThatThrownBy(() -> Commands.execute("support", new String[] {"check", locked.toString()}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[support-guard]")
                .hasMessageContaining("Object UUID: " + LOCKED_UUID);
    }

    private Path createLockedCatalog() throws Exception {
        Path cfg = tempDir.resolve("cfg");
        Files.createDirectories(cfg.resolve("Ext"));
        Files.createDirectories(cfg.resolve("Catalogs"));
        Files.writeString(cfg.resolve("Configuration.xml"), """
                <MetaDataObject>
                  <Configuration uuid="%s"><Properties><Name>Cfg</Name></Properties></Configuration>
                </MetaDataObject>
                """.formatted(ROOT_UUID), StandardCharsets.UTF_8);
        Files.writeString(cfg.resolve("Ext").resolve("ParentConfigurations.bin"), """
                {6,0,1,%s,0,%s,"1.0","Vendor","Cfg",2,0,0,%s,%s,0,0,%s,%s}
                """.formatted(BLOCK_UUID, VENDOR_UUID, ROOT_UUID, ROOT_UUID, LOCKED_UUID, LOCKED_UUID),
                StandardCharsets.UTF_8);
        Path catalog = cfg.resolve("Catalogs").resolve("Locked.xml");
        Files.writeString(catalog, """
                <MetaDataObject><Catalog uuid="%s"/></MetaDataObject>
                """.formatted(LOCKED_UUID), StandardCharsets.UTF_8);
        return catalog;
    }
}

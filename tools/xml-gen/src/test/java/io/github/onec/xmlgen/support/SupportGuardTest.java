package io.github.onec.xmlgen.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportGuardTest {
    private static final String ROOT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String LOCKED_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String EDITABLE_UUID = "33333333-3333-3333-3333-333333333333";
    private static final String REMOVED_UUID = "44444444-4444-4444-4444-444444444444";
    private static final String VENDOR_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BLOCK_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @TempDir
    Path tempDir;

    @Test
    void blocksWholeConfigurationWhenCapabilityOff() throws Exception {
        Path catalog = createConfig(1, """
                <Catalog uuid="%s"><Properties><Name>Locked</Name></Properties></Catalog>
                """.formatted(LOCKED_UUID));

        SupportDecision decision = SupportGuard.check(catalog, SupportRequirement.EDITABLE);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.code()).isEqualTo(SupportBlockCode.CAPABILITY_OFF);
    }

    @Test
    void blocksLockedObjectButAllowsEditableAndRemovedObjects() throws Exception {
        Path locked = createConfig(0, """
                <Catalog uuid="%s"><Properties><Name>Locked</Name></Properties></Catalog>
                """.formatted(LOCKED_UUID));
        Path catalogs = locked.getParent();
        Path editable = catalogs.resolve("Editable.xml");
        Files.writeString(editable, """
                <MetaDataObject><Catalog uuid="%s"/></MetaDataObject>
                """.formatted(EDITABLE_UUID), StandardCharsets.UTF_8);
        Path removed = catalogs.resolve("Removed.xml");
        Files.writeString(removed, """
                <MetaDataObject><Catalog uuid="%s"/></MetaDataObject>
                """.formatted(REMOVED_UUID), StandardCharsets.UTF_8);

        assertThat(SupportGuard.check(locked, SupportRequirement.EDITABLE).blocked()).isTrue();
        assertThat(SupportGuard.check(editable, SupportRequirement.EDITABLE).blocked()).isFalse();
        assertThat(SupportGuard.check(removed, SupportRequirement.EDITABLE).blocked()).isFalse();
    }

    @Test
    void removeRequiresObjectToBeOffSupport() throws Exception {
        Path locked = createConfig(0, """
                <Catalog uuid="%s"><Properties><Name>Locked</Name></Properties></Catalog>
                """.formatted(LOCKED_UUID));
        Path removed = locked.getParent().resolve("Removed.xml");
        Files.writeString(removed, """
                <MetaDataObject><Catalog uuid="%s"/></MetaDataObject>
                """.formatted(REMOVED_UUID), StandardCharsets.UTF_8);

        assertThat(SupportGuard.check(locked, SupportRequirement.REMOVED).blocked()).isTrue();
        assertThat(SupportGuard.check(removed, SupportRequirement.REMOVED).blocked()).isFalse();
    }

    @Test
    void ignoresExtensionConfiguration() throws Exception {
        Path cfg = tempDir.resolve("cfe");
        Files.createDirectories(cfg.resolve("Ext"));
        Files.writeString(cfg.resolve("Configuration.xml"), """
                <MetaDataObject>
                  <Configuration uuid="%s">
                    <Properties><ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose></Properties>
                  </Configuration>
                </MetaDataObject>
                """.formatted(ROOT_UUID), StandardCharsets.UTF_8);
        Files.writeString(cfg.resolve("Ext").resolve("ParentConfigurations.bin"), parentBin(1), StandardCharsets.UTF_8);
        Path object = cfg.resolve("Catalogs").resolve("Locked.xml");
        Files.createDirectories(object.getParent());
        Files.writeString(object, """
                <MetaDataObject><Catalog uuid="%s"/></MetaDataObject>
                """.formatted(LOCKED_UUID), StandardCharsets.UTF_8);

        assertThat(SupportGuard.check(object, SupportRequirement.EDITABLE).blocked()).isFalse();
    }

    @Test
    void requireThrowsActionableDiagnostic() throws Exception {
        Path locked = createConfig(0, """
                <Catalog uuid="%s"><Properties><Name>Locked</Name></Properties></Catalog>
                """.formatted(LOCKED_UUID));

        assertThatThrownBy(() -> SupportGuard.require(locked, SupportRequirement.EDITABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[support-guard]")
                .hasMessageContaining("Object UUID: " + LOCKED_UUID);
    }

    private Path createConfig(int globalFlag, String catalogBody) throws Exception {
        Path cfg = tempDir.resolve("cfg-" + globalFlag);
        Files.createDirectories(cfg.resolve("Ext"));
        Files.createDirectories(cfg.resolve("Catalogs"));
        Files.writeString(cfg.resolve("Configuration.xml"), """
                <MetaDataObject>
                  <Configuration uuid="%s"><Properties><Name>Cfg</Name></Properties></Configuration>
                </MetaDataObject>
                """.formatted(ROOT_UUID), StandardCharsets.UTF_8);
        Files.writeString(cfg.resolve("Ext").resolve("ParentConfigurations.bin"),
                parentBin(globalFlag), StandardCharsets.UTF_8);
        Path catalog = cfg.resolve("Catalogs").resolve("Locked.xml");
        Files.writeString(catalog, "<MetaDataObject>" + catalogBody + "</MetaDataObject>",
                StandardCharsets.UTF_8);
        return catalog;
    }

    private String parentBin(int globalFlag) {
        return "{6,%d,1,%s,%d,%s,\"1.0\",\"Vendor\",\"Cfg\",4,"
                .formatted(globalFlag, BLOCK_UUID, globalFlag, VENDOR_UUID)
                + record(0, ROOT_UUID)
                + record(0, LOCKED_UUID)
                + record(1, EDITABLE_UUID)
                + record(2, REMOVED_UUID).replaceFirst(",$", "")
                + "}";
    }

    private String record(int rule, String uuid) {
        return "%d,0,%s,%s,".formatted(rule, uuid, uuid);
    }
}

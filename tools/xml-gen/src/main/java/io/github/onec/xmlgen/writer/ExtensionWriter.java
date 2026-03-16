package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Генератор scaffold расширения конфигурации 1С (CFE).
 *
 * Создаёт минимальную структуру:
 * - Configuration.xml (extension-specific properties)
 * - Languages/Русский.xml (заимствованный язык)
 * - Roles/<prefix>ОсновнаяРоль.xml (опционально)
 */
public class ExtensionWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 7 фиксированных ClassId для InternalInfo расширения */
    private static final String[] CLASS_IDS = {
            "9cd510cd-abfc-11d4-9434-004095e12fc7",
            "9fcd25a0-4822-11d4-9414-008048da11f9",
            "e3687481-0a87-462c-a166-9f34594f9bba",
            "9de14907-ec23-4a07-96f0-85521cb6b53b",
            "51f2d5d8-ea4d-4064-8892-82951750031e",
            "e68182ea-4237-4383-967f-90c1e3370bc7",
            "fb282519-d103-4dd3-bc12-cb271d631dfc",
    };

    private static final String XMLNS = "xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
            + "xmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\" "
            + "xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\" "
            + "xmlns:cmi=\"http://v8.1c.ru/8.2/managed-application/cmi\" "
            + "xmlns:ent=\"http://v8.1c.ru/8.1/data/enterprise\" "
            + "xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\" "
            + "xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\" "
            + "xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
            + "xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\" "
            + "xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\" "
            + "xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\" "
            + "xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\" "
            + "xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\" "
            + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" "
            + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

    private final PrintStream out;

    public ExtensionWriter() { this(System.out); }
    public ExtensionWriter(PrintStream out) { this.out = out; }

    /**
     * Создать scaffold расширения.
     *
     * @param outputDir   каталог для размещения файлов
     * @param name        имя расширения
     * @param synonym     синоним (null = name)
     * @param prefix      префикс имён (null = name + "_")
     * @param purpose     назначение: Patch, Customization, AddOn (null = Customization)
     * @param compat      режим совместимости (null = Version8_3_24)
     * @param version     версия (null = "")
     * @param vendor      поставщик (null = "")
     * @param configPath  путь к Configuration.xml основной конфигурации (null = без авто-resolve)
     * @param noRole      не создавать роль
     */
    public void create(Path outputDir, String name, String synonym, String prefix,
                       String purpose, String compat, String version, String vendor,
                       Path configPath, boolean noRole) throws IOException {
        // Safety check — do not overwrite existing extension
        Path existingCfg = outputDir.resolve("Configuration.xml");
        if (Files.isRegularFile(existingCfg)) {
            throw new IllegalArgumentException("Configuration.xml already exists in " + outputDir
                    + ". Remove it first or use a different directory.");
        }

        Files.createDirectories(outputDir);

        String syn = synonym != null ? synonym : name;
        String pfx = prefix != null ? prefix : (name + "_");
        String purp = purpose != null ? purpose : "Customization";
        String cmp = compat != null ? compat : "Version8_3_24";
        String ver = version != null ? version : "";
        String vnd = vendor != null ? vendor : "";
        String baseLangUuid = "00000000-0000-0000-0000-000000000000";

        // Auto-resolve from base config
        if (configPath != null) {
            Path cfgFile = resolveConfigPath(configPath);
            if (cfgFile != null) {
                String resolvedCompat = readCompatibilityMode(cfgFile);
                if (resolvedCompat != null) {
                    cmp = resolvedCompat;
                    out.println("[INFO] Base config CompatibilityMode: " + cmp);
                }
                String resolvedLangUuid = readLanguageUuid(cfgFile.getParent());
                if (resolvedLangUuid != null) {
                    baseLangUuid = resolvedLangUuid;
                    out.println("[INFO] Base config Language UUID: " + baseLangUuid);
                }
            }
        } else {
            out.println("[WARN] Language ExtendedConfigurationObject set to zeros. "
                    + "Use --config-path to auto-resolve from base config.");
        }

        String roleName = pfx + "ОсновнаяРоль";

        // 1. Configuration.xml
        writeConfigurationXml(outputDir, name, syn, pfx, purp, cmp, ver, vnd,
                baseLangUuid, roleName, noRole);

        // 2. Languages/Русский.xml
        writeLanguageXml(outputDir, baseLangUuid);

        // 3. Role (optional)
        if (!noRole) {
            writeRoleXml(outputDir, roleName);
        }

        // Summary
        out.println("[OK] Extension created: " + name);
        out.println("     Directory:       " + outputDir);
        out.println("     Purpose:         " + purp);
        out.println("     Prefix:          " + pfx);
        out.println("     Compatibility:   " + cmp);
    }

    // ─── Configuration.xml ──────────────────────────────────────────────

    private void writeConfigurationXml(Path outputDir, String name, String synonym,
                                       String prefix, String purpose, String compat,
                                       String version, String vendor, String baseLangUuid,
                                       String roleName, boolean noRole) throws IOException {
        String cfgUuid = UuidGenerator.generate();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"2.17\">\n");
        sb.append("\t<Configuration uuid=\"").append(cfgUuid).append("\">\n");

        // InternalInfo — 7 ContainedObjects
        sb.append("\t\t<InternalInfo>\n");
        for (String classId : CLASS_IDS) {
            sb.append("\t\t\t<xr:ContainedObject>\n");
            sb.append("\t\t\t\t<xr:ClassId>").append(classId).append("</xr:ClassId>\n");
            sb.append("\t\t\t\t<xr:ObjectId>").append(UuidGenerator.generate()).append("</xr:ObjectId>\n");
            sb.append("\t\t\t</xr:ContainedObject>\n");
        }
        sb.append("\t\t</InternalInfo>\n");

        // Properties — extension-specific order
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n");
        sb.append("\t\t\t<Name>").append(esc(name)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(esc(synonym)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<ConfigurationExtensionPurpose>").append(purpose).append("</ConfigurationExtensionPurpose>\n");
        sb.append("\t\t\t<KeepMappingToExtendedConfigurationObjectsByIDs>true</KeepMappingToExtendedConfigurationObjectsByIDs>\n");
        sb.append("\t\t\t<NamePrefix>").append(esc(prefix)).append("</NamePrefix>\n");
        sb.append("\t\t\t<ConfigurationExtensionCompatibilityMode>").append(compat).append("</ConfigurationExtensionCompatibilityMode>\n");
        sb.append("\t\t\t<DefaultRunMode>ManagedApplication</DefaultRunMode>\n");
        sb.append("\t\t\t<UsePurposes>\n");
        sb.append("\t\t\t\t<v8:Value xsi:type=\"app:ApplicationUsePurpose\">PlatformApplication</v8:Value>\n");
        sb.append("\t\t\t</UsePurposes>\n");
        sb.append("\t\t\t<ScriptVariant>Russian</ScriptVariant>\n");

        // DefaultRoles
        if (!noRole) {
            sb.append("\t\t\t<DefaultRoles>\n");
            sb.append("\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">Role.").append(esc(roleName)).append("</xr:Item>\n");
            sb.append("\t\t\t</DefaultRoles>\n");
        } else {
            sb.append("\t\t\t<DefaultRoles/>\n");
        }

        sb.append("\t\t\t<Vendor>").append(esc(vendor)).append("</Vendor>\n");
        sb.append("\t\t\t<Version>").append(esc(version)).append("</Version>\n");
        sb.append("\t\t\t<DefaultLanguage>Language.Русский</DefaultLanguage>\n");
        sb.append("\t\t\t<BriefInformation/>\n");
        sb.append("\t\t\t<DetailedInformation/>\n");
        sb.append("\t\t\t<Copyright/>\n");
        sb.append("\t\t\t<VendorInformationAddress/>\n");
        sb.append("\t\t\t<ConfigurationInformationAddress/>\n");
        sb.append("\t\t\t<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>\n");
        sb.append("\t\t</Properties>\n");

        // ChildObjects
        sb.append("\t\t<ChildObjects>\n");
        sb.append("\t\t\t<Language>Русский</Language>\n");
        if (!noRole) {
            sb.append("\t\t\t<Role>").append(esc(roleName)).append("</Role>\n");
        }
        sb.append("\t\t</ChildObjects>\n");

        sb.append("\t</Configuration>\n");
        sb.append("</MetaDataObject>");

        writeWithBom(outputDir.resolve("Configuration.xml"), sb.toString());
    }

    // ─── Languages/Русский.xml ──────────────────────────────────────────

    private void writeLanguageXml(Path outputDir, String baseLangUuid) throws IOException {
        Path langDir = outputDir.resolve("Languages");
        Files.createDirectories(langDir);

        String langUuid = UuidGenerator.generate();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"2.17\">\n");
        sb.append("\t<Language uuid=\"").append(langUuid).append("\">\n");
        sb.append("\t\t<InternalInfo/>\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n");
        sb.append("\t\t\t<Name>Русский</Name>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<ExtendedConfigurationObject>").append(baseLangUuid).append("</ExtendedConfigurationObject>\n");
        sb.append("\t\t\t<LanguageCode>ru</LanguageCode>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Language>\n");
        sb.append("</MetaDataObject>");

        writeWithBom(langDir.resolve("Русский.xml"), sb.toString());
    }

    // ─── Role ───────────────────────────────────────────────────────────

    private void writeRoleXml(Path outputDir, String roleName) throws IOException {
        Path roleDir = outputDir.resolve("Roles");
        Files.createDirectories(roleDir);

        String roleUuid = UuidGenerator.generate();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"2.17\">\n");
        sb.append("\t<Role uuid=\"").append(roleUuid).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(esc(roleName)).append("</Name>\n");
        sb.append("\t\t\t<Synonym/>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Role>\n");
        sb.append("</MetaDataObject>");

        writeWithBom(roleDir.resolve(roleName + ".xml"), sb.toString());
    }

    // ─── Config path resolution ─────────────────────────────────────────

    private Path resolveConfigPath(Path configPath) {
        if (Files.isDirectory(configPath)) {
            Path candidate = configPath.resolve("Configuration.xml");
            if (Files.isRegularFile(candidate)) return candidate;
            out.println("[WARN] No Configuration.xml in: " + configPath);
            return null;
        }
        if (Files.isRegularFile(configPath)) return configPath;
        out.println("[WARN] Config file not found: " + configPath);
        return null;
    }

    private String readCompatibilityMode(Path cfgFile) {
        try {
            String content = Files.readString(cfgFile, StandardCharsets.UTF_8);
            // Simple regex extraction — avoid full XML parsing
            var m = java.util.regex.Pattern.compile("<CompatibilityMode>(\\w+)</CompatibilityMode>")
                    .matcher(content);
            if (m.find()) return m.group(1);
        } catch (IOException e) {
            out.println("[WARN] Could not read base config: " + e.getMessage());
        }
        return null;
    }

    private String readLanguageUuid(Path configDir) {
        Path langFile = configDir.resolve("Languages").resolve("Русский.xml");
        if (!Files.isRegularFile(langFile)) {
            out.println("[WARN] Base config language not found: " + langFile);
            return null;
        }
        try {
            String content = Files.readString(langFile, StandardCharsets.UTF_8);
            var m = java.util.regex.Pattern.compile("<Language\\s+uuid=\"([^\"]+)\"")
                    .matcher(content);
            if (m.find()) return m.group(1);
        } catch (IOException e) {
            out.println("[WARN] Could not read base language: " + e.getMessage());
        }
        return null;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private void writeWithBom(Path path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + bytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(bytes, 0, result, BOM.length, bytes.length);
        Files.write(path, result);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

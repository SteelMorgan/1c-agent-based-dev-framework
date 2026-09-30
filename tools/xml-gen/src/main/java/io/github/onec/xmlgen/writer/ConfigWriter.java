package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Генератор scaffold конфигурации 1С (Configuration.xml, ConfigDumpInfo.xml, Languages/).
 */
public class ConfigWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // 7 канонических ClassId для InternalInfo конфигурации.
    // TASK-171 (D-1): позиции 2,5,6,7 раньше были неверны → config init генерил битый InternalInfo
    // (cf-validate ловил 4 Unknown ClassId). Канон сверен с грунт-труф (grep ClassId
    // src/xml/Configuration.xml даёт ровно эти 7 GUID), с эталоном Николая (cf-init.py / cf-validate.py
    // VALID_CLASS_IDS) и с ExtensionWriter.CLASS_IDS (там те же 7 GUID были уже правильными).
    private static final String[] INTERNAL_CLASS_IDS = {
            "9cd510cd-abfc-11d4-9434-004095e12fc7",
            "9fcd25a0-4822-11d4-9414-008048da11f9",
            "e3687481-0a87-462c-a166-9f34594f9bba",
            "9de14907-ec23-4a07-96f0-85521cb6b53b",
            "51f2d5d8-ea4d-4064-8892-82951750031e",
            "e68182ea-4237-4383-967f-90c1e3370bc7",
            "fb282519-d103-4dd3-bc12-cb271d631dfc"
    };

    /**
     * Создать scaffold конфигурации.
     *
     * @param outputDir каталог для размещения файлов
     * @param name      имя конфигурации
     * @param synonym   синоним (null = name)
     * @param version   версия (null = "1.0.0.1")
     * @param vendor    поставщик (null = "")
     * @param langName  имя языка (null = "Русский")
     * @param langCode  код языка (null = "ru")
     */
    public void create(Path outputDir, String name, String synonym, String version,
                       String vendor, String langName, String langCode) throws IOException {
        create(outputDir, name, synonym, version, vendor, langName, langCode, null, null);
    }

    /**
     * Создать scaffold конфигурации с указанием версии формата и режима совместимости
     * (TASK-171 D-10).
     *
     * @param compatibilityMode значение {@code CompatibilityMode}/{@code ConfigurationExtensionCompatibilityMode}
     *                          (например {@code Version8_3_27}); null = {@code Version8_3_24}
     * @param formatVersion     версия формата сериализации (атрибут {@code version} корня); null = {@code 2.17}
     */
    public void create(Path outputDir, String name, String synonym, String version,
                       String vendor, String langName, String langCode,
                       String compatibilityMode, String formatVersion) throws IOException {
        Files.createDirectories(outputDir);

        String syn = synonym != null ? synonym : name;
        String ver = version != null ? version : "1.0.0.1";
        String vnd = vendor != null ? vendor : "";
        String lName = langName != null ? langName : "Русский";
        String lCode = langCode != null ? langCode : "ru";
        String compat = compatibilityMode != null ? compatibilityMode : "Version8_3_24";
        String fmtVer = formatVersion != null ? formatVersion : "2.17";

        String configUuid = UuidGenerator.generate();
        String langUuid = UuidGenerator.generate();

        // 1. Configuration.xml
        writeConfigurationXml(outputDir, name, syn, ver, vnd, lName, lCode, configUuid, compat, fmtVer);

        // 2. ConfigDumpInfo.xml
        writeConfigDumpInfo(outputDir, name, configUuid, lName, langUuid, fmtVer);

        // 3. Languages/<langName>.xml
        writeLanguage(outputDir, lName, lCode, langUuid, fmtVer);

        // 4. Ext/ modules (empty stubs)
        Path extDir = outputDir.resolve("Ext");
        Files.createDirectories(extDir);
        writeWithBom(extDir.resolve("ManagedApplicationModule.bsl"), "");
        writeWithBom(extDir.resolve("SessionModule.bsl"), "");
    }

    private void writeConfigurationXml(Path outputDir, String name, String synonym,
                                       String version, String vendor, String langName,
                                       String langCode, String configUuid,
                                       String compatibilityMode, String formatVersion) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\txmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\"\n");
        // TASK-171 D-10: версия формата конфигурируема (раньше хардкод 2.17).
        sb.append("\tversion=\"").append(escapeXml(formatVersion)).append("\">\n");
        sb.append("\t<Configuration uuid=\"").append(configUuid).append("\">\n");

        // InternalInfo
        sb.append("\t\t<InternalInfo>\n");
        for (String classId : INTERNAL_CLASS_IDS) {
            sb.append("\t\t\t<xr:ContainedObject>\n");
            sb.append("\t\t\t\t<xr:ClassId>").append(classId).append("</xr:ClassId>\n");
            sb.append("\t\t\t\t<xr:ObjectId>").append(UuidGenerator.generate()).append("</xr:ObjectId>\n");
            sb.append("\t\t\t</xr:ContainedObject>\n");
        }
        sb.append("\t\t</InternalInfo>\n");

        // Properties
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(escapeXml(name)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>").append(escapeXml(langCode)).append("</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(synonym)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<NamePrefix/>\n");
        sb.append("\t\t\t<Vendor>").append(escapeXml(vendor)).append("</Vendor>\n");
        sb.append("\t\t\t<Version>").append(escapeXml(version)).append("</Version>\n");
        sb.append("\t\t\t<UpdateCatalogAddress/>\n");
        sb.append("\t\t\t<BriefInformation/>\n");
        sb.append("\t\t\t<DetailedInformation/>\n");
        sb.append("\t\t\t<Copyright/>\n");
        sb.append("\t\t\t<VendorInformationAddress/>\n");
        sb.append("\t\t\t<ConfigurationInformationAddress/>\n");
        // TASK-171 D-10: режим совместимости конфигурируем (раньше хардкод Version8_3_24).
        sb.append("\t\t\t<ConfigurationExtensionCompatibilityMode>").append(escapeXml(compatibilityMode))
                .append("</ConfigurationExtensionCompatibilityMode>\n");
        sb.append("\t\t\t<DefaultRunMode>ManagedApplication</DefaultRunMode>\n");
        sb.append("\t\t\t<ScriptVariant>Russian</ScriptVariant>\n");
        sb.append("\t\t\t<CompatibilityMode>").append(escapeXml(compatibilityMode)).append("</CompatibilityMode>\n");
        sb.append("\t\t\t<DataLockControlMode>Managed</DataLockControlMode>\n");
        sb.append("\t\t\t<ObjectAutonumerationMode>NotAutoFree</ObjectAutonumerationMode>\n");
        sb.append("\t\t\t<ModalityUseMode>DontUse</ModalityUseMode>\n");
        sb.append("\t\t\t<SynchronousPlatformExtensionAndAddInCallUseMode>DontUse</SynchronousPlatformExtensionAndAddInCallUseMode>\n");
        //**agent TASK-175 [07.06.2026 18:55:00]
        // XG-36 (72bad1aa cf-init v1.1): Taxi → TaxiEnableVersion8_2 — соответствует всем
        // реальным конфигурациям (acc 8.3.20/24/27, erp 8.3.24); голый Taxi не встречается.
        //sb.append("\t\t\t<InterfaceCompatibilityMode>Taxi</InterfaceCompatibilityMode>\n");
        sb.append("\t\t\t<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>\n");
        //**agent TASK-175
        sb.append("\t\t\t<DatabaseTablespacesUseMode>DontUse</DatabaseTablespacesUseMode>\n");
        sb.append("\t\t\t<MainClientApplicationWindowMode>Normal</MainClientApplicationWindowMode>\n");
        sb.append("\t\t\t<UsePurposes>\n");
        sb.append("\t\t\t\t<v8:Value xsi:type=\"app:ApplicationUsePurpose\">PlatformApplication</v8:Value>\n");
        sb.append("\t\t\t</UsePurposes>\n");
        sb.append("\t\t\t<DefaultRoles/>\n");
        sb.append("\t\t\t<DefaultLanguage>Language.").append(escapeXml(langName)).append("</DefaultLanguage>\n");
        sb.append("\t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>\n");
        sb.append("\t\t\t<UseManagedFormInOrdinaryApplication>false</UseManagedFormInOrdinaryApplication>\n");
        sb.append("\t\t\t<UseOrdinaryFormInManagedApplication>false</UseOrdinaryFormInManagedApplication>\n");
        sb.append("\t\t\t<Content/>\n");
        sb.append("\t\t\t<StandaloneConfigurationRestrictionRoles/>\n");
        sb.append("\t\t\t<CommonSettingsStorage/>\n");
        sb.append("\t\t\t<ReportsUserSettingsStorage/>\n");
        sb.append("\t\t\t<ReportsVariantsStorage/>\n");
        sb.append("\t\t\t<FormDataSettingsStorage/>\n");
        sb.append("\t\t\t<DynamicListsUserSettingsStorage/>\n");
        sb.append("\t\t\t<DefaultReportForm/>\n");
        sb.append("\t\t\t<DefaultReportVariantForm/>\n");
        sb.append("\t\t\t<DefaultReportSettingsForm/>\n");
        sb.append("\t\t\t<DefaultReportAppearanceTemplate/>\n");
        sb.append("\t\t\t<DefaultDynamicListSettingsForm/>\n");
        sb.append("\t\t\t<DefaultSearchForm/>\n");
        sb.append("\t\t\t<DefaultDataHistoryChangeHistoryForm/>\n");
        sb.append("\t\t\t<DefaultDataHistoryVersionDataForm/>\n");
        sb.append("\t\t\t<DefaultDataHistoryVersionDifferencesForm/>\n");
        sb.append("\t\t\t<DefaultCollaborationSystemUsersChoiceForm/>\n");
        sb.append("\t\t\t<DefaultConstantsForm/>\n");
        sb.append("\t\t\t<AdditionalFullTextSearchDictionaries/>\n");
        sb.append("\t\t\t<RequiredMobileApplicationPermissions/>\n");
        sb.append("\t\t\t<UsedMobileApplicationFunctionalities/>\n");
        sb.append("\t\t</Properties>\n");

        // ChildObjects
        sb.append("\t\t<ChildObjects>\n");
        sb.append("\t\t\t<Language>").append(escapeXml(langName)).append("</Language>\n");
        sb.append("\t\t</ChildObjects>\n");

        sb.append("\t</Configuration>\n");
        sb.append("</MetaDataObject>\n");

        writeWithBom(outputDir.resolve("Configuration.xml"), sb.toString());
    }

    private void writeConfigDumpInfo(Path outputDir, String configName, String configUuid,
                                     String langName, String langUuid,
                                     String formatVersion) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ConfigDumpInfo xmlns=\"http://v8.1c.ru/8.3/xcf/dumpinfo\"\n");
        sb.append("\txmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tformat=\"Hierarchical\" version=\"").append(escapeXml(formatVersion)).append("\">\n");
        sb.append("\t<ConfigVersions>\n");
        sb.append("\t\t<Metadata name=\"Configuration.").append(escapeXml(configName))
                .append("\" id=\"").append(configUuid).append("\" configVersion=\"00000000000000000000000000000000 00000000\"/>\n");
        sb.append("\t\t<Metadata name=\"Language.").append(escapeXml(langName))
                .append("\" id=\"").append(langUuid).append("\" configVersion=\"00000000000000000000000000000000 00000000\"/>\n");
        sb.append("\t</ConfigVersions>\n");
        sb.append("</ConfigDumpInfo>\n");

        writeWithBom(outputDir.resolve("ConfigDumpInfo.xml"), sb.toString());
    }

    private void writeLanguage(Path outputDir, String langName, String langCode,
                               String langUuid, String formatVersion) throws IOException {
        Path langDir = outputDir.resolve("Languages");
        Files.createDirectories(langDir);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"").append(escapeXml(formatVersion)).append("\">\n");
        sb.append("\t<Language uuid=\"").append(langUuid).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(escapeXml(langName)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>").append(escapeXml(langCode)).append("</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(langName)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<LanguageCode>").append(escapeXml(langCode)).append("</LanguageCode>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Language>\n");
        sb.append("</MetaDataObject>\n");

        writeWithBom(langDir.resolve(langName + ".xml"), sb.toString());
    }

    private static void writeWithBom(Path path, String content) throws IOException {
        //++agent TASK-172 [02.06.2026 07:15:00]
        // Канон Designer (_Демо): BOM + CRLF через единый чокпоинт нормализации.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

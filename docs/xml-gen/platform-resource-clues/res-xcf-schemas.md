# Safe platform resource clues for XCF/XDTO schemas

Source platform: `/opt/1cv8/current` -> `/opt/1cv8/x86_64/8.3.27.2074`.

Scope: read-only inspection of installed 1C platform resource and shared-object files. No project XML was opened for editing, no platform files were modified, no decompilation was performed. Evidence below is limited to short identifiers from `strings`, ELF metadata, and ZIP directory listings.

## Commands used

Representative commands:

```bash
find /opt/1cv8/current -type f \( -name 'backend_root.res' -o -name 'mngcore_root.res' -o -name 'fmtd_root.res' -o -name 'config_root.res' -o -name 'chart_root.res' -o -name 'bp_root.res' -o -name 'accnt_root.res' -o -name '*.so' \) -print
find /opt/1cv8/current -maxdepth 0 -printf '%p type=%y -> %l\n'
find /opt/1cv8/x86_64/8.3.27.2074 -maxdepth 1 -type f \( -name '*root.res' -o -name '*xdto*' -o -name '*xml*' -o -name '*config*' \) -print
readelf -h /opt/1cv8/x86_64/8.3.27.2074/{backend,mngcore,fmtd,config,chart,bp,accnt,xdto,xml2,dcs,plnnr,moxel}.so
readelf -d /opt/1cv8/x86_64/8.3.27.2074/{backend,mngcore,fmtd,config,chart,bp,accnt,xdto,xml2,dcs,plnnr,moxel}.so
objdump -T /opt/1cv8/x86_64/8.3.27.2074/{backend,mngcore,fmtd,config,chart,bp,accnt,xdto,xml2,dcs,plnnr,moxel}.so
strings -a -n 3 /opt/1cv8/x86_64/8.3.27.2074/<file> | <filter for short XCF/XDTO/XSD/XML identifiers>
unzip -l /opt/1cv8/x86_64/8.3.27.2074/<root>.res
```

Filtering used only read-only shell text filters (`grep`, `sort`, `head`) to keep snippets short and identifier-only.

## Files inspected

Primary requested roots:

| File | Size | Notes |
|---|---:|---|
| `backend_root.res` | 21,684,016 | XCF errors/resources, XCF schemas, appended ZIP payload |
| `mngcore_root.res` | 5,748,432 | managed app/logform schemas, many UI ZIP resources |
| `fmtd_root.res` | 54,564 | formatted-document XDTO/XSD resources |
| `config_root.res` | 4,588,436 | configurator XCF dump/import resources, mobile schemas, appended ZIP payload |
| `chart_root.res` | 199,076 | chart XDTO/XSD resources |
| `bp_root.res` | 168,336 | mostly UI ZIP resources for business process/task icons |
| `accnt_root.res` | 74,016 | mostly UI ZIP resources for accounting/register UI |

Related schema/serializer roots:

| File | Size | Notes |
|---|---:|---|
| `xdto_root.res` | 144,808 | base XDTO model/schema resources |
| `xml2_root.res` | 339,580 | XML Schema resource names |
| `dcs_root.res` | 749,948 | data composition system schemas and appearance templates |
| `plnnr_root.res` | 32,900 | planner schema/resources |
| `moxel_root.res` | 905,324 | spreadsheet document schema/resources |

Related shared objects:

`backend.so`, `mngcore.so`, `fmtd.so`, `config.so`, `chart.so`, `bp.so`, `accnt.so`, `xdto.so`, `xml2.so`, `dcs.so`, `plnnr.so`, `moxel.so`.

All inspected `.so` files are ELF64 shared objects. `readelf -d` shows the common dependency base around `core83.so` and `nuke83.so`; `xml2.so` additionally exposes XML/libxml/xmlsec-related dependency strings.

## Embedded resource names

Schema-bearing resource names found by `strings`:

| Root | Short resource identifiers |
|---|---|
| `backend_root.res` | `model.xdto`, `core.xsd`, `misc.xsd`, `role.xsd`, `xcf_dump_info.xsd`, `xcf_enums.xsd`, `xcf_readable.xsd`, `TestAndRepairExchangePlanIntegrityReport.xsd`, `ConfigDumpInfo.xml` |
| `config_root.res` | `model.xdto`, `mobileApp.xsd`, `mobileForm.xsd`, `MobileForm.xsd`, `xcf_dump_info.xsd`, `xcf_enums.xsd`, `xcf_extprops.xsd`, `xcf_readable.xsd`, plus imports such as `core.xsd`, `bsl.xsd`, `chart.xsd`, `cmi.xsd`, `logform.xsd`, `mngapp.xsd`, `plnnr.xsd`, `role.xsd`, `scheme.xsd`, `settings.xsd`, `spreadsheetDocument.xsd`, `uiobjects.xsd` |
| `mngcore_root.res` | `model.xdto`, `mngapp.xsd`, `mngsrvws.xsd`, `modules.xsd`, `logform.xsd`, `logformlay.xsd`, `logformhl.xsd`, `dlist.xsd`, `dlistdata.xsd`, `cmi.xsd`, `bsl.xsd`, `chart.xsd`, `details.xsd`, `settings.xsd`, `core.xsd`, `uiobjects.xsd` |
| `fmtd_root.res` | `model.xdto`, `fmtd.xsd`, `fd.xsd`, `core.xsd`, `uiobjects.xsd` |
| `chart_root.res` | `model.xdto`, `chart.xsd`, `core.xsd`, `uiobjects.xsd` |
| `xdto_root.res` | `model.xdto`, `XSDModel.xdto`, `XDTOSchema.xsd`, `core.xsd`, `uiobjects.xsd`, `bsl.xsd` |
| `xml2_root.res` | `XMLSchema.xsd`, `XMLSchema-instance.xsd`, `MagicXMLSchema.xsd`, `datatypes.xsd`, `structures.xsd`, `xml.xsd` |
| `dcs_root.res` | `model.xdto`, `scheme.xsd`, `settings.xsd`, `core.xsd`, `details.xsd`, `result.xsd`, `appearanceTemplate.xsd`, `areaTemplate.xsd`, `common.xsd`, `compositionTemplate.xsd`, `spreadsheetDocument.xsd`, `uiobjects.xsd`; appearance XML names include `main.xml`, `mainNew.xml`, `mainNew833.xml`, `mainNew8310.xml`, `none.xml`, `antique.xml`, `arctic.xml`, `gaudy.xml`, `green.xml`, `sea.xml` |
| `plnnr_root.res` | `model.xdto`, `plnnr.xsd`, `chart.xsd`, `core.xsd`, `uiobjects.xsd` |
| `moxel_root.res` | `model.xdto`, `spreadsheetDocument.xsd`, `presetTableStyles.xml`, `core.xsd`, `uiobjects.xsd` |
| `bp_root.res` | `BusinessProcess.zip`, `Task.zip`, `Images.zip`, `schemedocicon.zip`, `manifest.xml`; no schema names found in the root resource string pass |
| `accnt_root.res` | `AccntRegForm.zip`, `TbAccntUIMD.zip`, `folder.zip`, `manifest.xml`; no schema names found in the root resource string pass |

`unzip -l` observations:

| Root | ZIP listing result |
|---|---|
| `backend_root.res` | ZIP directory found after extra bytes; visible entries are image resources and `manifest.xml` |
| `mngcore_root.res` | ZIP directory found after extra bytes; visible entries are user image resources and `manifest.xml` |
| `config_root.res` | ZIP directory found after extra bytes; visible entries are `setup.app/...` files from `setup.zip` |
| `bp_root.res` | ZIP directory found after extra bytes; visible entries are UI images and `manifest.xml` |
| `accnt_root.res` | ZIP directory found after extra bytes; visible entries are UI images and `manifest.xml` |
| `fmtd_root.res`, `chart_root.res`, `xdto_root.res`, `xml2_root.res`, `dcs_root.res`, `plnnr_root.res` | not ZIP archives according to `unzip -l` |

## Namespaces and schema identifiers

Short namespace identifiers found in resources:

| Area | Namespace identifiers |
|---|---|
| XCF/backend | `http://v8.1c.ru/8.3/xcf/dumpinfo`, `http://v8.1c.ru/8.3/xcf/enums`, `http://v8.1c.ru/8.3/xcf/extrnprops`, `http://v8.1c.ru/8.3/xcf/predef`, `http://v8.1c.ru/8.3/xcf/readable`, `http://v8.1c.ru/8.3/MDClasses` |
| Core/UI/XDTO | `http://v8.1c.ru/8.1/xdto`, `http://v8.1c.ru/8.1/data/core`, `http://v8.1c.ru/8.1/data/ui`, `http://v8.1c.ru/8.1/data/enterprise` |
| Managed app | `http://v8.1c.ru/8.2/managed-application/core`, `.../logform`, `.../cmi`, `.../dynamic-list`, `.../dynamic-list-data`, `.../modules`, `.../user-settings` |
| Formatted document | `http://v8.1c.ru/8.2/data/formatted-document`, `http://v8.1c.ru/8.2/data/formatted-document-internal` |
| Chart/planner/spreadsheet | `http://v8.1c.ru/8.2/data/chart`, `http://v8.1c.ru/8.3/data/planner`, `http://v8.1c.ru/8.2/data/spreadsheet` |
| DCS | `http://v8.1c.ru/8.1/data-composition-system/schema`, `.../settings`, `.../core`, `.../details`, `.../result`, `.../appearance-template`, `.../area-template`, `.../common`, `.../composition-template` |
| Mobile/config | `http://v8.1c.ru/8.3/mobile-application/app`, `http://v8.1c.ru/8.3/mobile-application/form`, `http://v8.1c.ru/8.2/roles`, `http://v8.1c.ru/8.2/data/bsl` |

## Serializer and validation identifiers

Short identifiers observed in `.so` strings/symbol tables:

| Module | Relevant identifiers |
|---|---|
| `backend.so` | `XCFSerializer`, `XCFMDObjSerializer`, `XCFSerializerFactory`, `XCFMDPropSerializer`, `XCFFormatVersion`, `XCFPathPlain`, `XCFPathHierarchical`, `XCFDumpInfoReader`, `XCFDumpInfoWriter`, `XMLConfigService`, `MDModelXDTOSystem`, `MDObjectXMLSerializeHelper`, `XDTOEnumTypeMapping_XCFFormat`, `XDTOEnumTypeMapping_XCFFormatVersion`, `DCSXCFSerializer`, `SheetXCFSerializer`, `TemplateXCFSerializer`, `RoleRightXCFSerializer`, `PictureXCFSerializer`, `DefaultExtXCFSerializer` |
| `config.so` | `ExportConfigToXMLDlg`, `ImportConfigFromXMLDlg`, `XCFObjectsListDlg`, `XCFMDObjsWithLongNamesDlg`, `XCF_FORMAT_PLAIN`, `XCF_FORMAT_HIERARCHICAL`, `XCF_ERR_XML_SOURCE_IS_NOT_ARCHIVE`, `saveContainerToXml`, `exportSelectedToXML`, `exportAllExtensionsInArchive` |
| `xdto.so` | `XDTOService`, `XDTODefaultSystem`, `XDTOFactoryBuilder`, `XDTOValidationException`, `XDTOReaderException`, `XDTOWriterException`, `XDTOPathException`, `XDTOException` |
| `xml2.so` | `XMLSchemaException`, `XMLReaderException`, `XMLWriterException`, `ConversionToCanonicalXMLException`, `http://www.w3.org/2001/XMLSchema`, `XMLSCHEMA_ERR_VALIDATION` |
| `fmtd.so` | `FormattedDocumentXDTOSerializer`, `FormattedDocumentInternalXDTOSerializer`, `FormattedDocumentBkmkXDTOSerializer`, `FormattedDocumentRangeXDTOSerializer`, `XDTOEnumTypeMapping_ParagraphType`, `XDTOEnumTypeMapping_HorizontalAlignment` |
| `chart.so` | `XDTOChartAxisSerializable`, `XDTOChartScaleSerializable`, `XDTOChartLabelAreaSerializable`, `XDTOChartColorPaletteDescriptionSerializable`, `XDTOTrendLineSerializable`, `XDTOReferenceLineSerializable`, `XDTOReferenceBandSerializable`, `ChartSerializable`, `GanttChartSerializable`, `PivotChartSerializable`, `DendrogramSerializable` |
| `bp.so` | `XMLSerializer_BPProcessObject`, `XMLSerializer_BPTaskObject`, `XDTOBPProcessObjectSerializable`, `XDTOBPTaskObjectSerializable`, `BPDBTools::serializeMetadataObjectsToXml` |
| `accnt.so` | `XMLSerializer_AccountObject`, `XMLSerializer_AccntRegRecordSet`, `XDTOAccountObjectSerializable`, `XDTOAccntRegRecordSetSerializable`, `AccntPredefXCFSerializer`, `AccntDBTools::serializeMetadataObjectsToXml` |
| `dcs.so` | many DCS-specific serializers, including `DCSSchemeDataCompositionSchemeSerializer`, `DCSSchemeDataSetQuerySerializer`, `DCSSchemeDataSetUnionSerializer`, `DCSATAppearanceTemplateSerializer`, `DCSCATAreaTemplateSerializer`, `DCSCTDataCompositionTemplateSerializer` |
| `plnnr.so` | `PlannerXDTOSerializer`, `PlannerItemScheduleXDTOSerializer`, `PlannerSerializerImpl`, `XDTOEnumTypeMapping_BWAValue` |
| `mngcore.so` | `LogFormXDTOHelperImpl`, `UObjectXDTOSerializer`, `XDTOTypeLinkSerializable`, `XDTOChoiceParameterSerializable`, `FormWindowSettingsXDTOSerializer`, many `XDTOEnumTypeMapping_*` names for managed forms/log forms |
| `moxel.so` | `spreadsheetDocument.xsd` in resource root; `.so` also contains Office Open XML exporter type identifiers, separate from 1C Designer XML validation |

## XCF error/status identifiers

`backend_root.res` and `config_root.res` include short error/status keys useful for oracle failure classification:

- Format/version: `IDS_XCF_ERR_FORMAT_VERSION_TOO_HIGH`, `IDS_XCF_ERR_DIFFERENT_FORMAT_VERSION`, `IDS_XCF_ERR_UNDEFINED_FORMAT_VERSION`, `IDS_XCF_ERR_UNKNOWN_FORMAT`, `IDS_XCF_ERR_INVALID_FILE_FORMAT`.
- XDTO/property shape: `IDS_XCF_ERR_XDTO_READ_ERROR`, `IDS_XCF_ERR_XDTO_PROPERTY_UNKNOWN`, `IDS_XCF_ERR_XDTO_PROPERTY_MISMATCH`, `IDS_XCF_ERR_WRONG_MD_PROPERTY`, `IDS_XCF_ERR_UNKNOWN_PROPERTY`.
- References/names: `IDS_XCF_ERR_BAD_REFERENCE`, `IDS_XCF_ERR_UNDEFINED_OBJECT`, `IDS_XCF_ERR_UNDEFINED_MDOBJECT_CLASS`, `IDS_XCF_ERR_UNDEFINED_FIELD_REF`, `IDS_XCF_ERR_UNDEFINED_TYPE_NAME`.
- Dump/import flow: `IDS_XCF_EXPORT_CONFIG`, `IDS_XCF_EXPORT_OBJECT`, `IDS_XCF_IMPORT_CONFIG_PREPARE`, `IDS_XCF_IMPORT_OBJECT_PREPARE`, `IDS_XCF_IMPORT_CONFIG_END`.
- Configurator command-line/import constraints: `IDS_XCF_ERR_XML_SOURCE_IS_NOT_ARCHIVE`, `IDS_XCF_ERR_XML_SOURCE_IS_NOT_DIR`, `IDS_XCF_ERR_NOT_EMPTY_DUMP_DIR`, `IDS_XCF_ERR_ARCHIVE_FILE_NAME`, `IDS_XCF_FORMAT_PLAIN`, `IDS_XCF_FORMAT_HIERARCHICAL`.

## Implications for xml-gen validators and oracle

1. Treat `backend_root.res` and `config_root.res` as the strongest safe clue sources for XCF export/import vocabulary. Their resource strings expose XCF namespace families, dump info schemas, readable/extprops/enums resources, and error classes without requiring binary interpretation.

2. Keep schema lookup modular. The platform appears to split schema resources by subsystem: base XDTO in `xdto_root.res`, XML Schema in `xml2_root.res`, managed forms/log forms in `mngcore_root.res`, formatted documents in `fmtd_root.res`, charts in `chart_root.res`, DCS in `dcs_root.res`, spreadsheet documents in `moxel_root.res`, planner in `plnnr_root.res`, and XCF/config wrappers in `backend_root.res`/`config_root.res`.

3. For `xml-gen validate`, map document namespaces to candidate resource families before semantic checks. Examples: `http://v8.1c.ru/8.1/data-composition-system/schema` should route to DCS schema logic; `http://v8.1c.ru/8.2/data/spreadsheet` to MXL/spreadsheet logic; `http://v8.1c.ru/8.2/managed-application/logform` to managed form/logform logic.

4. For oracle reporting, use platform-like categories instead of only generic XML errors. The observed XCF identifiers support buckets such as `format_version`, `unknown_property`, `xDTO_read_error`, `bad_reference`, `undefined_type`, `missing_file`, and `archive_or_directory_mode`.

5. Do not assume every domain root contains its own schema text. `bp_root.res` and `accnt_root.res` mostly expose UI resources, while `bp.so` and `accnt.so` expose domain serializer identifiers. XML/XDTO validation for these domains likely depends on backend metadata/XCF machinery plus domain serializers rather than standalone `.xsd` files visible in their root resources.

6. The platform has both plain and hierarchical XCF modes (`XCF_FORMAT_PLAIN`, `XCF_FORMAT_HIERARCHICAL`, `XCFPathPlain`, `XCFPathHierarchical`). Validators/oracles should preserve this distinction when comparing Designer XML dumps or reconstructing paths.

7. The visible serializer names confirm that some schema-less bodies in xml-gen should stay validation-only until a domain-specific behavioral oracle exists. Examples: DCS appearance/area templates, spreadsheet document bodies, formatted document content, planner bodies, BP/task objects, and accounting predefined/register records.

8. Safe future enhancement: add a read-only resource clue index in xml-gen docs/tests that records only these identifiers and namespace-to-domain mappings. Avoid embedding full schema text from platform resources; use short names and use generated fixtures from project XML for behavior tests.


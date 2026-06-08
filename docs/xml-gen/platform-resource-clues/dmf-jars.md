# DMF/JAR resource clues for xml-gen

Date inspected: 2026-06-08

Platform path: `/opt/1cv8/x86_64/8.3.27.2074`

Scope and safety:

- Inspected jar metadata/listings only with `jar tf`, `unzip -p` for manifests and `.properties`, `unzip -l`-equivalent listings through `jar tf`, and `strings` for literal tokens.
- No class decompilation was performed.
- No project XML or code was edited.

## Jars inspected

Primary DMF directory: `/opt/1cv8/x86_64/8.3.27.2074/dmf/lib/*.jar`

| Jar | Entries | Notes |
|---|---:|---|
| `aopalliance-1.0.jar` | not counted in focused table | Third-party AOP API. No 1C metadata clues. |
| `com._1c.chassis.time-0.7.0-6.jar` | not counted in focused table | 1C time utility bundle. No metadata clues. |
| `com._1c.dmf.common-1.13-92.jar` | 39 | DMF common helpers, graph, locale, reference id helper. |
| `com._1c.dmf.comparator-1.13-92.jar` | 90 | DMF comparison/converter interfaces and parameter holders. |
| `com._1c.dmf.migrationbuilder-1.13-92.jar` | 417 | Builds migration DB-space/SQL/evaluation model. |
| `com._1c.dmf.migrator-1.13-92.jar` | 90 | Migration execution/preparation for MSSQL/PostgreSQL. |
| `com._1c.dmf.model-1.13-92.jar` | 139 | Core entity, attribute, index, row-source, expression model. |
| `com._1c.dmf.model.changes-1.13-92.jar` | 15 | Changes/control/migration model. |
| `com._1c.dmf.model.configuration-1.13-92.jar` | 21 | Configuration model parameters. |
| `com._1c.dmf.model.migration-1.13-92.jar` | 31 | Migration model commands: clear, commit, schema creation, indexes. |
| `com._1c.dmf.source-1.13-92.jar` | 33 | Source/column abstractions. |
| `com._1c.dmf.sourceframework-1.13-92.jar` | 60 | Source processing framework. |
| `com._1c.dmf.sql-1.13-92.jar` | 167 | SQL model, SQL types, expressions, operators. |
| `com._1c.dmf.sqlframework-1.13-92.jar` | 304 | MSSQL/PostgreSQL SQL evaluators/introspection. |
| `com._1c.dmf.v8.cli-1.13-92.jar` | 42 | DMF CLI, JDBC, SDBL type parsing/model parsing. |
| `com._1c.dmf.v8.converters-1.13-92.jar` | 369 | Main V8 converter jar. Strongest clue source. |
| `com._1c.dmf.v8.integration-1.13-92.jar` | 48 | Integration, DB schema changes, SDBL serialization. |
| `com._1c.dmf.v8.logging-1.13-92.jar` | 46 | Tech-log support. |
| `com._1c.g5.i18n-1.0.1.jar` | not counted in focused table | 1C i18n/localizable support. |
| `com._1c.v8.core.streams-0.2.0-1.jar` | 37 | 1C stream/list stream support. |
| `com.microsoft.sqlserver.sqljdbc.jre8-6.2.jar` | not counted in focused table | JDBC driver only. |
| `commons-io-2.4.jar` | not counted in focused table | Third-party IO utility. |
| `guava-16.0.1.jar` | not counted in focused table | Third-party utility library. |
| `guice-3.0.jar` | not counted in focused table | Third-party DI library. |
| `icu4j-56.1.jar` | not counted in focused table | Third-party ICU data/classes. |
| `javax.inject-1.jar` | not counted in focused table | Third-party injection annotations. |
| `postgresql-42.7.8.jar` | not counted in focused table | JDBC driver only. |
| `slf4j-api-1.7.7.jar` | not counted in focused table | Logging API. |
| `slf4j-jdk14-1.7.7.jar` | not counted in focused table | Logging binding. |

Closely related platform metadata inspected:

- `/opt/1cv8/x86_64/8.3.27.2074/dmf_root.res`
- `/opt/1cv8/x86_64/8.3.27.2074/dmf_ru.res`
- `/opt/1cv8/x86_64/8.3.27.2074/sm-searcher/e1c-meta-1.2.jar`
- `/opt/1cv8/x86_64/8.3.27.2074/sm-searcher/e1c-xml-1.2.jar`
- `/opt/1cv8/x86_64/8.3.27.2074/sm-searcher/e1c-core-1.2.jar`
- `/opt/1cv8/x86_64/8.3.27.2074/sm-searcher/e1c-core-base-1.2.jar`

## Bundle metadata

The DMF jars are OSGi-style bundles. All `com._1c.dmf.*-1.13-92.jar` bundles report:

- `Bundle-Version: 1.13.0.92`
- `Implementation-Version: 54b8f8776df3704258bbcad37dd0a602bc908acb`
- `Bundle-Vendor: 1C`
- Java 8 execution environment

Exported package clues:

- `com._1c.dmf.model`, `com._1c.dmf.model.entities`, `com._1c.dmf.model.expressions`, `com._1c.dmf.model.rowsources`
- `com._1c.dmf.model.changes`
- `com._1c.dmf.model.configuration`
- `com._1c.dmf.model.migration`
- `com._1c.dmf.comparator`, `com._1c.dmf.comparator.predicates`
- `com._1c.dmf.migrationbuilder`
- `com._1c.dmf.migrator`
- `com._1c.dmf.sql`, `com._1c.dmf.sqlframework`
- `com._1c.dmf.v8.cli`
- `com._1c.dmf.v8.converters`
- `com._1c.dmf.v8.integration`

## Strong package/class clues

### Core DMF entity model

From `com._1c.dmf.model-1.13-92.jar`:

- `com/_1c/dmf/model/entities/DataTypeKind.class`
- `com/_1c/dmf/model/entities/NumericIntType.class`
- `com/_1c/dmf/model/entities/RefType.class`
- `com/_1c/dmf/model/entities/DataType.class`
- `com/_1c/dmf/model/entities/DataTypeList.class`
- `com/_1c/dmf/model/entities/Attribute.class`
- `com/_1c/dmf/model/entities/Index.class`
- `com/_1c/dmf/model/entities/SeparatorInfo.class`
- `com/_1c/dmf/model/entities/Entity.class`
- `com/_1c/dmf/model/entities/EntitiesModel.class`
- `com/_1c/dmf/model/entities/ExtZone.class`
- `com/_1c/dmf/model/entities/CompatibilityMode.class`
- `com/_1c/dmf/model/rowsources/EntityReference.class`
- `com/_1c/dmf/model/rowsources/FromEntityReference.class`
- `com/_1c/dmf/model/rowsources/EntityReferenceByPoint.class`

Interpretation: DMF has a machine model for database entities/attributes/indexes, not a full XML metadata model. This is useful for inferring database coverage expectations but not enough to generate metadata XML directly.

### V8 converters: high-value clue map

From `com._1c.dmf.v8.converters-1.13-92.jar`:

General converter packages:

- `com/_1c/dmf/v8/converters/attributes/`
- `com/_1c/dmf/v8/converters/entities/`
- `com/_1c/dmf/v8/converters/parameters/`
- `com/_1c/dmf/v8/converters/filters/`
- `com/_1c/dmf/v8/converters/controls/`
- `com/_1c/dmf/v8/converters/relations/`
- `com/_1c/dmf/v8/converters/common/`
- `com/_1c/dmf/v8/converters/ConverterStorage.class`
- `com/_1c/dmf/v8/converters/IIdGeneratorForPredefined.class`

Predefined-data-related packages/classes:

- `com/_1c/dmf/v8/converters/attributes/predefined/`
- `com/_1c/dmf/v8/converters/attributes/predefined/extdims/`
- `com/_1c/dmf/v8/converters/entities/predefineddata/`
- `com/_1c/dmf/v8/converters/entities/initpredefineddata/`
- `com/_1c/dmf/v8/converters/parameters/predefineddata/`
- `com/_1c/dmf/v8/converters/attributes/messagenumber/PredefinedChangesMessageNumberConverter.class`
- `com/_1c/dmf/v8/converters/filters/PredefinedChangeRecTo832Filter.class`
- `com/_1c/dmf/v8/converters/relations/InitPredefinedRelationConverter.class`
- `com/_1c/dmf/v8/converters/IIdGeneratorForPredefined.class`

Specific predefined attribute/entity classes:

- `PredefinedUtils.class`
- `ChartOfCharacteristicTypesNullForGroupsPredefined.class`
- `ChangedParentToNewPredefined.class`
- `ChangedParentPredefined.class`
- `ChangedParentToNullPredefined.class`
- `ChangedParentToOldPredefined.class`
- `ChangedParentPredefined832.class`
- `MarkedDeletedPredefined.class`
- `NewIsMetadata.class`
- `Compatibility832EnabledMarked.class`
- `ChangedAttributeExistingPredefined.class`
- `NewPredefinedIdWithoutPredefined.class`
- `NewPredefinedIdWithPredefined.class`
- `NewAttributeExistingPredefined.class`
- `PredefinedIdDeletedPredefined.class`
- `LineNoChangedPredefinedExtDims.class`
- `ChangedAttributeExtDimPredefined.class`
- `ExtDimIsMetadataChanged.class`
- `PredefinedDataUtils.class`
- `ExtDimsPredefinedDataConverter832.class`
- `ExtDimsPredefinedDataConverter.class`
- `PredefinedDataConverter832.class`
- `NewMdObjectPredefinedDataConverter832.class`
- `PredefinedDataConverter.class`
- `InitPredefinedDataUtils.class`
- `NewInitPredefinedDataConverter.class`
- `InitPredefinedDataFromOldConverter.class`
- `PredefinedData.class`
- `PredefinedData$PredefinedDataRow.class`
- `ChartOfAccountsExtDimPredefinedData.class`
- `ChartOfAccountsExtDimPredefinedData$ChartOfAccountsExtDimPredefinedDataRow.class`

Data exchange / exchange plan clues:

- `com/_1c/dmf/v8/converters/attributes/dataexchange/`
- `com/_1c/dmf/v8/converters/attributes/dataexchange/NewDataExchangePredefinedId.class`
- `com/_1c/dmf/v8/converters/entities/changerec/DataExchange.class`
- `com/_1c/dmf/v8/converters/entities/DataExchangeAddThisEndpointConverter.class`
- `com/_1c/dmf/v8/converters/entities/DataExchangeEndpointConverter.class`
- `com/_1c/dmf/v8/converters/filters/ExcludedDataExchangeFilter.class`
- `com/_1c/dmf/v8/converters/entities/changerec/ChangeRec.class`
- `com/_1c/dmf/v8/converters/entities/changerec/ChangeRecConverter.class`
- `com/_1c/dmf/v8/converters/entities/changerec/ChangedOrDeletedPredefinedChangeRecRegisterConverter.class`
- `com/_1c/dmf/v8/converters/entities/changerec/NewPredefinedChangeRecRegisterConverter.class`

The literal term `exchangeplan` was not found in jar entry names. The closest machine-visible concepts are `DataExchange`, `DataExchangeEndpoint`, and change-record converter classes.

Document journal and graph content clues:

- `com/_1c/dmf/v8/converters/entities/documentjournal/`
- `DocumentJournalUtils.class`
- `NewDocumentsToJournalConverter.class`
- `ChangedJournalDocumentConverter.class`
- `DocumentJournalFromOldConverter.class`
- `com/_1c/dmf/v8/converters/parameters/documentjournal/GraphContent.class`
- `GraphContent$DocumentGraphInfo.class`

Accounting/predefined extended dimensions clues:

- `com/_1c/dmf/v8/converters/entities/accounting/ChartOfAccountExtDimensions.class`
- `ChartOfAccounts.class`
- `ChartOfAccounts$AccountingFlag.class`
- `AccntReg.class`
- `AccntReg$Account.class`
- `AccntReg$Dimension.class`
- `AccntRegExtDimensions.class`
- `AccntRegNewPredefinedExtDimsConverter.class`
- `ChartOfAccountsExtensionDimensionsConverter.class`
- `AccntRegExtDimesConverter.class`
- `com/_1c/dmf/v8/converters/attributes/accountingflags/AccountingFlagChanged.class`

Typed-field/value-storage/binary clues:

- `TypeDomainPattern.class`
- `TypeDomainPattern$NumericQualifiers.class`
- `TypeDomainPattern$StringQualifiers.class`
- `TypeDomainPattern$DateQualifiers.class`
- `TypeDomainPattern$BinaryQualifiers.class`
- `TypeDomainPatternUtils.class`
- `ValueStorageUtils.class`
- `DbFieldDomain.class`
- `DbFieldDomain$RefInfo.class`
- `DbFieldDomain$FixedValue.class`
- `TypedFieldConverter.class`
- `ChangedTypes.class`
- `ChangedRef.class`
- `ChangedString.class`
- `ChangedNumeric.class`
- `ChangedDateTimeToDate.class`
- `ChangedDateTimeToTime.class`
- `DroppedTypeToDefaultValue.class`

Pictures/templates:

- No DMF jar entry names matched `picture`, `template`, `form`, `mxl`, or `spreadsheet`.
- Targeted binary/value/resource terms found only generic support such as `BinaryConstExpression`, SQL `Binary*Type`, `ValueStorageUtils`, and stream classes.
- This suggests DMF converter metadata is database-migration oriented and does not expose picture/template metadata coverage as named resources/classes in these jars.

### V8 integration and SDBL clues

From `com._1c.dmf.v8.integration-1.13-92.jar`:

- `com/_1c/dmf/v8/internal/integration/dbschema/IDbSchemaChangesCreator.class`
- `DbSchemaChanges.class`
- `SdblCommandSerializerSerializer.class`
- `ISdblCommandSerializer.class`
- `DbSchemaChangesCreator.class`
- `ChangesModelAnalyzer.class`
- `IArgumentStorage.class`
- `ArgumentStorage.class`

From `com._1c.dmf.v8.cli-1.13-92.jar`:

- `com/_1c/dmf/v8/cli/modelparsing/SdblTypeParser.class`

Interpretation: DMF can parse/serialize SDBL and DB schema changes, but the jar listing does not expose a declarative SDBL schema or XML schema file.

## Resource names found

No `.xml`, `.xsd`, `.json`, `.csv`, or `.sql` resources were found in the focused DMF jars. DMF resources are mostly localized diagnostic properties:

- `com/_1c/dmf/common/IMessagesList_ru.properties`
- `com/_1c/dmf/common/graph/IMessagesList_ru.properties`
- `com/_1c/dmf/internal/comparator/IMessagesList_ru.properties`
- `com/_1c/dmf/internal/migrationbuilder/**/IMessagesList_ru.properties`
- `com/_1c/dmf/internal/migrator/**/IMessagesList_ru.properties`
- `com/_1c/dmf/model/entities/IMessagesList_ru.properties`
- `com/_1c/dmf/model/visitor/IMessagesList_ru.properties`
- `com/_1c/dmf/model/changes/IMessagesList_ru.properties`
- `com/_1c/dmf/model/configuration/IMessagesList_ru.properties`
- `com/_1c/dmf/internal/sourceframework/IMessagesList_ru.properties`
- `com/_1c/dmf/internal/sqlframework/**/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/cli/**/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/attributes/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/attributes/predefined/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/attributes/typedfield/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/common/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/entities/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/entities/documentjournal/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/converters/entities/parameterholders/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/internal/integration/IMessagesList_ru.properties`
- `com/_1c/dmf/v8/logging/**/IMessagesList_ru.properties`

Selected strings from converter `.properties` resources:

- `row_source_does_not_contains_item_for_attribute`
- `cannot_create_constant_expression`
- `domain_must_have_undefined_type`
- `cannot_create_default_value_expression`
- `expected_hash_char`
- `invalid_value_storage_byte_array`
- `expected_uuid`
- `old_configuration_model_does_not_contain_entity`
- `document_ids_is_not_included_in_document_journal`
- `row_source_has_no_column_with_marker`
- `row_source_has_no_column_with_parameter`

These are diagnostic hints only. They do not define metadata object structure.

## Closely related platform metadata

`dmf_root.res` and `dmf_ru.res`:

- `strings` with target terms found no hits for `predefined`, `initpredefineddata`, `dataexchange`, `exchange`, `picture`, `template`, `converter`, `entity`, `entities`, `xml`, or `schema`.

`sm-searcher/e1c-meta-1.2.jar`:

- Annotation-like utility classes only, for example `Dto`, `Immutable`, `Getter`, `Nullable`, `NotNull`, `Threadsafe`.
- No 1C metadata object model resources found by listing.

`sm-searcher/e1c-xml-1.2.jar`:

- Generic XML classes: `XmlReader`, `XmlWriter`, `RawXml`, `SimpleTag`, `ComplexTag`, `XmlAttr`, `XmlConfigFile`, `ConfigSectionReader`, `ConfigSectionWriter`.
- Useful as evidence that the platform bundles a generic XML utility, but it does not contain 1C metadata XML schemas or object catalogs in the jar listing.

`sm-searcher/e1c-core*.jar`:

- Generic resource/config/converter utilities such as `ResourceReader`, `ResourceTools`, `IResource`, `ConfigSection`, `ConvertStringToDateTime`, `ConvertBytesToBase64`.
- No specific predefined-data/data-exchange/picture/template metadata catalog found.

## Machine-parseable material

Machine-parseable:

- Jar entry names from `jar tf`: yes. They provide stable package/class/resource names.
- OSGi manifest fields: yes. Useful for bundle versions, exported packages, dependencies.
- `.properties` diagnostic resources: yes, but limited to message keys/text.

Not machine-parseable enough for xml-gen coverage:

- No DMF XML/XSD/JSON metadata schemas were present in the inspected jars.
- No declarative tables mapping 1C metadata XML elements to DMF converters were found.
- Converter behavior is inside `.class` files. Without decompilation, only class/package names are available.
- `strings` output mostly repeats jar entry names and sparse diagnostic tokens; it does not expose a reliable metadata grammar.

## Concrete implications for xml-gen coverage gaps

1. Predefined data deserves explicit xml-gen coverage.

   The converter jar has multiple named packages/classes around `predefined`, `predefineddata`, `initpredefineddata`, predefined extended dimensions, predefined ID changes, and 8.3.2 compatibility. xml-gen should treat predefined data as a first-class coverage area, not only as a catalog/enum attribute side effect.

2. Initialization of predefined data is a separate concern.

   `entities/initpredefineddata/*` and `relations/InitPredefinedRelationConverter.class` imply that initial predefined data and its relations have distinct migration handling. xml-gen docs should separate:

   - predefined object definitions in metadata XML
   - initial/preloaded predefined data rows
   - relations/parent links for predefined items
   - compatibility behavior around old/new predefined IDs

3. Exchange plan/data exchange coverage is likely incomplete if it only models the object shell.

   The jars expose `DataExchangeEndpointConverter`, `DataExchangeAddThisEndpointConverter`, `NewDataExchangePredefinedId`, `ExcludedDataExchangeFilter`, and change-record converters. xml-gen coverage should verify exchange plan XML generation for:

   - endpoint/this-node predefined data
   - exchange change-record registers
   - data-exchange predefined IDs
   - filters/exclusion behavior when metadata changes

4. Accounting predefined extended dimensions need dedicated tests/docs.

   There are specific converters for chart-of-accounts and accounting-register extended dimensions, including predefined extended dimension rows. xml-gen should not assume generic predefined data covers accounting objects. Add coverage for chart of accounts/accounting register predefined ext dims and their metadata/data links.

5. Document journals have graph-content behavior worth documenting.

   `GraphContent` and document journal converter classes suggest document-journal content is not just a flat attribute list. xml-gen should validate document journal XML/content generation and update docs for document inclusion/content graph rules.

6. Pictures/templates are not covered by DMF jar clues.

   No target names for `picture`, `template`, `form`, `mxl`, or `spreadsheet` appeared in DMF jar entry names. For xml-gen, platform DMF jars do not provide useful resource clues for pictures/templates. Coverage for these must come from real configuration XML samples, known XML specs, EDT exports, or platform behavior tests, not from DMF metadata listings.

7. SDBL/DB schema clues are useful for database migration parity, not XML structure.

   `SdblTypeParser`, `SdblCommandSerializer*`, and DB schema change classes can inform whether generated metadata eventually produces expected database artifacts. They do not define the XML format. Keep them as downstream validation clues, not source-of-truth XML docs.

8. There is no discovered auto-ingest source for xml-gen docs.

   Because the inspected jars lack XML/XSD/JSON resource schemas, a doc generator cannot simply parse DMF resources into xml-gen coverage. The actionable path is a manually curated clue map plus tests against exported platform XML.

## Suggested follow-up coverage checklist

- Add/verify xml-gen docs and fixtures for predefined data rows.
- Add/verify init predefined data generation separately from object metadata.
- Add/verify exchange plan node/endpoint/predefined ID behavior.
- Add/verify change-record register metadata generated for exchange-related objects.
- Add/verify chart of accounts/accounting register predefined extended dimensions.
- Add/verify document journal content/graph behavior.
- Source picture/template/MXL/form clues from exported configuration XML samples rather than DMF jars.


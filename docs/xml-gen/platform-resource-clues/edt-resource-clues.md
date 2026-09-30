# EDT resource clues for xml-gen

Date inspected: 2026-06-08

Scope: read-only inspection of installed 1C:EDT 2025.2.5, local `v8-runner-rust` sources, and the public `1C-Company/dt-project-checks` repository. No EDT/platform files were modified. GitHub source was cloned to `/tmp/dt-project-checks` for source reading only.

## Installed EDT

Detected binaries:

- `/opt/1C/1CE/components/1c-edt-2025.2.5+2-x86_64/1cedt`
- `/opt/1C/1CE/components/1c-edt-2025.2.5+2-x86_64/1cedtcli`
- `/usr/local/bin/1cedtcli`

Relevant plugin root:

- `/opt/1C/1CE/components/1c-edt-2025.2.5+2-x86_64/plugins`

EDT uses its own project format (`.mdo`, `.form`, `.rights`, `.cmi`, `.dcss`, etc.). These files are not Designer XML, but the installed plugins expose useful EMF/Xcore models and import/export classes that can guide xml-gen invariants and conversion tests.

## v8-runner converter

Local source:

- `/workspaces/work/repos/1C Framework/v8-runner-rust`

Relevant files:

- `spec/decisions/0020-dobavit-cli-only-convert-dlya-dvustoronney-konvertatsii-edt-i-designer.md`
- `src/domain/convert.rs`
- `src/use_cases/convert_sources.rs`
- `src/platform/edt.rs`
- `src/support/edt_project.rs`
- `tests/cli_convert.rs`
- `tests/fixtures/edt/configuration`
- `tests/fixtures/designer/configuration`

Converter contract:

- public command is `v8-runner convert [--source-set <name>] [--output <dir>]`;
- direction is inferred from `v8project.yaml` source format:
  - `EDT -> Designer` when source set format is `EDT`;
  - `Designer -> EDT` when source set format is `DESIGNER`;
- conversion is backed by `1cedtcli` import/export, not by raw xml-gen;
- default workspace is under `workPath/convert/edt-workspace`;
- default output is under `workPath/convert/out/<sourceSetName>/<target-format>/`;
- source/target overlap is protected by staging and publication checks.

Low-level EDT CLI calls used by v8-runner:

- `1cedtcli -command export --project-name <project_name> --configuration-files <target>`
- `1cedtcli -command export --project <source> --configuration-files <target>`
- `1cedtcli -command import --project <source>`
- `1cedtcli -command import --configuration-files <source> --project <target> [--version ...] [--base-project-name ...] [--build]`
- `1cedtcli -command validate --file <out_log> --project-list <source>`

Practical use for xml-gen oracle:

1. Keep Designer XML round-trip oracle independent: decompile/compile or copy/build into a separate output and byte/semantic compare without touching the source tree.
2. Use v8-runner sources only as a read-only knowledge source for project layout and conversion preconditions.
3. Do not run EDT conversion as part of the current xml-gen oracle scope. The implemented oracle layer is local and fast: Xcore-derived structural checks plus dt-project-checks behavioral checks.

## EDT model plugins

Primary model jars with readable Xcore resources:

| Domain | Jar | Xcore/resource clues |
|---|---|---|
| Metadata classes | `com._1c.g5.v8.dt.metadata_18.0.100...jar` | `model/MdClass.xcore`, `Common.xcore`, `DbView.xcore`, `MdType.xcore`, `MdExternalProperties.xcore` |
| Metadata extensions | `com._1c.g5.v8.dt.metadata.extension_5.0.0...jar` | `model/MdClassExtension.xcore`, `ExtensionType.xcore` |
| Core values | `com._1c.g5.v8.dt.mcore_8.6.0...jar` | `model/Mcore.xcore` |
| Managed forms | `com._1c.g5.v8.dt.form.model_14.0.0...jar` | `model/Form.xcore`, `Mapping.xcore` |
| Form layout | `com._1c.g5.v8.dt.form.layout.model_9.0.0...jar` | form layout model |
| Rights | `com._1c.g5.v8.dt.rights.model_6.2.400...jar` | `model/Rights.xcore` |
| DCS/SKD | `com._1c.g5.v8.dt.dcs.model_9.0.100...jar` | DCS schema/settings/appearance/area-template packages |
| Query DCS | `com._1c.g5.v8.dt.ql.dcs.model_5.0.300...jar` | DCS query model |
| Aggregates | `com._1c.g5.v8.dt.aggregates.model_4.0.2900...jar` | `model/Aggregates.xcore` |
| Business process schemes | `com._1c.g5.v8.dt.bp.scheme.model_2.2.600...jar` | `model/GraphicalScheme.xcore` |
| XDTO | `com._1c.g5.v8.dt.xdto.model_4.3.0...jar` | XDTO model |
| CMI | `com._1c.g5.v8.dt.cmi.model_5.4.100...jar` | command interface model |
| HPWA | `com._1c.g5.v8.dt.hpwa.model_2.0.2900...jar` | home page work area model |
| Moxel | `com._1c.g5.v8.dt.moxel_17.0.0...jar` | spreadsheet/document domain classes |

The form Xcore is directly useful. It declares:

- containment/reference semantics (`contains`, `refers`);
- predefined/transient elements such as `autoCommandBar`, `contextMenu`, `extendedTooltip`, `selectedItemsActionsPanel`, `rowActionsPanel`, `autoTable`;
- since-version comments, for example 8.3.21 and 8.5.1 form features;
- validators imported into the model, including `FormValidator`, `GroupValidator`, `TableValidator`, `FlowchartFieldValidator`, `DataPathValidator`, `FormCommandValidator`, `FormAttributeValidator`.

This is not enough to serialize Designer XML by itself, but it is enough to derive and verify invariants that xml-gen must preserve.

## EDT import/export plugins

Relevant installed import/export jars:

| Domain | Import/export jars |
|---|---|
| General Designer XML | `com._1c.g5.v8.dt.import.xml_16.0.0...jar`, `com._1c.g5.v8.dt.export.xml_13.0.100...jar` |
| Metadata | `com._1c.g5.v8.dt.md.import.xml_6.0.0...jar`, `com._1c.g5.v8.dt.md.export.xml_10.0.101...jar` |
| Forms | `com._1c.g5.v8.dt.form.import.xml_8.0.1...jar`, `com._1c.g5.v8.dt.form.export.xml_10.1.0...jar` |
| Rights | `com._1c.g5.v8.dt.rights.import.xml_2.0.3200...jar`, `com._1c.g5.v8.dt.rights.export.xml_2.0.3200...jar` |
| BSL | `com._1c.g5.v8.dt.bsl.import.xml_2.0.3200...jar`, `com._1c.g5.v8.dt.bsl.export.xml_2.0.3200...jar` |
| DCS/SKD-related | DCS model plus XML import/export support in common import/export stack |
| Aggregates | `com._1c.g5.v8.dt.aggregates.import.xml_1.0.3000...jar`, `com._1c.g5.v8.dt.aggregates.export.xml_1.0.3000...jar` |
| XDTO | `com._1c.g5.v8.dt.xdto.import.xml_1.0.3000...jar`, `com._1c.g5.v8.dt.xdto.export.xml_1.0.3000...jar` |
| CMI | `com._1c.g5.v8.dt.cmi.import.xml_2.0.3200...jar`, `com._1c.g5.v8.dt.cmi.export.xml_2.1.300...jar` |
| Flowchart/planner/chart/style/schedule | corresponding `*.import.xml` and `*.export.xml` jars are present |

The form import/export jars expose class names such as:

- readers: `FormXmlFileReader`, `FormXmlReaderResult`, `FormChildItemsXmlPartReader`, `FormCommandXmlPartReader`, `FormCommandInterfaceXmlPartReader`, `FormAttributeXmlPartReader`, `FormParameterXmlPartReader`, `FormValueXmlPartReader`;
- writers: `FormXmlWriter`, `FormItemWriter`, `FormAttributeWriter`, `FormCommandWriter`, `FormCommandInterfaceItemsWriter`, `DataPathWriter`, `ContextMenuWriter`, `AutoCommandBarWriter`, `ExtInfoWriter`;
- XML constants: `IFormXmlElements`.

Practical use:

- the class names identify the XML parts EDT treats as first-class; use them as a checklist for xml-gen form validator/writer coverage;
- avoid decompiling these classes unless needed later; Xcore plus public behavior through `1cedtcli` is enough for oracle design.

## dt-project-checks

Repository:

- `https://github.com/1C-Company/dt-project-checks`
- cloned read-only to `/tmp/dt-project-checks`

Installed EDT jars:

- `com.e1c.dt.check.form_0.10.0.v20251119-1031.jar`
- `com.e1c.dt.check.md_0.10.0.v20251119-1031.jar`
- `com.e1c.g5.v8.dt.check_6.0.100.v202604021910.jar`

The project describes itself as an EDT extension for checking structural integrity of 1C:Enterprise 8 projects. It is useful for xml-gen because it contains small, explicit integrity checks and negative workspace fixtures.

Registered checks:

| Check id | Domain | What it validates | xml-gen implication |
|---|---|---|---|
| `form-invalid-item-id` | Form | every `FormItem` has a valid id; duplicate ids are reported after the first duplicate | CLI `form add-element` and generated forms must allocate stable, unique ids across nested items and predefined containers |
| `form-named-element-name` | Form | names of form attributes, commands, parameters, and items are present and valid; `AutoCommandBar` is excluded | CLI generation must reject/normalize invalid names and must not warn on platform-created nameless auto command bar |
| `form-data-path` | Form | each segment of a data path resolves to an object/property unless special base-form handling applies | from-object generation and form edits must validate `dataPath`, dynamic list paths, and object/list form paths |
| `md-reference-intergrity` | Metadata | lost/proxy references in important metadata collections | meta CLI commands must update both object files and owner collections such as `Configuration`, `Subsystem`, `CommonAttribute`, `ExchangePlan` |

`md-reference-intergrity` checked collections:

- `Configuration`: accounting/accumulation/calculation registers, bots, business processes, catalogs, charts, command groups, common attributes/commands/forms/modules/pictures/templates/settings storage, constants, data processors, defined types, document journals/numerators/documents, enums, event subscriptions, exchange plans, external data sources, filter criteria, functional options/parameters, HTTP services, information registers, integration services, palette colors, reports, roles, scheduled jobs, sequences, session parameters, settings storages, styles/style items, subsystems, tasks, web services, web socket clients, WS references, XDTO packages.
- `Subsystem`: `content`, nested `subsystems`.
- `CommonAttribute`: `CommonAttributeContentItem.metadata`.
- `ExchangePlan`: `ExchangePlanContentItem.mdObject`.
- `StandaloneContent`: used/unused/priority item metadata references.

Important test fixture behaviors:

- `form-invalid-item-id`:
  - default generated list form has no marker;
  - id `0` on immediate or nested child is invalid;
  - missing id is invalid;
  - negative id has no marker in the EDT check;
  - multiple missing ids produce separate issues;
  - duplicate ids mark each duplicate after the first occurrence.
- `form-named-element-name`:
  - empty `AutoCommandBar` name is not an issue;
  - invalid names on form item, extended tooltip, context menu, and attribute are issues;
  - correct form names produce no nested marker.
- `form-data-path`:
  - dynamic list with custom query: known list fields are accepted, unknown field is reported on derived form but ignored on base form;
  - list form with main table: `Ref`, `Code`, custom fields, `Current` paths are accepted; unknown paths are reported on derived form but ignored on base form;
  - object form: known object fields accepted, unknown object field reported on derived form.
- `md-reference-intergrity`:
  - removing referenced top objects creates markers on `Configuration`, `Subsystem`, and `CommonAttribute` content;
  - restoring the objects clears markers;
  - manual stale-reference removal clears markers.

## Recommended xml-gen work

1. Add an EDT-derived invariant report step to oracle output.
   - It should not replace existing PASS/WARN classification.
   - It should explain whether generated/edit-mode artifacts satisfy the known EDT structural invariants.

2. Add form id oracle checks.
   - After every CLI form generation/edit scenario, parse all `FormItem` ids.
   - Fail on missing id and id `0`.
   - Fail on duplicate positive ids.
   - Keep a note that EDT does not flag negative ids, but xml-gen should avoid generating them.

3. Add form name oracle checks.
   - Validate names for form attributes, commands, parameters, and items.
   - Exclude auto command bar placeholders from required-name warnings.
   - Preserve current handling of required empty placeholder groups/menus separately from named element checks.

4. Add form dataPath checks where enough metadata context is available.
   - First target generated/from-object forms, because the metadata source object is known.
   - Check obvious object fields and dynamic-list fields.
   - Treat extension/base-form cases carefully; dt-project-checks intentionally suppresses some base-form findings.

5. Add metadata reference-integrity checks to CLI-generation mode.
   - For every command that creates or adds a metadata object, assert the owner collection references it.
   - Cover `Configuration` first.
   - Then cover `Subsystem.content`, `CommonAttribute.content.metadata`, `ExchangePlan.content.mdObject`, and standalone content once these writers are supported.

6. Use Xcore extraction as a generated checklist.
   - Parse `*.xcore` resources from the installed EDT jars into a small JSON manifest:
     - classes;
     - enum names;
     - `contains` features;
     - `refers` features;
     - transient/predefined features;
     - since-version comments where simple to extract.
   - Compare this manifest to xml-gen registry/validator coverage.
   - Start with `MdClass.xcore`, `Form.xcore`, `Rights.xcore`, `Aggregates.xcore`, `GraphicalScheme.xcore`.

## Priority impact on current gaps

| Gap | EDT clue | Suggested oracle/use |
|---|---|---|
| Form body generation/editing | `Form.xcore`, form import/export readers/writers, dt-project-checks form checks | Add id/name/dataPath invariants to CLI-command oracle |
| Form wrappers | `MdClass.xcore`, metadata import/export, reference integrity checks | Keep wrapper oracle, add owner collection/link checks |
| Template wrappers | metadata model and reference integrity | Check registration/linkage; body fidelity remains body-domain-specific |
| ExchangePlanContent | `MdReferenceIntegrity` explicitly checks `ExchangePlanContentItem.mdObject`; aggregates/import/export jars exist | Add content reference integrity check after CLI creation/edit |
| AccumulationRegisterAggregates | `Aggregates.xcore`, aggregates import/export jars, validators | Build read-model and invariants before synthesis |
| Flowchart | `GraphicalScheme.xcore`, `bp.scheme.xml.serialization`, import/export jars | Use `GraphicalScheme` model as checklist for Flowchart body validation |
| Rights | `Rights.xcore`, rights import/export jars, validators | Compare role DSL coverage to rights model classes and right names |
| SKD/DCS/AppearanceTemplate | DCS model packages and appearance-template package | Use DCS Xcore/classes as coverage checklist; keep Designer XML body oracle separate |

## Bottom line

EDT resources should be used in three layers:

1. **Model checklist** from Xcore and import/export class names: what object kinds, child collections, references, and validators exist.
2. **Behavioral invariants** from `dt-project-checks`: what structural mistakes EDT itself reports.
3. **v8-runner source reading only**: project layout and conversion preconditions are useful as knowledge, but the current xml-gen oracle does not invoke conversion.

They do not replace the two core xml-gen oracle modes. The main oracle should still run both:

- Designer XML round-trip into a separate output and compare;
- full CLI-command generation/editing followed by validation and comparison.

Current implemented scope:

- `EdtDerivedInvariantChecker` runs locally over Designer XML.
- It uses Xcore/dt-project-checks-derived rules for form ids, form names, `Configuration` references, `CommonAttribute` metadata references, and `ExchangePlanContent` metadata references.
- It is attached to `oracle demo` as `details.edtDerivedInvariants`.
- It does not run `v8-runner convert` or `1cedtcli`.

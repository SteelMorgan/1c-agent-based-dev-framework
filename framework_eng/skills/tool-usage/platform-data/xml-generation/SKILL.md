---
name: xml-generation
description: "For any 1C metadata XML via xml-gen CLI"
argument-hint: <domain> <operation> [<args>]
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
metadata:
  category: 1c-development
  version: "2.0"
---

# xml-generation — Toolkit for Working with 1C Metadata XML

The unified `xml-gen` CLI covers the entire 1C XML workflow: generation from JSON DSL, targeted modification of existing files, and validation. This SKILL.md is a **router**: it contains an overview, an index of subdomains, and cross-cutting principles. For detailed specifications for each domain, go to the corresponding sub-skill (`<name>/SKILL.md`).

## §1 xml-gen CLI Overview

Installation: `python tools/install.py --install-xml-gen` (requires JDK 17+).

`xml-gen` has two complementary working surfaces:

- **JSON DSL surface** — `mxl/skd/form/role/meta compile` and available decompilers, such as `mxl decompile`, `form decompile`. Use when it is more convenient to describe an artifact declaratively and compile it into Designer XML. `form decompile` is only a draft/scaffold, not a lossless round-trip.
- **Operational CLI surface** — public commands `epf init`, `epf add-template`, `form add-element`, `meta edit`, `template add`, `validate`. Use when you need to create or modify an existing metadata tree through explicit CLI actions.
- **Support safety surface** — `support check/info` and the built-in mutation guard. `xml-gen` reads `Ext/ParentConfigurations.bin` and blocks direct XML editing of standard configuration objects under vendor support.

For maintaining the tool itself, `xml-gen` includes diagnostic oracle commands. They are not needed for ordinary XML generation/editing tasks; their reference is documented separately: [references/behavioral-oracles.md](references/behavioral-oracles.md).

Details are in §2 and the sub-skills. Universal commands (validate, form/template/help add, edit replace-text) are described in §3.

**Do not use** when: the EDT format is required (Designer only), or DataSetUnion/CalculatedFields are required in СКД (workaround: calculations in queries).

## §2 Sub-area index

| Sub-area | What it does | When to use | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — operational cycle for working with managed forms and EPF | analyzing form structure, adding fields, validation, Title→Name mapping for Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | compiling a form from JSON DSL (`form compile`, including `--from-object`) and draft `form decompile` | creating a form from scratch, generating one from an object, or creating a JSON draft from a sample | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | compiling СКД from JSON (`skd compile`) | creating a layout composition schema from scratch | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch operations on an existing СКД (`skd add-parameter`, `skd add-field`) | making targeted changes to Schema.xml | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | MXL / SpreadsheetDocument layouts (`mxl compile`) | printed forms, templates | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | compiling roles (`role compile`, `role add-object`, `role add-right`) | creating/changing a role | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | working with the configuration root (`config init/info/edit/validate`) | initializing a new CF, navigating the root | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 types of metadata objects (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums, etc. | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | subsystems and command interfaces (`subsystem compile/edit`, `interface edit/validate`) | organizing the configuration interface | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | external data processors and reports (`epf init/add-form/add-template/bsp-init`) | creating EPF / ERF from scratch, including БСП variants | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | configuration extensions / CFE (`extension init/borrow/diff`) | creating a CFE, borrowing objects, comparing an extension with the base | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Universal commands (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`, `support check/info`) are described in §3 below and do not have a separate sub-skill.

## §3 Universal Commands

Five groups: **validate** (structural/semantic validation of any XML), **support check/info** (checking the vendor support status), **form/template/help add** (adding forms, templates, and help to any metadata object), **edit replace-text** (byte-by-byte replacement without normalizing line endings).

When to use: validate — before and after every modification; support check/info — before deliberately editing a standard configuration under support or in hooks; form/template/help add — when you need to register a new artifact without rebuilding; edit replace-text — for a targeted XML edit with multiline content in `<v8:content>` (tooltips, descriptions) or any replacement where preserving line endings matters.

→ [references/universal-commands.md](references/universal-commands.md)

## §3.1 Tool Diagnostics

Oracle commands are intended for maintaining `xml-gen` and checking behavior on canonical XML, not for ordinary generation of a single artifact. See [references/behavioral-oracles.md](references/behavioral-oracles.md) for details, modes, and test matrices.

## §3.2 Visual Review and Second Opinion (MCP `1c-form-viewer`)

If the MCP server `1c-form-viewer` is connected (third-party, view-only; a tool for quick visual assessment after creation or modification):
- after generating or editing a **form, template, or DCS** and after `xml-gen validate`, call `open_preview` (`audience="agent"`, absolute path) and `capture_preview`. This is an initial visual assessment without building the database;
- for forms — use `validate_form` as a **second opinion** alongside `xml-gen validate`. It is non-blocking and does not replace `xml-gen validate`;

**Not for editing**: edit XML only through `xml-gen`; editing on the server is disabled by flags. The preview does **not** replace checking in the 1C client (`va-visual-check`). Triggers, tools, limitations, and flags are described in [references/form-viewer-mcp.md](references/form-viewer-mcp.md).

## §4 Cross-cutting Principles

1. **Designer format only** — `--format designer` (default). EDT is not supported.
2. **Encoding** — UTF-8 with BOM (`utf-8-sig`). Preserve the BOM when editing.
3. **Line endings** — CRLF between tags, bare LF in `<v8:content>`. Do not use Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — run `validate` before and after modification. On error, `<domain> edit` rolls back automatically.
5. **Batch operations** — the JSON format for `form edit` / `meta edit` / `subsystem edit` accepts arrays of operations; use it instead of repeated CLI calls.
6. **EPF layout** — root XML: `output/MyProcessor.xml`. EPF forms: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.
7. **Oracle sandboxing** — `xml-gen oracle ...` reads the canonical source and writes generated XML only under `--out`; do not point oracle output inside `src/xml`.
8. **Vendor support guard** — mutation commands inside `xml-gen` check `Ext/ParentConfigurations.bin`: `G=1` blocks the entire configuration, `f1=0` blocks the object, deletion requires `f1=2`. CFE extensions are not blocked.

## §4.1 Platform Anomalies After XML Generation

If, after the latest `xml-gen` operation, a build, Designer-load, opening an external object, configuration load, or runtime object opening does not complete within the time normally expected in this environment, hangs, consumes memory indefinitely, ends with an unclear platform error, or displays a message such as "fatal error attempting to open file," treat this first and foremost as a sign of an invalid XML structure created by the latest `xml-gen` iteration.

Do not treat this situation as ordinary platform instability, and do not work around it by making local manual XML edits. If an operation is claimed to be supported but `xml-gen` creates an invalid or noncanonical structure, this is a tool defect, not grounds for an exception to the `no-manual-xml-edit` rule. Follow this procedure:

1. Immediately stop the dependent development flow at the first step that requires the generator's faulty output. Do not continue it with locally fixed XML.
2. Record the latest `xml-gen` operation, exact input, tool version/hash, and observed failure. Register a separate tool defect; if the project maintains an XG registry, create a new `XG-<number>` before continuing the dependent flow.
3. Stop the hung process and collect minimal logs/failure signals; do not run the same build indefinitely.
4. Find a matching working object in the project or canonical sample: external report to external report, external data processor to external data processor, form to form, layout to layout, DCS to DCS. If you cannot reliably find a matching sample, ask the user.
5. Reproduce the defect with a minimal immutable fixture. Compare the XML structure with the project's structural diff script or an equivalent XML-tree diff that compares tags, attributes, the order of significant nodes, and key values, rather than just text strings.
6. Use the diff to identify the smallest structural difference that explains the failure. Refer to the platform round-trip, canonical dump, working object, and `xml-gen` validators, but remember: a successful `xml-gen validate` does not prove that the object is usable by the platform.
7. Fix the generator and first capture the failure/fix with a regression red/green test or behavioral oracle. Do not substitute the input or enshrine a workaround in a specific XML file as a "solution".
8. Perform a full build of the tool and check the built artifact by replaying the original scenario; a targeted test alone is not sufficient.
9. Regenerate the erroneous fragment with the standard `xml-gen` command, and perform the applicable `validate`, build/load/open, and live/runtime checks on the original object class. For structures sensitive to Designer serialization, use the Configurator package/load/round-trip oracle.
10. Save the proof and only after a successful live check return to the interrupted step in the dependent flow. The current successful fix is not automatically proof of the historical behavior of the old artifact: mark historical and current-fix evidence separately.

For stuck platform processes, use a time limit and explicitly terminate only the process started by the current step. Do not leave a hang as “still running” without checking logs, artifact size, processes, and the usual operation time.

## §5 Quick examples (entry-level workflows)

### Create an external data processor with a form

```bash
epf-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen validate --type epf output/MyProcessor
```

Details — [epf-full/SKILL.md](epf-full/SKILL.md).

### Add a field to an existing form

```bash
# 1. Examine the structure
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Add an element bound to an attribute
xml-gen form add-element --type InputField --name Склад --path Объект.Склад \
  --parent ГруппаШапка --after Контрагент \
  "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 3. Check
xml-gen validate --type form "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"
```

Details — [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) (info/edit/decompile/validate) and [form-dsl/SKILL.md](form-dsl/SKILL.md) (compile from scratch).

### Compile an SKD from JSON

```bash
xml-gen skd compile schema.json Template.xml
xml-gen validate --type skd Template.xml
```

Details — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). For a targeted edit of an existing Schema.xml — [skd-edit/SKILL.md](skd-edit/SKILL.md).

### Create an extension and borrow an object

```bash
xml-gen extension init output_ext/ МоёРасширение --config-path output/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/
```

Details — [extension-operations/SKILL.md](extension-operations/SKILL.md).

### Check support status before editing

```bash
xml-gen support info "src/Catalogs/Номенклатура.xml"
xml-gen support check "src/Catalogs/Номенклатура.xml" --require editable
xml-gen support check "src/Catalogs/Номенклатура.xml" --require removed --output json
```

`support check` exits with an error if mutation is prohibited. To delete an object, first explicitly move the object to the off-support state through an external agreed action; `xml-gen` does not remove support implicitly.

## §6 Anti-patterns (right / wrong)

```bash
# Wrong: role compile with a file as output
xml-gen role compile role.json Roles/МояРоль.xml

# Right: output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

```bash
# Wrong: form add-element without --path
xml-gen form add-element --type InputField --name Наименование Form.xml

# Right: --path binds the element to an attribute
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# Wrong: role add-object with "view"
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# Right: rights are comma-separated, casing from enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## §7 Additional protection layers (for agents without PreToolUse)

For agents without the PreToolUse protocol (Codex, Cursor, Aider, Cline, etc.), it is recommended to configure additional protection layers:

- **Git pre-commit hook** (`tools/hooks/pre-commit`) — extend it to run `--check` on all staged `.xml`/`.mxl` files. This is a last line of defense: it prevents the changes from entering the repository even if the agent ignored the rule:
  ```bash
  python3 tools/hooks/block-direct-xml-edit.py --check "<staged-file>" --tool Edit
  ```
  With exit code `2`, the file belongs to 1C metadata, and the commit is aborted.
- **CI on PR** — the same `--check` on the diff catches any direct-edit attempts when entering `main`.

Fine-tuning: the `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` lists are defined as constants in `tools/hooks/block-direct-xml-edit.py`. Add to them if a new 1С configuration pattern appears in the project (for example, a non-standard location) or a new false positive arises (build XML with a unique name).

## §8 Workarounds

| Problem | Solution |
|----------|---------|
| `Parent element not found` (form) | Check the exact parent name in Form.xml — case matters |
| `Object already exists` (role) | Use `role add-right` instead of `add-object` |
| `DataSet not found` (skd) | Check the data set name in Schema.xml |
| Edit tool breaks line endings | Use `xml-gen edit replace-text` |
| DataSetUnion / CalculatedFields is needed in СКД | Workaround: perform calculations in queries |
| EDT format is needed | Not supported; Designer only |

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/forms-toolkit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-edit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-interface/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/epf-full/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
---

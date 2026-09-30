# Behavioral oracle xml-gen — tool maintenance reference

Use oracle commands when you need to check `xml-gen` behavior against existing canonical XML, not when you just want to generate a single artifact.

Oracle always writes to a sandbox and must not overwrite `src/xml`.

```bash
# DSL round-trip: decompile canon into JSON DSL, compile into a separate sandbox, and compare
xml-gen oracle mxl --source src/xml --out build/oracle --mode dsl

# CLI command reconstruction: build and execute a CommandPlan from public xml-gen commands
xml-gen oracle mxl --source src/xml --out build/oracle --mode cli

# Run both independent modes and report on them separately
xml-gen oracle mxl --source src/xml --out build/oracle --mode both

# Broad _Demo audit for classes that still do not have a full decompiler/CommandPlan oracle
xml-gen oracle demo --source src/xml --out build/oracle-demo --threads 8

# PredefinedData CLI reconstruction through public meta commands
xml-gen oracle predefined-data --source src/xml --out build/oracle-predefined-data

# ExchangePlanContent CLI reconstruction through public meta commands
xml-gen oracle exchange-plan-content --source src/xml --out build/oracle-exchange-plan-content

# Structural rule mining from a trusted Designer XML corpus
xml-gen oracle mine-rules --source src/xml --out build/oracle-rule-mining --min-support 2 --digest-limit 500
```

## MXL oracle

The two `oracle mxl` modes are independent:

- a green DSL round-trip does not prove the operational CLI command surface;
- a green CLI reconstruction does not prove a byte-for-byte DSL round-trip.

For `src/xml`, the MXL oracle defaults to the `_Demo` pilot corpus; use `--include-all` only for a broad audit of all MXL `Template.xml` files.

`mxl decompile` emits an editable JSON projection (`columns`, `areas`, `styles`, pictures, etc.) plus a `losslessXmlBase64` payload. `mxl compile` preserves that payload byte-for-byte so unsupported Designer sections are not silently dropped during the oracle round-trip.

Reports are written under the selected `--out`: `oracle-report.json`, `coverage-matrix.json`, `xg-candidates.*`.

## PredefinedData oracle

`oracle predefined-data` is a separate behavioral CLI oracle for `Ext/Predefined.xml`.

It decompiles canon `PredefinedData` into a JSON element tree, then executes only public commands in a sandbox:

- `config init`;
- `meta compile`;
- `meta edit --op add-predefined --value @items.json`;
- `validate --type xcf-body`.

It covers Catalog, ChartOfAccounts, ChartOfCalculationTypes, and ChartOfCharacteristicTypes, including empty `<Code/>`, nested `ChildItems`, `Type`, `AccountType`, `AccountingFlags`, `ExtDimensionTypes`, `ActionPeriodIsBase`, and `Displaced`.

## ExchangePlanContent oracle

`oracle exchange-plan-content` is a separate behavioral CLI oracle for `ExchangePlans/<Name>/Ext/Content.xml`.

It decompiles canon `ExchangePlanContent` into JSON elements, then executes only public commands in a sandbox:

- `config init`;
- `meta compile`;
- `meta edit --op add-exchange-content --value @items.json`;
- `validate --type xcf-body`.

It covers `Item/Metadata` and `Item/AutoRecord`.

## Demo oracle

`oracle demo` is not a replacement for a full behavioral decompiler.

It runs public `xml-gen validate --output json --level semantic` commands in parallel across `_Demo` XML files and executes CLI registration checks for wrapper artifacts that are part of the configuration tree:

- `Forms/<Name>.xml`;
- `Templates/<Name>.xml`;
- `Ext/Help.xml`.

Additional checks:

- Help-template registration smoke through `template add --type Help`;
- picture-body lossless oracle for `Ext/Picture.xml` plus binary payload preservation;
- synthetic `form-generation-edit` smoke through public form commands: `form compile`, `form add-attribute`, `form add-command`, `form add-element`, `form move-element`, `validate`;
- local `EdtDerivedInvariantChecker`, based on EDT Xcore and `dt-project-checks`, without running EDT/v8-runner.

`form-generation-edit` checks generation invariants such as `UserSettingsGroup` placeholders, table additions with `AdditionSource`, `ChildItems` placement, `AutoCommandBar`, `Popup`, `ButtonGroup`, button `Type=UsualButton`, and `CommandName`.

XCF bodies without full reconstruction remain `validation_only_no_decompiler`:

- `Ext/Content.xml`;
- `Ext/Predefined.xml`;
- `Ext/Aggregates.xml`;
- `Ext/Flowchart.xml` (`GraphicalSchema` / `http://v8.1c.ru/8.3/xcf/scheme`);
- `AppearanceTemplate`;
- help-template bodies.

The report classifies files by `artifactKind`, `capability`, and `failureBucket` (`format_version`, `unknown_property`, `xDTO_read_error`, `bad_reference`, `undefined_type`, `missing_file`, `archive_or_directory_mode`, etc.).

## EDT-derived invariants

`EdtDerivedInvariantChecker` works locally with Designer XML and does not call EDT, `1cedtcli`, or `v8-runner`.

Current checks:

- form item `id`: missing, `0`, duplicate, negative value as a warning;
- form named elements: attributes / commands / parameters / items, excluding an empty `AutoCommandBar`;
- metadata reference integrity:
  - `Configuration/ChildObjects` -> object files exist;
  - `CommonAttribute` metadata refs;
  - `ExchangePlanContent/Metadata` refs.

In `oracle demo`, the result is written to `details.edtDerivedInvariants`.

## Rule mining oracle

`oracle mine-rules` scans a trusted Designer XML corpus and writes structural rule candidates for maintaining `xml-gen`.
It does not validate one edited object and does not call Designer/EDT.

Outputs:

- `rule-mining-report.json` — full raw facts and candidates;
- `rule-candidates.json` — raw candidates only;
- `rule-digest.json` — prioritized, bundled candidates for agent analysis;
- `rule-digest.md` — human/agent-readable digest with stable bundle keys;
- `rule-mining-summary.md` — corpus and bucket summary.

Candidate families:

- `LINKED_BODY` — wrapper-to-body presence, for example `Forms/<Name>.xml -> Ext/Form.xml`;
- `DISCRIMINATOR_LINKED_BODY` — body choice conditioned by wrapper property, for example `FormType=Managed -> Ext/Form.xml`, `FormType=Ordinary -> none`, `TemplateType=TextDocument -> Ext/Template.txt`;
- `DISCRIMINATOR_NODE_CONTRACT` — child contract conditioned by an in-node discriminator, for example form `Item/Type`;
- `ROOT_CONTRACT`, `REQUIRED_CHILD`, `REQUIRED_ATTRIBUTE`, `VALUE_DOMAIN`, `CARDINALITY`, `CHILD_ORDER` — general structure observations.

Feedback loop:

After a digest bundle is analyzed, add its stable `key` from `rule-digest.json` or `rule-digest.md` to a disposition file.
On the next scan, pass the file via `--disposition`; processed bundles are suppressed from the digest and counted in `feedbackSummary`.
The `key` may be exact or a glob pattern with `*` / `?`. Use exact keys for one-off findings and glob keys for an analyzed structural area, otherwise the next scan can resurface the same idea with a different signal set, for example `Properties` after `Properties/*`.

```json
{
  "entries": [
    {
      "key": "bundle:FormWrapper:FormType=Managed -> Ext/Form.xml:DISCRIMINATOR_LINKED_BODY",
      "status": "promoted",
      "reason": "Implemented by form wrapper registration validator",
      "target": "xml-gen",
      "updatedAt": "2026-06-08"
    },
    {
      "key": "bundle:MetaDataObject.Catalog:/MetaDataObject/Catalog/Properties*:*",
      "status": "implemented",
      "reason": "Catalog Properties set was analyzed against MetaWriter and MetaValidator coverage",
      "target": "xml-gen meta compile/validate",
      "updatedAt": "2026-06-09"
    }
  ]
}
```

Suppressing statuses: `promoted`, `implemented`, `accepted`, `rejected`, `ignored`.
Use `promoted`/`implemented` when the pattern became an invariant or generator behavior; use `rejected`/`ignored` only with an explicit reason so the same known false positive does not re-enter the analysis queue.
Use `accepted` for validation-only or consciously out-of-scope mined knowledge that should remain documented but should not keep blocking the next mining cycle.

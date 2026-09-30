# Validation: form-validate and epf-validate

## form-validate — Form Validator

Checks a managed form's Form.xml for structural errors: ID uniqueness, presence of companion elements, and correctness of DataPath and command references.

### Usage

```
/form-validate <FormPath>
```

### Parameters

| Parameter | Required | Default | Description              |
|-----------|:--------:|---------|--------------------------|
| FormPath  | yes      | —       | Path to the Form.xml file |
| MaxErrors | no       | 30      | Stop after N errors      |

### Command

```bash
xmlgen validate --type form "<FormPath>"
```

With JSON report:
```bash
xmlgen validate --type form --output json "<FormPath>"
```

> Implementation: Java-CLI `xmlgen validate` (replacement for the Python script). Error codes are `FORM-001..008` (structure), `FORM-101..120` (semantics).

### Checks Performed

| # | Check | Severity |
|---|---|---|
| 1 | Root element `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` is present, id="-1" | ERROR |
| 3 | Element ID uniqueness (separate pool) | ERROR |
| 4 | Attribute ID uniqueness (separate pool) | ERROR |
| 5 | Command ID uniqueness (separate pool) | ERROR |
| 6 | Companion elements (ContextMenu, ExtendedTooltip, etc.) | ERROR |
| 7 | DataPath points to an existing attribute (with resolution taken into account) | ERROR |
| 8 | Button CommandName points to an existing command | ERROR |
| 9 | Events have non-empty handler names | ERROR |
| 10 | Commands have Action (handler) | ERROR |
| 11 | No more than one MainAttribute | ERROR |
| 12 | BaseForm: presence and version (when extending) | OK / WARN |
| 13 | callType values: Before, After, Override | ERROR |
| 14 | Extension ID >= 1000000 for added attrs/commands | WARN |
| 15 | callType without BaseForm is invalid structure | WARN |

### Complex DataPath Resolution

Check #7 performs multi-step path resolution before looking up the attribute.

#### 1. Numeric indexes and UUIDs — silent-skip

The platform generates opaque DataPath values that cannot be resolved from Form.xml:

| Form | Example | Action |
|------|---------|--------|
| Numeric index | `10`, `1000003` | Skip without error |
| UUID reference | `1/0:a917a122-f663-4c45-8de0-fd5104007de3` | Skip without error |

Skip pattern: `^\d+$` or `^\d+/\d+:[0-9a-fA-F-]+$`.

> Engineering task: implement this in `xmlgen validate` before segment parsing.

#### 2. `~<Attr>.*` — current row of a dynamic list

The `~` prefix is a shorthand for "current row of the element". It is used together with `DynamicList`:

```
~Список.Ссылка  →  root attribute: Список
```

Algorithm: strip `~`, split by `.`, take the first segment as the attribute name. The remaining segments are fields of the list object itself; the validator does not check their existence (they are resolved by the platform at runtime).

#### 3. `Items.<Table>.CurrentData.*` — field from the current table row

Access to the current row of a form table element through the `Items` collection:

```
Items.Список.CurrentData.Ссылка
```

Resolution algorithm:
1. If the first segment is `Items`, the expected form is `Items.<TableName>.CurrentData.<Field>`.
2. Find the form element with tag `Table` and name `<TableName>`.
   - Not found → ERROR: `table element '<TableName>' not found`.
3. Read the `DataPath` of the found table; strip `[N]` and `~`.
   - A table without `DataPath` (possibly in dynamic forms) is accepted silently, without error.
4. Take the first segment of the resulting path as the root attribute and verify that it exists in `attrMap`.

Other `Items.*` forms (not `Items.<T>.CurrentData`) → WARN: `unknown Items.* shape`.

#### 4. General resolution order (summary)

```
DataPath
  ├─ numeric / UUID  →  silent-skip
  ├─ starts with '~'  →  strip '~', continue to step 5
  ├─ starts with 'Items.'  →  resolve through table element (step 3)
  └─ otherwise
5. Strip indexes [N], take the first segment → verify in attrMap
```

### Output

```
=== Validation: ФормаДокумента ===

[OK]    Root element: Form version=2.17
[OK]    AutoCommandBar: name='ФормаКоманднаяПанель', id=-1
[OK]    Unique element IDs: 96 elements
[OK]    Unique attribute IDs: 38 entries
[OK]    Unique command IDs: 5 entries
[OK]    Companion elements: 86 elements checked
[OK]    DataPath references: 53 paths checked
[OK]    Command references: 2 buttons checked
[OK]    Event handlers: 41 events checked
[OK]    Command actions: 5 commands checked
[OK]    MainAttribute: 1 main attribute

---
Total: 96 elements, 38 attributes, 5 commands
All checks passed.
```

Return code: 0 = all checks passed, 1 = there are errors.

Checks 12-15 are enabled automatically when `<BaseForm>` is detected.

Use after `/form-compile`, `/form-edit`, or manual editing of Form.xml to identify structural errors before EPF build.

---

## epf-validate — External processing validator (EPF/ERF)

Checks the structural correctness of external processing XML sources: root structure, InternalInfo, properties, ChildObjects, attributes, tabular sections, name uniqueness, and the presence of form and layout files.

The script also works for external reports (ERF) - auto-detection is based on the element type.

### Usage

```
/epf-validate <ObjectPath>
```

### Parameters

| Parameter  | Required | Default | Description                                   |
|------------|:--------:|---------|-----------------------------------------------|
| ObjectPath | yes      | —       | Path to the root XML or the processing folder |
| MaxErrors  | no       | 30      | Stop after N errors                           |
| OutFile    | no       | —       | Write the result to a file (UTF-8 BOM)        |

`ObjectPath` auto-resolves: if a directory is specified, it looks for `<dirName>/<dirName>.xml`.

### Command

```bash
xmlgen validate --type epf "<ObjectPath>"
```

With JSON report:
```bash
xmlgen validate --type epf --output json "<ObjectPath>"
```

> Implementation: Java-CLI `xmlgen validate` (replacement for the Python script). Error codes are `EPF-001..006` (structure), `EPF-007..010` (semantics: duplicates, identifiers, Form.xml, GUID).

### Checks Performed

| #  | Check                                               | Severity     |
|----|-----------------------------------------------------|--------------|
| 1  | Root structure: MetaDataObject/ExternalDataProcessor | ERROR        |
| 2  | InternalInfo: ClassId, ContainedObject, GeneratedType | ERROR / WARN |
| 3  | Properties: Name (identifier), Synonym              | ERROR / WARN |
| 4  | ChildObjects: allowed types, order                  | ERROR / WARN |
| 5  | Cross-references: DefaultForm → Form, AuxiliaryForm  | ERROR / WARN |
| 6  | Attributes: UUID, Name, Type                         | ERROR        |
| 7  | TabularSections: UUID, Name, GeneratedType, Attributes | ERROR / WARN |
| 8  | Name uniqueness (Attribute, TS, Form, Template, Command) | ERROR   |
| 9  | Files: forms (.xml + Ext/Form.xml), layouts          | ERROR        |
| 10 | Form descriptors: root structure, uuid, Name, FormType | ERROR / WARN |

### Output

```
=== Validation: EPF.МояОбработка ===

[OK]    1. Root structure: MetaDataObject/ExternalDataProcessor, version 2.17
[OK]    2. InternalInfo: ClassId correct, 1 GeneratedType
[OK]    3. Properties: Name="МояОбработка", Synonym present, DefaultForm set
[OK]    4. ChildObjects: Attribute(3), TabularSection(1), Form(1)
[OK]    5. Cross-references: DefaultForm valid
[OK]    6. Attributes: 3 checked (UUID, Name, Type)
[OK]    7. TabularSections: 1 sections, 5 inner attributes
[OK]    8. Name uniqueness: 6 names, all unique
[OK]    9. File existence: 3 files verified
[OK]    10. Form descriptors: 1 checked

=== Result: 0 errors, 0 warnings ===
```

Return code: 0 = all checks passed, 1 = there are errors.

### When to use

- After `/epf-init`, adding a form/layout, or manual XML editing - to identify structural errors before build.
- During build debugging - to find the cause of a Designer error.

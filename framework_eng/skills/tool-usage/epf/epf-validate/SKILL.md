---
name: epf-validate
description: "Validation of a 1C external processing (EPF). Use after creating or modifying a processing to verify correctness"
argument-hint: <ObjectPath> [-MaxErrors 30]
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /epf-validate — validation of an external processing (EPF)

Checks the structural correctness of the XML sources of an external processing: root structure, InternalInfo, properties, ChildObjects, attributes, tabular sections, unique names, and the presence of form and template files.

The script also works for external reports (ERF) — auto-detection by element type. See `/erf-validate`.

## Usage

```
/epf-validate <ObjectPath>
```

## Parameters

| Parameter  | Required | Default | Description                                   |
|------------|:--------:|---------|-----------------------------------------------|
| ObjectPath | yes      | —       | Path to the root XML or the processing folder |
| MaxErrors  | no       | 30      | Stop after N errors                           |
| OutFile    | no       | —       | Write the result to a file (UTF-8 BOM)        |

`ObjectPath` auto-resolves: if a directory is specified, it looks for `<dirName>/<dirName>.xml`.

## Command

```bash
xmlgen validate --type epf "<ObjectPath>"
```

With JSON report:
```bash
xmlgen validate --type epf --output json "<ObjectPath>"
```

> Implementation: Java-CLI `xmlgen validate` (replacement for the Python script). Error codes are `EPF-001..006` (structure), `EPF-007..010` (semantics: duplicates, identifiers, Form.xml, GUID).

## Checks Performed

| #  | Check                                                   | Severity     |
|----|---------------------------------------------------------|--------------|
| 1  | Root structure: MetaDataObject/ExternalDataProcessor    | ERROR        |
| 2  | InternalInfo: ClassId, ContainedObject, GeneratedType    | ERROR / WARN |
| 3  | Properties: Name (identifier), Synonym                  | ERROR / WARN |
| 4  | ChildObjects: allowed types, order                      | ERROR / WARN |
| 5  | Cross-references: DefaultForm → Form, AuxiliaryForm     | ERROR / WARN |
| 6  | Attributes: UUID, Name, Type                            | ERROR        |
| 7  | TabularSections: UUID, Name, GeneratedType, Attributes  | ERROR / WARN |
| 8  | Name uniqueness (Attribute, TS, Form, Template, Command) | ERROR       |
| 9  | Files: forms (.xml + Ext/Form.xml), templates           | ERROR        |
| 10 | Form descriptors: root structure, uuid, Name, FormType  | ERROR / WARN |

## Output

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

Return code: 0 = all checks passed, 1 = errors are present.

## When to Use

- After `/epf-init`, adding a form/template, or manually editing XML - to detect structural errors before building.
- While debugging a build - to find the cause of a Designer error.

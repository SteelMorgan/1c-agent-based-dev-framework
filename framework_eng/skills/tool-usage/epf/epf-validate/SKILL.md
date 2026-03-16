---
name: epf-validate
description: Validation of the 1C external processing (EPF). Use after creating or modifying the processing to verify correctness
argument-hint: <ObjectPath> [-MaxErrors 30]
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /epf-validate — validation of the external processing (EPF)

Checks the structural correctness of the XML sources of the external processing: root structure, InternalInfo, properties, ChildObjects, attributes, tabular sections, name uniqueness, and the presence of form and layout files.

The script also works for external reports (ERF) — automatic detection by the element type. See `/erf-validate`.

## Usage

```
/epf-validate <ObjectPath>
```

## Parameters

| Parameter   | Required | Default | Description                                      |
|-------------|:--------:|---------|--------------------------------------------------|
| ObjectPath | yes      | —       | Path to the root XML or the processing directory   |
| MaxErrors  | no       | 30      | Stop after N errors                               |
| OutFile    | no       | —       | Write the result to a file (UTF-8 BOM)            |

`ObjectPath` auto-resolve: if a directory is provided, it looks for `<dirName>/<dirName>.xml`.

## Command

```bash
python3 scripts/epf-validate.py -ObjectPath "<path>"
```

## Checks Performed

| #  | Check                                                  | Severity     |
|----|--------------------------------------------------------|--------------|
| 1  | Root structure: MetaDataObject/ExternalDataProcessor   | ERROR        |
| 2  | InternalInfo: ClassId, ContainedObject, GeneratedType  | ERROR / WARN |
| 3  | Properties: Name (identifier), Synonym                 | ERROR / WARN |
| 4  | ChildObjects: allowed types, order                     | ERROR / WARN |
| 5  | Cross-references: DefaultForm → Form, AuxiliaryForm    | ERROR / WARN |
| 6  | Attributes: UUID, Name, Type                           | ERROR        |
| 7  | TabularSections: UUID, Name, GeneratedType, Attributes | ERROR / WARN |
| 8  | Name uniqueness (Attribute, TabularSection, Form, Template, Command) | ERROR |
| 9  | Files: forms (.xml + Ext/Form.xml), layout files       | ERROR        |
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

Return code: 0 = all checks passed, 1 = errors present.

## Verification

```
/epf-init <Name>                   — create the processing
/epf-validate src/<Name>.xml       — check the result
/epf-build <Name>                  — build the EPF
```

## When to use

- **After `/epf-init`**: verify the scaffold
- **After adding a form/layout**: ensure ChildObjects, files, and references are correct
- **After manually editing the XML**: catch structural issues before building
- **When debugging a build**: find the cause of a Designer error

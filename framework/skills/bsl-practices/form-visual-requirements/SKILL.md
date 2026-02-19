---
name: form-visual-requirements
description: Checklist and criteria for verifying 1C form layout and usability. Use this to analyze screenshots from visual-check.
---

# Form Visual Requirements

Use this checklist to verify 1C forms.

## 1. Layout & Alignment

- [ ] **Alignment**: Controls should be aligned to a grid. No "staircase" effect.
- [ ] **Grouping**: Logically related fields should be grouped (Frame, Page).
- [ ] **Whitespace**: No large empty areas (>150px) unless intentional.
- [ ] **Width**:
  - `Code`, `Number`, `Date` fields should be narrow.
  - `Description`, `Comment`, `Address` fields should be wide (stretch).
  - Table columns should use "Auto Width" or explicit width to fill space.

## 2. Controls & Labels

- [ ] **Labels**: All fields must have labels (or explicit "TitleLocation=None").
- [ ] **Truncation**: Labels and values should NOT be truncated with ellipsis ("...") if space permits.
- [ ] **Captions**: Checkbox captions should be clear (e.g., "Active" instead of just a checkbox).
- [ ] **CommandBar**: "More" (Ещё) menu should not hide primary actions.

## 3. Usability

- [ ] **Tab Order**: Focus should move Left-to-Right, Top-to-Bottom.
- [ ] **Primary Fields**: Important identifiers (Name, Code, Date) should be at the Top-Left.
- [ ] **Tables**: Tabular sections should have a reasonable height (min 5-10 rows visible).
- [ ] **Horizontal Scroll**: STRICTLY PROHIBITED for main form area (vertical scroll is OK).

## 4. Specific Object Types

### Catalogs (Справочники)
- Code/Description usually at top.
- Hierarchy parent field (if hierarchical) prominent.

### Documents (Документы)
- Date/Number at top.
- Status/Organization/Warehouse - header.
- Tabular sections - body.
- Totals/Comment/Author - footer.

### Data Processors (Обработки)
- Settings/Parameters - top or separate tab.
- Action buttons - CommandBar or bottom right.

---
name: form-visual-requirements
description: "For visual checks of 1C forms: layout, labels, UX"
alwaysApply: false
---

# Visual Requirements for Forms

Use this checklist to review 1C forms.

Before evaluating the image, check that the PNG is not empty and not single-color/black. How to capture a form screenshot, how to work in Xvfb, and when browser fallback is allowed — see the dedicated skill `va-visual-check`.

## 1. Layout and Alignment

- [ ] **Alignment**: elements are aligned to the grid, without a stair-step effect.
- [ ] **Grouping**: logically related fields are grouped together (frame, page).
- [ ] **Empty spaces**: there are no large empty areas (>150px) unless intended.
- [ ] **Field widths**:
  - `Code`, `Number`, `Date` — narrow.
  - `Description`, `Comment`, `Address` — wide (stretched).
  - Columns of tabular sections — "Auto width" or an explicit width to fill the space.

## 2. Elements and Labels

- [ ] **Labels**: all fields have labels (or `TitleLocation=None` is explicitly specified).
- [ ] **Truncation**: labels and values must not be truncated with an ellipsis ("…") when there is space available.
- [ ] **Checkbox labels**: the checkbox label should be clear (for example, "Active", not just a checkbox).
- [ ] **Command bar**: the "More" menu must not hide primary actions.

## 3. Usability

- [ ] **Tab order**: focus moves left to right and top to bottom.
- [ ] **Key fields**: important identifiers (Name, Code, Date) are in the upper-left corner.
- [ ] **Tabular sections**: reasonable height (at least 5-10 visible rows).
- [ ] **Horizontal scrolling**: strictly forbidden for the main form area (vertical is allowed).

## 4. Specifics by Object Type

### Справочники
- Code/Name are usually at the top.
- The parent field (for hierarchies) should be prominent.

### Документы
- Date/Number are at the top.
- Status/Organization/Warehouse are in the header.
- Tabular sections are in the form body.
- Totals/Comment/Author are at the bottom.

### Обработки
- Settings/parameters are at the top or on a separate tab.
- Action buttons are in the command bar or at the bottom right.

---
depends_on: []
---

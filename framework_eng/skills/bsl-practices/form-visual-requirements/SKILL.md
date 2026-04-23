---
name: form-visual-requirements
description: "Checklist and criteria for checking the layout and usability of 1C forms. Use for analyzing screenshots from visual-check."
---

# Visual requirements for forms

Use this checklist to review 1C forms.

## 1. Layout and alignment

- [ ] **Alignment**: elements are aligned to the grid, without a stair-step effect.
- [ ] **Grouping**: logically related fields are grouped together (frame, page).
- [ ] **Empty spaces**: there are no large empty areas (>150px) unless intentional.
- [ ] **Field widths**:
  - `Code`, `Number`, `Date` — narrow.
  - `Description`, `Comment`, `Address` — wide (expanded).
  - Table section columns — "Auto width" or an explicit width to fill the available space.

## 2. Elements and labels

- [ ] **Labels**: all fields have labels (or `TitleLocation=None` is explicitly set).
- [ ] **Truncation**: labels and values should not be truncated with an ellipsis ("...") when there is space available.
- [ ] **Checkbox labels**: the checkbox label should be clear (for example, "Active", not just a checkbox).
- [ ] **Command bar**: the "More" menu should not hide the primary actions.

## 3. Usability

- [ ] **Tab order**: focus moves from left to right and top to bottom.
- [ ] **Key fields**: important identifiers (Name, Code, Date) are in the upper-left corner.
- [ ] **Table sections**: reasonable height (at least 5–10 visible rows).
- [ ] **Horizontal scrolling**: strictly forbidden for the main form area (vertical scrolling is allowed).

## 4. Object type specifics

### Справочники
- Code/Name are usually at the top.
- The parent field (when hierarchical) should be prominent.

### Документы
- Date/Number are at the top.
- Status/Organization/Warehouse are in the header.
- Table sections are in the body of the form.
- Totals/Comment/Author are at the bottom.

### Обработки
- Settings/parameters are at the top or on a separate tab.
- Action buttons are in the command bar or at the bottom right.

---
depends_on: []
---

---
name: form-visual-requirements
description: "When visually checking 1C forms: layout, labels, UX"
alwaysApply: false
---

# Visual Requirements for Forms

Use this checklist to review 1C forms.

Before evaluating the image, check that the PNG is not empty and not monochrome/black. For how to capture a form screenshot, how to work in Xvfb, and when browser fallback is allowed, see the specialized skill `va-visual-check`.

## 1. Layout and Alignment

- [ ] **Alignment**: elements are aligned to the grid, without a stair-step effect.
- [ ] **Grouping**: logically related fields are combined into groups (frame, page).
- [ ] **Whitespace**: there are no large empty areas (>150px) unless that is intentional.
- [ ] **Field widths**:
  - `Код`, `Номер`, `Дата` — narrow.
  - `Описание`, `Комментарий`, `Адрес` — wide (stretched).
  - Table part columns — "Auto width" or an explicit width to fill the space.

## 2. Elements and Labels

- [ ] **Labels**: all fields have labels (or `TitleLocation=None` is explicitly specified).
- [ ] **Truncation**: labels and values should not be truncated with an ellipsis ("…") when there is room.
- [ ] **Checkbox labels**: a checkbox label should be clear (for example, "Active", not just a checkbox).
- [ ] **Command bar**: the "More" menu should not hide primary actions.

## 3. Usability

- [ ] **Tab order**: focus moves left to right and top to bottom.
- [ ] **Key fields**: important identifiers (Name, Code, Date) are in the upper-left corner.
- [ ] **Table parts**: reasonable height (minimum 5-10 visible rows).
- [ ] **Horizontal scrolling**: strictly forbidden for the main form area (vertical is allowed).
- [ ] **Width by meaning**: short codes, numbers, dates, and flags are not stretched; long comments and addresses are multiline.
- [ ] **Primary action**: the form has no more than one primary command by default, unless the scenario requires several equivalent actions.
- [ ] **Dialogs**: the question explains the consequences; the confirm button names the action (`Удалить строки`, `Провести`, `Записать`), not just `Yes`.
- [ ] **Links and buttons**: hyperlinks open a form/section/help; data actions are performed with buttons.
- [ ] **Checkboxes**: checkbox labels describe the enabled behavior without negations.

## 4. Specifics by object type

### Catalogs
- Code/Description are usually at the top.
- The parent field (for hierarchies) should be prominent.

### Documents
- Date/Number are at the top.
- Status/Organization/Warehouse are in the header.
- Tabular sections are in the body of the form.
- Totals/Comment/Author are at the bottom.
- The tabular section should have a clear title; if the title shows the number of rows, check `ПутьКДаннымЗаголовка`.

### Data processors
- Settings/parameters are at the top or on a separate tab.
- Action buttons are in the command bar or at the bottom right.

## 5. Platform Properties

- [ ] Commands that modify persisted data have `ИзменяетСохраняемыеДанные`.
- [ ] Required attributes use `ПроверкаЗаполнения` if the requirement does not depend on a complex business condition.
- [ ] The list form contains `ГруппаПользовательскихНастроек` if user filters should be visible.
- [ ] In a standard form, new attributes are placed programmatically: on a service tab or in a service group, if the specification does not define an exact location.
- [ ] Element visibility/accessibility looks consistent; in the form module, this should match the centralized update procedure.

---
depends_on: []
---

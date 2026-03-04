---
name: form-visual-requirements
description: Checklist and criteria for verifying the layout and usability of 1С forms. Use it for analyzing screenshots from visual-check.
---

# Visual requirements for forms

Use this checklist for reviewing 1С forms.

## 1. Layout and alignment

- [ ] **Alignment**: elements are aligned to a grid without a "staircase" effect.
- [ ] **Grouping**: logically related fields are grouped (frame, page).
- [ ] **Gaps**: there are no large empty areas (>150px), unless intentionally designed.
- [ ] **Field width**:
  - `Код`, `Номер`, `Дата` — narrow.
  - `Описание`, `Комментарий`, `Адрес` — wide (expanded).
  - Table part columns — "Auto width" or explicit width to fill the space.

## 2. Controls and labels

- [ ] **Labels**: every field has a label (or `TitleLocation=None` is explicitly specified).
- [ ] **Truncation**: labels and values must not be cut off with ellipses ("…") when there is space.
- [ ] **Checkbox captions**: the checkbox label should be clear (e.g., "Активен", not just an unlabeled checkbox).
- [ ] **Command panel**: the "More" menu must not hide primary actions.

## 3. Usability

- [ ] **Tab order**: focus moves from left to right and top to bottom.
- [ ] **Key fields**: important identifiers (Наименование, Код, Дата) are in the upper-left corner.
- [ ] **Table parts**: reasonable height (at least 5–10 visible rows).
- [ ] **Horizontal scrolling**: strictly forbidden for the main form area (vertical scrolling is allowed).

## 4. Object type specifics

### Справочники
- Код/Наименование usually at the top.
- Parent field (when hierarchy exists) should be noticeable.

### Документы
- Дата/Номер at the top.
- Status/Организация/Склад in the header.
- Table parts located in the body of the form.
- Итоги/Комментарий/Автор at the bottom.

### Обработки
- Настройки/параметры at the top or on a separate tab.
- Action buttons located in the command panel or on the right-bottom.

---
depends_on: []
---

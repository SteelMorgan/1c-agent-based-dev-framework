---
name: visual-check
description: Visual verification of 1С forms through the web client and browser automation. Takes a screenshot, checks for JS errors in the console, and analyzes the form using the form-visual-requirements checklist.
---

# Visual verification of forms (Visual Check)

## Purpose

This skill enables visually verifying 1С forms via the web client: open the form in a browser, take a screenshot, check the console for JS errors, and analyze using the `form-visual-requirements` checklist.

---

## Prerequisites

- The 1С web client URL (published database).
- Credentials (login/password).

---

## Verification process

### Step 1. Navigating to the form

Use `browser_navigate` to open the form. Prefer direct links (Deep Linking) as they are faster than navigating through the interface.

**URL patterns:**

- List form: `<base_url>/e1cib/list/<ТипМетаданных>.<Имя>`
  - Example: `http://localhost/ib/e1cib/list/Справочник.Товары`
- Object form (new): `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=00000000-0000-0000-0000-000000000000`
  - Alternative: open the list form and click “Создать”.
- Object form (existing): `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=<UUID>`

### Step 2. Authentication

If the browser redirects to the login page:
1. `browser_snapshot` — collect references to the page elements.
2. `browser_fill` — populate login and password (use the `ref` from the snapshot).
3. `browser_click` — press the “Войти” button.

### Step 3. Screenshot and console check

After the form loads (wait until the loading indicator disappears):
1. `browser_take_screenshot` — capture the current form state.
2. `browser_console_messages` — check for “Error”, “Exception”, “Uncaught”.
3. Save the screenshot as an artifact if needed.

### Step 4. Analysis

Review the screenshot against the **form-visual-requirements** checklist:
- Layout and alignment (alignment, grouping, spacing, width)
- Controls and labels (labels, text truncation, headers, command panel)
- Usability (tab order, key fields, tables, horizontal scrolling)
- Object-specific aspects (справочники, документы, обработки)

**Report:** describe the screenshot analysis results and document whether JS errors were present in the console.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `browser_navigate` | Open the form URL |
| `browser_snapshot` | Capture the page structure and element refs |
| `browser_fill` | Fill fields (login, password) |
| `browser_click` | Click buttons and elements |
| `browser_take_screenshot` | Capture the form |
| `browser_console_messages` | Check for JS errors in the console |
| `browser_wait_for` | Wait for the form to load |

---

## Example

```
# Концептуальный пример рабочего процесса.
# На практике используй ref-ы из browser_snapshot.

# 1. Открыть веб-клиент
browser_navigate(url="http://localhost/ib")

# 2. Авторизация — сначала снимок для получения ref-ов
browser_snapshot()
# Найти ref-ы полей логина и пароля в снимке, затем:
browser_fill(ref="<ref_из_снимка>", value="Администратор")
browser_fill(ref="<ref_из_снимка>", value="пароль")
browser_click(ref="<ref_из_снимка>", element="Кнопка входа")

# 3. Перейти к форме через прямую ссылку
browser_navigate(url="http://localhost/ib/e1cib/list/Справочник.Товары")

# 4. Дождаться загрузки, сделать скриншот и проверить консоль
browser_wait_for(time=2)
browser_take_screenshot(filename="справочник_товары.png")
browser_console_messages()  # Проверить на «Error», «Exception»

# 5. Проанализировать скриншот по form-visual-requirements, зафиксировать JS-ошибки
```

---

## Common pitfalls

| Issue | Workaround |
|-------|------------|
| The form is not fully loaded yet — the screenshot is blank | Use `browser_wait_for` before taking the screenshot; check for the loading indicator |
| Deep Link does not work for a new object | Open the list form → click “Создать” via `browser_click` |
| `browser_fill` cannot locate the field | Use `browser_snapshot` to retrieve current refs |
| JS errors are present but the form looks fine | Document the errors in the report — they may appear during saving |

---
depends_on:
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---

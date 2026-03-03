---
name: visual-check
description: Visual checking of 1С forms through the web client and browser automation. Takes a screenshot, inspects JS errors in the console, and analyzes according to the form-visual-requirements checklist.
---

# Visual check of forms (Visual Check)

## Purpose

This skill lets you visually inspect 1С forms via the web client: open the form in a browser, take a screenshot, check the console for JS errors, and review it against the `form-visual-requirements` checklist.

---

## Prerequisites

- URL of the 1С web client (published database).
- Credentials (login/password).

---

## Verification Process

### Step 1. Navigate to the form

Use `browser_navigate` to open the form. Prefer direct links (Deep Linking) as they are faster than navigating through the UI.

**URL templates:**

- List form: `<base_url>/e1cib/list/<ТипМетаданных>.<Имя>`
  - Example: `http://localhost/ib/e1cib/list/Справочник.Товары`
- Object form (new): `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=00000000-0000-0000-0000-000000000000`
  - Alternative: open the list form and click “Создать”.
- Object form (existing): `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=<UUID>`

### Step 2. Authorization

If the browser redirected you to the login page:
1. `browser_snapshot` — capture the page structure and element refs.
2. `browser_fill` — fill in the login and password fields (use the `ref` values from the snapshot).
3. `browser_click` — click the “Войти” button.

### Step 3. Screenshot and console check

After the form loads (wait for any loading indicator to disappear):
1. `browser_take_screenshot` — capture the current state of the form.
2. `browser_console_messages` — look for “Error”, “Exception”, “Uncaught”.
3. Save the screenshot as an artifact if needed.

### Step 4. Analysis

Review the screenshot according to the **form-visual-requirements** checklist:
- Layout and alignment (alignment, grouping, spacing, width)
- Controls and labels (labels, text clipping, headers, command bar)
- Usability (tab order, key fields, tables, horizontal scrolling)
- Object type specifics (catalogs, documents, processing)

**Report:** state the result of the screenshot analysis and whether JS errors appeared in the console.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `browser_navigate` | Open the form URL |
| `browser_snapshot` | Capture the page structure and element refs |
| `browser_fill` | Populate fields (login, password) |
| `browser_click` | Click buttons and elements |
| `browser_take_screenshot` | Take a snapshot of the form |
| `browser_console_messages` | Check the console for JS errors |
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

## Common Issues

| Issue | Workaround |
|-------|------------|
| The form has not finished loading so the screenshot is empty | Use `browser_wait_for` before taking the screenshot; verify the loading indicator has disappeared |
| Deep Link does not work for a new object | Open the list form → click “Создать” using `browser_click` |
| `browser_fill` cannot find the field | Run `browser_snapshot` to retrieve current refs |
| JS errors are present but the form looks fine | Report the errors — they may surface when saving |

---
depends_on:
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---

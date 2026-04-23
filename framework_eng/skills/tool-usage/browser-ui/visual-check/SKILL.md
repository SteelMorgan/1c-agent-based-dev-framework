---
name: visual-check
description: "Visual check of 1С form via web client and browser automation. Takes a screenshot, checks for JS errors in the console, and analyzes according to the form-visual-requirements checklist."
---

# Visual check of forms (Visual Check)

Required: URL of the 1С web client (published base), credentials.

## Verification process

### 1. Navigating to the form

Prefer Deep Linking — faster than navigation through the interface.

- List: `<base_url>/e1cib/list/<ТипМетаданных>.<Имя>`
- New object: `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=00000000-0000-0000-0000-000000000000`
- Existing object: `<base_url>/e1cib/data/<ТипМетаданных>.<Имя>?ref=<UUID>`

### 2. Authorization (if redirected to login)

`browser_snapshot` → `browser_fill` (login/password by ref) → `browser_click` (Log in).

### 3. Screenshot and console

After loading (wait for the indicator to disappear):
1. `browser_take_screenshot`
2. `browser_console_messages` — look for “Error”, “Exception”, “Uncaught”

### 4. Analysis against the `form-visual-requirements` checklist

- Layout and alignment (grouping, spacing, width)
- Controls and labels (captions, clipping, titles, command panel)
- Usability (tab order, key fields, tables, horizontal scrolling)
- Object-specific characteristics (Справочники, Документы, Обработки)

**Report:** result of the screenshot analysis + presence/absence of JS errors.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `browser_navigate` | Opening the form URL |
| `browser_snapshot` | Page structure and element refs |
| `browser_fill` | Filling fields |
| `browser_click` | Clicking elements |
| `browser_take_screenshot` | Capturing the form |
| `browser_console_messages` | Checking for JS errors |
| `browser_wait_for` | Waiting for loading |

## Typical errors

| Error | Workaround |
|--------|------------|
| Screenshot is blank | `browser_wait_for` before the screenshot |
| Deep Link does not work for a new object | List → “Create” via `browser_click` |
| `browser_fill` cannot find the field | `browser_snapshot` for current refs |
| JS errors on a normal form | Record them — they surface on save |

---
depends_on:
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---

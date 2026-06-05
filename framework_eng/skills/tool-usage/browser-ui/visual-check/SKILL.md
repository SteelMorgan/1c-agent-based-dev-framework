---
name: visual-check
description: "MUST use WHEN a 1C form is created or modified and requires acceptance by the UI checklist. Provides a screenshot via the web client, checks JS errors in the console, and analyzes against form-visual-requirements."
alwaysApply: false
---

# Visual Check of Forms (Visual Check)

A 1C web client URL (published infobase) and credentials are required.

## Verification Process

### 1. Navigate to the Form

Prefer Deep Linking - it is faster than navigating through the interface.

- List: `<base_url>/e1cib/list/<MetadataType>.<Name>`
- New object: `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=00000000-0000-0000-0000-000000000000`
- Existing object: `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=<UUID>`

### 2. Authentication (if redirected to sign-in)

`browser_snapshot` → `browser_fill` (login/password from ref) → `browser_click` (Log in).

### 3. Screenshot and Console

After loading (wait for the indicator to disappear):
1. `browser_take_screenshot`
2. `browser_console_messages` — look for "Error", "Exception", "Uncaught"

### 4. Analysis against `form-visual-requirements`

- Layout and alignment (grouping, padding, width)
- Controls and labels (labels, truncation, headings, command bar)
- Usability (tab order, key fields, tables, horizontal scrolling)
- Object-type specifics (directories, documents, data processors)

**Report:** screenshot analysis result + presence/absence of JS errors.

## Capabilities

| Capability | Purpose |
|------------|---------|
| `browser_navigate` | Open the form URL |
| `browser_snapshot` | Page structure and element refs |
| `browser_fill` | Fill in fields |
| `browser_click` | Click elements |
| `browser_take_screenshot` | Capture the form |
| `browser_console_messages` | Check for JS errors |
| `browser_wait_for` | Wait for loading |

## Typical Issues

| Error | Workaround |
|--------|------------|
| Blank screenshot | `browser_wait_for` before the screenshot |
| Deep Link does not work for a new object | List → "Create" via `browser_click` |
| `browser_fill` cannot find the field | `browser_snapshot` for current refs |
| JS errors on a normal form | Record it - they will surface on save |

---
depends_on:
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---

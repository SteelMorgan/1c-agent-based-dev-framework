---
name: visual-check
description: Perform a visual check of a 1C form using a web client and browser automation. Captures a screenshot for analysis.
---

# Visual Check Skill

This skill allows you to visually inspect 1C forms via the web client.

## Prerequisites

- Active `browser-use` session (or capability to start one).
- 1C Web Client URL (published database).
- Credentials (login/password).

## Workflow

### 1. Navigation

Use `browser_navigate` to open the form. Prefer direct links (Deep Linking) to avoid complex UI navigation.

**URL Patterns:**

- List Form: `<base_url>/e1cib/list/<MetadataType>.<Name>`
  - Example: `http://localhost/ib/e1cib/list/Catalog.Товары`
- Object Form (New): `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=00000000-0000-0000-0000-000000000000`
  - Better: Use UI "Create" button from list form if direct link is tricky.
- Object Form (Existing): `<base_url>/e1cib/data/<MetadataType>.<Name>?ref=<UUID>`

### 2. Login

If redirected to login page:
1. `browser_snapshot` to find inputs.
2. `browser_fill` user/password.
3. `browser_click` "Enter" button.

### 3. Capture

Once the form is loaded (wait for spinner to disappear):
1. `browser_take_screenshot` (viewport is usually enough).
2. Save screenshot as artifact if needed.

### 4. Analysis

Analyze the screenshot using `form-visual-requirements` skill criteria.

## Example

```bash
# 1. Open browser
browser_navigate url="http://localhost/ib"

# 2. Login
browser_fill element="User" value="Admin"
browser_click element="Login"

# 3. Navigate to form
browser_navigate url="http://localhost/ib/e1cib/list/Catalog.Items"

# 4. Screenshot
browser_take_screenshot filename="catalog_items.png"
```

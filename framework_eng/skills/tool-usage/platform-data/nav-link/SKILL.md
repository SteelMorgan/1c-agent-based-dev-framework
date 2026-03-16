---
name: nav-link
description: Working with navigation links (Nav Link). The skill teaches an agent **to parse and construct navigation links** in the e1cib/data/... format — extracting the object type and link, generating links from data.
---

# Working with navigation links (Nav Link)

## Purpose

The skill teaches the agent **to work with navigation links** in the `e1cib/data/...` format — parsing the link to identify the object type and ref, and forming a link from the data. It is used when the user provides a link to an object (counterparty, document) and asks to find related data.

**Principle:** Link e1cib/data/... → `parse_nav_link` → type + link → queries/analysis. Query results → `get_nav_link` → link for replying to the user.

---

## When to use

| Trigger | Action |
|---------|--------|
| The user provides an e1cib/data/... link | `parse_nav_link` — extract the object type and link |
| Need to form a link from query data | `get_nav_link` — object type + link |
| Task “show data for this counterparty/document” | parse_nav_link → execute_query with the parameter from the link |

---

## Usage scenarios

### Scenario 1: Parsing a user link

**Steps:**

1. User: “Show the debt for the counterparty: e1cib/data/Справочник.Контрагенты?ref=...”
2. `parse_nav_link` — extract the type (Справочник.Контрагенты) and ref.
3. Build a query with the parameter from the link.
4. `execute_query` — retrieve the data.

### Scenario 2: Forming a link in the response

**Steps:**

1. `execute_query` returned a row with a link field.
2. `get_nav_link` — form a navigation link from the object type and link value.
3. Return the link to the user in a convenient format.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `parse_nav_link` | Parsing a navigation link (e1cib/data/...) — extracting the object type and link |
| `get_nav_link` | Forming a navigation link from data (object type + link) |

---
depends_on: []
---

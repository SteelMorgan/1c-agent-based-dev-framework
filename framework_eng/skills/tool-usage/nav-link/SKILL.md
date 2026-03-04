---
name: nav-link
description: Working with navigation links (Nav Link). The skill teaches the agent to **parse and construct navigation links** of the format e1cib/data/... — extracting the object type and link, and generating links from data.
---

# Working with navigation links (Nav Link)

## Purpose

The skill teaches the agent to **work with navigation links** of the format `e1cib/data/...` — parsing the link to determine the object type and reference, and composing a link from the data. It is used when the user provides a link to an object (counterparty, document) and asks to find related data.

**Principle:** Link e1cib/data/... → `parse_nav_link` → type + reference → queries/analysis. Query results → `get_nav_link` → link for the user response.

---

## When to apply

| Trigger | Action |
|---------|----------|
| The user provides an e1cib/data/... link | `parse_nav_link` — extract the object type and reference |
| A link needs to be generated from query data | `get_nav_link` — object type + reference |
| Task “show data for this counterparty/document” | parse_nav_link → execute_query with the reference as a parameter |

---

## Usage scenarios

### Scenario 1: Parsing a link from the user

**Steps:**

1. User: “Show the debt for the counterparty: e1cib/data/Справочник.Контрагенты?ref=...”
2. `parse_nav_link` — extract the type (Справочник.Контрагенты) and ref.
3. Build a query with the reference parameter.
4. `execute_query` — obtain the data.

### Scenario 2: Generating a link in the response

**Steps:**

1. `execute_query` returned a row with a reference field.
2. `get_nav_link` — create a navigation link based on the object type and reference value.
3. Return the link to the user in a convenient format.

---

## Capabilities

| Capability | Purpose |
|------------|------------|
| `parse_nav_link` | Parsing the navigation link (e1cib/data/...) — extracting the object type and reference |
| `get_nav_link` | Generating a navigation link from data (object type + reference) |

---
depends_on: []
---

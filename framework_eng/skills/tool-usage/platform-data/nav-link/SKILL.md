---
name: nav-link
description: "Working with navigation links (Nav Link). The skill teaches an agent **to parse and construct navigation links** in the e1cib/data/... format — extracting the object type and link, generating links from data."
---

# Working with navigation links (Nav Link)

## When to apply

| Trigger | Action |
|---------|----------|
| The user gives an e1cib/data/... link | `parse_nav_link` → type + link → queries/analysis |
| Need to build a link from query data | `get_nav_link(type, link)` → link for response |

## Algorithm

- **Incoming link:** `parse_nav_link` → extract type and ref → build query → `execute_query`.
- **Outgoing link:** `execute_query` returned a link → `get_nav_link(type, link)` → return to the user.

## Capabilities

| Capability | Purpose |
|------------|------------|
| `parse_nav_link` | Parsing e1cib/data/... — extracting the object type and link |
| `get_nav_link` | Forming a navigation link from type + link |

---
depends_on: []
---

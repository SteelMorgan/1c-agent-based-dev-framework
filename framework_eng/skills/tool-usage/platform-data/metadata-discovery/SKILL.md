---
name: metadata-discovery
description: "Working with metadata (Metadata Discovery). The skill teaches the agent **to work with 1C configuration metadata** — to find objects (catalogs, documents, registers, processors) and understand their structure and relationships."
---

# Working with metadata (Metadata Discovery)

**Principle:** Before working with business logic — explore the metadata. Do not create objects “at random” unless the user explicitly asks for them.

## Tools

| Tool | Parameters | Purpose |
|------|------------|---------|
| `list_metadata_objects` | metaType, nameMask, maxItems | Search for objects by type and name mask |
| `get_metadata_structure` | metaType, name | Structure: attributes, tabular sections, dimensions, resources |
| `navigate_symbol` | — | Jump to the modules or procedures of the found objects |
| `get_call_graph` | — | Analyze the call chains inside the modules |

## Workflow

1. **Find the object:** `list_metadata_objects(metaType, nameMask)` — verify existence and type. For fuzzy searches use `nameMask: "*Nomenclature*"`.
2. **Get the structure:** `get_metadata_structure(metaType, name)` — attributes, tabular sections, dimensions, resources. Do this before building a query.
3. **Code analysis (if needed):** `navigate_symbol` → `get_call_graph`.
4. **Creating a new object:** the agent DOES NOT create metadata objects automatically. Procedure: check that it does not exist → describe it to the user (name, attributes, tabular sections) → the user creates it in the Configurator/EDT → run `get_metadata_structure` for verification.

## Common mistakes

| Mistake | Solution |
|---------|----------|
| Searching by a generic word returns too many results | Narrow the nameMask; specify the metaType; reduce maxItems |
| The agent tries to create the object itself | Protocol: the agent describes it, the user creates it in the Configurator/EDT |

---
depends_on: []
---

---
name: metadata-discovery
description: Working with metadata (Metadata Discovery). The skill teaches an agent to **work with metadata of the 1С configuration** — to find objects (справочники, документы, регистры, обработки), understand their structure and relationships.
---

# Working with metadata (Metadata Discovery)

## Purpose

The skill teaches an agent to **work with metadata of the 1С configuration** — to find objects (справочники, документы, регистры, обработки), understand their structure and relationships. Metadata is the foundation of any development in 1C:Enterprise — code always relies on existing configuration objects.

**Principle:** Before working with business logic, explore the metadata. Do not create objects “at random” unless the user explicitly requests it.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The task is related to a specific object (справочник, документ) | `list_metadata_objects` — verify existence; `get_metadata_structure` — inspect structure |
| Need to find “all documents related to Номенклатура” | `list_metadata_objects` by metaType + nameMask + analyze attributes via `get_metadata_structure` |
| Checking the existence of a register (for example, ОстаткиТоваров) | `list_metadata_objects` with metaType: "РегистрНакопления", nameMask |
| The user asks to add an attribute | First `get_metadata_structure` — study the object, attributes, tabular sections |
| Before building a query — unsure about table/field names | `get_metadata_structure` — get dimensions, resources, attributes |
| Analyzing dependencies between objects | `list_metadata_objects` + `navigate_symbol` to jump to modules |
| Creating a new metadata object | See the “agent ↔ user” protocol — user involvement is required |

---

## Usage scenarios

### Scenario 1: Finding an object by name

**Steps:**

1. `list_metadata_objects` with `metaType: "Справочник"`, `nameMask: "Номенклатура"` (or `"*Номенклатура*"` for fuzzy search).
2. Analyze the results — full name, type.
3. If necessary, refine the type: `metaType: "Документ"`, `"РегистрНакопления"`, etc.

**Example:** Check the existence of the „ОстаткиТоваров” register.

```
list_metadata_objects(metaType: "РегистрНакопления", nameMask: "ОстаткиТоваров", maxItems: 10)
```

### Scenario 2: Finding objects by pattern

**Steps:**

1. `list_metadata_objects` with metaType and nameMask (for example, metaType: "Документ", nameMask: "*Номенклатура*").
2. Filter the results as needed.
3. For detailed analysis — `get_metadata_structure` for the found object.
4. To jump to the code — `navigate_symbol` by the object name.

**Example:** Find all documents related to Номенклатура.

```
1. list_metadata_objects(metaType: "Справочник", nameMask: "Номенклатура")
2. list_metadata_objects(metaType: "Документ", nameMask: "*", maxItems: 50)
3. get_metadata_structure(metaType: "Документ", name: "РеализацияТоваровУслуг") — check attributes of type СправочникСсылка.Номенклатура
```

### Scenario 3: Getting structure before building a query

**Steps:**

1. `list_metadata_objects` — find the object (if the name is unknown).
2. `get_metadata_structure` — get attributes, tabular sections, dimensions, resources.
3. Build the query based on the exact field names.

**Example:** Write a query against a balance register.

```
1. list_metadata_objects(metaType: "РегистрНакопления", nameMask: "Остатки*")
2. get_metadata_structure(metaType: "РегистрНакопления", name: "ОстаткиТоваров")
3. Use the dimensions and resources from the structure in the query text
```

### Scenario 4: Finding an object → analyzing the code

**Steps:**

1. `list_metadata_objects` — find the object (for example, Справочник.Контрагенты).
2. `navigate_symbol` — find the object module, procedures, functions.
3. `get_call_graph` — understand the call chains (optional).
4. Based on the analysis — propose a solution or write code.

### Scenario 5: Creating a new object — protocol with the user

**Important:** Creating metadata objects (new справочник, документ, регистр) is **not performed automatically** by the agent. This requires actions in the Configurator or EDT.

**Steps:**

1. `list_metadata_objects` — make sure the object does not exist.
2. The agent drafts a description: what to create, which attributes, tabular sections.
3. The agent tells the user: “Create object X in the Configurator with the following attributes: …”.
4. The user creates the object manually.
5. After creation — `list_metadata_objects` and `get_metadata_structure` for verification, then work with the modules.

---

## Tool parameters

| Tool | Parameters | Purpose |
|------|------------|---------|
| `list_metadata_objects` | metaType, nameMask, maxItems | Search for objects by type and name mask. Call before `execute_query` if the exact object name is unknown. |
| `get_metadata_structure` | metaType, name | The object structure: attributes, tabular sections, dimensions, resources. Mandatory before building a query if you are unsure of field names. |

---

## Understanding metadata structure

| Element | Description |
|---------|-------------|
| full_name | Full name: `Справочник.Номенклатура`, `Документ.РеализацияТоваровУслуг` |
| type | Object type: Справочник, Документ, РегистрСведений, РегистрНакопления, Обработка, etc. |
| attributes | Object attributes (from `get_metadata_structure`) |
| tabular_sections | Tabular sections (from `get_metadata_structure`) |
| dimensions, resources | Dimensions and resources of registers (from `get_metadata_structure`) |

**Hierarchy:** Справочники may have hierarchies. Документы have periodicity and posting. Регистры involve types, dimensions, and resources.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `list_metadata_objects` | Search for metadata objects by type and name mask |
| `get_metadata_structure` | Obtain the object structure (attributes, tabular sections, dimensions, resources) |
| `navigate_symbol` | Jump to the modules and procedures of discovered objects |
| `get_call_graph` | Analyze call chains inside the modules |
| `dump_config` | Dump the configuration (may be performed at the user’s request) |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|---------|------------|
| Searching by a general word yields too many results | Narrow the nameMask; use metaType for filtering; reduce maxItems. |
| Need to create an object — the agent tries to do it itself | Follow the protocol: the agent describes the object, the user creates it in the Configurator/EDT. |
| Tools are unavailable | Capabilities depend on the provider; fallback — `navigate_symbol` by known object names. |
| It is unclear how objects are connected | Combine with `navigate_symbol` and `get_call_graph` — code analysis will show the relationships. |

---
depends_on: []
---

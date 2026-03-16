---
name: metadata-discovery
description: Working with metadata (Metadata Discovery). The skill teaches the agent **to work with 1С configuration metadata** — locate objects (справочники, документы, регистры, обработки), understand their structure and relationships.
---

# Working with metadata (Metadata Discovery)

## Purpose

The skill teaches the agent **to work with 1С configuration metadata** — locate objects (справочники, документы, регистры, обработки), understand their structure and relationships. Metadata is the foundation of any development in 1С:Enterprise, and code always relies on existing configuration objects.

**Principle:** Before dealing with business logic — study the metadata. Do not create objects “at random” unless the user explicitly requests it.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The task is related to a specific object (справочник, документ) | `list_metadata_objects` — check existence; `get_metadata_structure` — inspect the structure |
| Need to find “all documents related to Номенклатура” | `list_metadata_objects` by metaType + nameMask + analyze requisites through `get_metadata_structure` |
| Checking for the presence of a register (for example, ОстаткиТоваров) | `list_metadata_objects` with metaType: "РегистрНакопления", nameMask |
| User asks to add a реквизит | First `get_metadata_structure` — study the object, реквизиты, табличные части |
| Before building a query — unsure about table or field names | `get_metadata_structure` — retrieve dimensions, resources, and реквизиты |
| Analyzing dependencies between objects | `list_metadata_objects` + `navigate_symbol` to jump to modules |
| Creating a new metadata object | See the “agent ↔ user” protocol — user involvement is required |

---

## Use cases

### Scenario 1: Finding an object by name

**Steps:**

1. `list_metadata_objects` with `metaType: "Справочник"`, `nameMask: "Номенклатура"` (or `"*Номенклатура*"` for a fuzzy match).
2. Analyze the results — full name, type.
3. If necessary, narrow down the type: `metaType: "Документ"`, `"РегистрНакопления"`, etc.

**Example:** Verify that the register «ОстаткиТоваров» exists.

```
list_metadata_objects(metaType: "РегистрНакопления", nameMask: "ОстаткиТоваров", maxItems: 10)
```

### Scenario 2: Finding objects by pattern

**Steps:**

1. `list_metadata_objects` with metaType and nameMask (for example, metaType: "Документ", nameMask: "*Номенклатура*").
2. Filter the results as needed.
3. For a detailed analysis — `get_metadata_structure` for the chosen object.
4. To jump into the code — `navigate_symbol` by the object name.

**Example:** Locate all documents connected to номенклатура.

```
1. list_metadata_objects(metaType: "Справочник", nameMask: "Номенклатура")
2. list_metadata_objects(metaType: "Документ", nameMask: "*", maxItems: 50)
3. get_metadata_structure(metaType: "Документ", name: "РеализацияТоваровУслуг") — check реквизиты of type СправочникСсылка.Номенклатура
```

### Scenario 3: Getting the structure before a query

**Steps:**

1. `list_metadata_objects` — find the object (if the name is unknown).
2. `get_metadata_structure` — retrieve реквизиты, табличные части, измерения, ресурсы.
3. Build the query relying on the exact field names.

**Example:** Write a query against a остатки register.

```
1. list_metadata_objects(metaType: "РегистрНакопления", nameMask: "Остатки*")
2. get_metadata_structure(metaType: "РегистрНакопления", name: "ОстаткиТоваров")
3. Use the dimensions and resources from the structure in the query text
```

### Scenario 4: Finding an object → analyzing the code

**Steps:**

1. `list_metadata_objects` — locate the object (for example, Справочник.Контрагенты).
2. `navigate_symbol` — find the object module, procedures, functions.
3. `get_call_graph` — understand call chains (optional).
4. Based on the analysis — propose a solution or write code.

### Scenario 5: Creating a new object — protocol with the user

**Important:** Creating metadata objects (a new справочник, документ, регистр) **is not done automatically** by the agent. This requires actions in Конфигуратор or EDT.

**Steps:**

1. `list_metadata_objects` — make sure the object does not already exist.
2. The agent composes a specification: what to create, which реквизиты, табличные части.
3. The agent tells the user: “Create object X in Конфигуратор with the following реквизиты: …”.
4. The user creates the object manually.
5. After creation — `list_metadata_objects` and `get_metadata_structure` to verify, then work with the modules.

---

## Tool parameters

| Tool | Parameters | Purpose |
|------|------------|---------|
| `list_metadata_objects` | metaType, nameMask, maxItems | Discover objects by type and name mask. Call before `execute_query` when the exact object name is unknown. |
| `get_metadata_structure` | metaType, name | Object structure: реквизиты, табличные части, измерения, ресурсы. Required before composing a query if you are unsure about field names. |

---

## Understanding metadata structure

| Element | Description |
|---------|-------------|
| full_name | Full name: `Справочник.Номенклатура`, `Документ.РеализацияТоваровУслуг` |
| type | Object type: Справочник, Документ, РегистрСведений, РегистрНакопления, Обработка, etc. |
| attributes | Object реквизиты (from `get_metadata_structure`) |
| tabular_sections | Табличные части (from `get_metadata_structure`) |
| dimensions, resources | Register измерения and ресурсы (from `get_metadata_structure`) |

**Hierarchy:** Справочники can have hierarchies. Документы — periodicity, posting. Registers — kind, dimensions, resources.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `list_metadata_objects` | Search for metadata objects by type and name mask |
| `get_metadata_structure` | Retrieve the object structure (реквизиты, табличные части, измерения, ресурсы) |
| `navigate_symbol` | Jump to modules, procedures, and functions of found objects |
| `get_call_graph` | Analyze call chains in modules |
| `dump_config` | Dump the configuration (can be executed on user request) |

---

## Common mistakes and workarounds

| Mistake | Workaround |
|--------|------------|
| Searching by a generic word returns too many results | Narrow the nameMask; filter by metaType; reduce maxItems. |
| Need to create an object — the agent tries to do it itself | Follow the protocol: the agent describes the object, the user creates it in Конфигуратор/EDT. |
| Tools are unavailable | Capabilities depend on the provider; fallback — use `navigate_symbol` by known object names. |
| It is unclear how the objects are connected | Combine with `navigate_symbol` and `get_call_graph` — analyzing the code reveals the relationships. |

---
depends_on: []
---

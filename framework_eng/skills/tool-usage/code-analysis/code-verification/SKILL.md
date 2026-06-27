---
name: code-verification
description: "After BSL changes: LSP, Buddy/API, syntax checks"
uses_capabilities:
  - get_diagnostics
  - ask_ai_assistant
  - search_syntax_reference
  - getMembers
  - getMember
  - getConstructors
  - get_hover_info
alwaysApply: false
---

# Code Verification

This skill describes **the sequence for verifying BSL code after changes**.
Three verification layers, each catching its own class of errors.

## When to Use

| Trigger | Action |
|---------|----------|
| After modifying BSL code | Full cycle (all 3 layers) |
| Reviewing someone else's code | Layer 2 + Layer 3 |
| User asks "check syntax" | Full cycle |

## Verification Layers

### Layer 1 - LSP diagnostics (fast)

Goal: immediate feedback on the changed file.

1. `get_diagnostics(uri)` — get errors/warnings from the BSL Language Server.
2. If there is an `error`-level issue, fix it before moving to layer 2.
3. If LSP is unavailable, move to layer 2 and note that the LSP check was skipped.

### Layer 2 - Buddy (VALIDATE_BSL)

Goal: syntax validation, standards check, search for analogs in БСП.

**What to pass:** the complete procedures/functions that were modified.
Not fragments, not individual lines - full method bodies.

**Call:** `ask_ai_assistant` with the VALIDATE_BSL template from buddy-prompting.

**How to interpret the result:**

| Situation | Action |
|----------|----------|
| Buddy found errors | Analyze each one. Filter false "undeclared variable" reports for global methods. Fix or justify the rest. |
| Buddy found no errors | **DO NOT treat this as proof of correctness.** Buddy has limited context - it cannot see the project. Move to layer 3. |
| Buddy recommends replacing with a БСП function | Verify via `search_ssl_functions` that the recommended function exists. |

### Layer 3 - Platform API verification

Goal: confirm that every platform object, method, property, and constructor used in the code **really exists** on the specified type.

**Algorithm:**

1. Extract all platform API references from the changed code:
   - `New <Type>(...)` - object creation
   - `<Object>.<Method>(...)` - method calls
   - `<Object>.<Property>` - property reads/writes
   - References to managers, enumerations, predefined values

2. Verify each reference:

   | What is in the code | How to check | Capability |
   |------------|---------------|------------|
   | `New <Type>` | Does the type exist? | `search_syntax_reference` → `get_type_info` |
   | `New <Type>(arg1, arg2)` | Constructor with these parameters? | `getConstructors` |
   | `Object.Method()` | Does the method exist on this type? | `getMember` |
   | `Object.Property` | Does the property exist on this type? | `getMember` |
   | Type is unclear | Search by name | `search_syntax_reference` → `getMembers` |
   | Variable/expression type is unknown | Get the type of the value under the cursor | `get_hover_info` |

3. **Special attention to collection types.** The APIs of `Структура`, `Соответствие`, `ТаблицаЗначений`, `Массив` differ. Do not assume the same methods - verify on the specific type.

4. **Determining a variable type.** When it is unclear which type a variable has (and therefore which API is allowed on it), `get_hover_info(uri, line, character)` on the variable name returns the inferred BSL LS value type (Type System v2). This is the type inference for the **specific value** at this point, not help for all platform types. Then verify members of the resulting type through `getMember`/`getMembers`. If `get_hover_info` is unavailable, determine the type from the declaration/assignment site via `navigate_symbol`.

## Trust Hierarchy

```
v8-runner syntax …  (compiler)   ← formal check, final verdict
  > get_diagnostics (LSP)          ← quick diagnostics
    > bsl-platform-context         ← authoritative API reference
      > ask_ai_assistant           ← advisory voice (do not trust absence of errors)
```

When there is a mismatch, the source higher in the hierarchy wins.

## Report Format

As a result of the check, provide a structured output:

1. **LSP:** errors / warnings (or "LSP clean / unavailable").
2. **Buddy:** found issues + recommendations (or "no remarks - BUT this is not a guarantee").
3. **Platform API:** confirmed / unconfirmed references.
4. **Final:** the type of each problem - `syntax` / `API error` / `standard violation` / `runtime risk`.

## Errors and Limitations

| Problem | Workaround |
|----------|------------|
| LSP unavailable | Skip layer 1, move to layer 2. Note it in the report. |
| Buddy unavailable | Skip layer 2. Strengthen layer 3. |
| `bsl-platform-context` does not know the type | Type from the project (not a platform type) - verify via `navigate_symbol`. |
| False "undeclared variable" from Buddy | Normal for global methods — filter it out. |
| `search_syntax_reference` is empty | Clarify the type name (Russian/English spelling), check the version. |
| Buddy recommends a non-existent БСП function | Verify via `search_ssl_functions`. |

## Capabilities

| Capability | Layer | Purpose |
|------------|------|------------|
| `get_diagnostics` | 1 | LSP diagnostics for the file |
| `ask_ai_assistant` | 2 | VALIDATE_BSL through Buddy |
| `search_ssl_functions` | 2 | Checking БСП recommendations |
| `search_syntax_reference` | 3 | Searching for a platform type |
| `get_type_info` | 3 | Type information |
| `getMembers` | 3 | List of type members |
| `getMember` | 3 | Checking a specific member |
| `getConstructors` | 3 | Checking a constructor |
| `get_hover_info` | 3 | Inferred type of a variable/expression value under the cursor |
| `navigate_symbol` | 3 | Determining a variable type from its declaration (fallback to `get_hover_info`) |
| `v8-runner syntax …` | * | Final compiler check (CLI; see the `v8-runner` skill) |

---
depends_on: [syntax-checking, buddy-prompting, code-navigation]
---

---
name: code-verification
description: "After BSL edits: LSP, Buddy/API, syntax checks"
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

# Code Verification (Code Verification)

This skill describes the **procedure for checking BSL code after changes are made**.
Three verification layers, each catching its own class of errors.

## When to Apply

| Trigger | Action |
|---------|----------|
| After modifying BSL code | Full cycle (all 3 layers) |
| Reviewing someone else's code | Layer 2 + Layer 3 |
| User question "check syntax" | Full cycle |

## Verification Layers

### Layer 1 — LSP Diagnostics (fast)

Goal: immediate feedback on the modified file.

1. `get_diagnostics(uri)` — get errors/warnings from BSL Language Server.
2. If there is an `error` level — fix it before moving to layer 2.
3. If LSP is unavailable — move to layer 2, noting that the LSP check was skipped.

### Layer 2 — Buddy (VALIDATE_BSL)

Goal: syntax check, standards, and searching for analogs in БСП.

**What to pass:** entire procedures/functions that were modified.
Not fragments, not individual lines — full method bodies.

**Call:** `ask_ai_assistant` with the VALIDATE_BSL template from buddy-prompting.

**How to interpret the result:**

| Situation | Action |
|----------|----------|
| Buddy found errors | Analyze each one. Filter out false "undeclared variable" reports for global methods. Fix the rest or justify them. |
| Buddy found no errors | **DO NOT treat this as proof of correctness.** Buddy has limited context — it does not see the project. Move to layer 3. |
| Buddy recommends a replacement from БСП | Check via `search_ssl_functions` that the recommended function exists. |

### Layer 3 — Platform API Verification

Goal: confirm that every platform object, method, property, and constructor used in the code **really exists** on the specified type.

**Algorithm:**

1. Extract all platform API references from the modified code:
   - `Новый <Type>(...)` — object creation
   - `<Object>.<Method>(...)` — method calls
   - `<Object>.<Property>` — property reads/writes
   - References to managers, enumerations, predefined values

2. Verify each reference:

   | What is in the code | How to check | Capability |
   |------------|---------------|------------|
   | `Новый <Type>` | Does the type exist? | `search_syntax_reference` → `get_type_info` |
   | `Новый <Type>(param1, param2)` | Constructor with these parameters? | `getConstructors` |
   | `Object.Method()` | Does the method exist on this type? | `getMember` |
   | `Object.Property` | Does the property exist on this type? | `getMember` |
   | Type is unclear | Search by name | `search_syntax_reference` → `getMembers` |
   | Variable/expression type unknown | Output the type of the value under the cursor | `get_hover_info` |

3. **Special attention for collection types.** The APIs of `Структура`, `Соответствие`, `ТаблицаЗначений`, `Массив` differ. Do not assume the same methods - verify against the specific type.

4. **Determining a variable's type.** When it is unclear what type a variable has (and therefore what API is allowed on it), `get_hover_info(uri, line, character)` on the variable name returns the inferred BSL LS value type (Type System v2). This is the inferred type of the **specific value** at that point, not help for all platform types. Then verify members of the resulting type via `getMember`/`getMembers`. If `get_hover_info` is unavailable, determine the type from the declaration/assignment site via `navigate_symbol`.

## Trust Hierarchy

```
v8-runner syntax …  (компилятор)   ← формальная проверка, финальный вердикт
  > get_diagnostics (LSP)          ← быстрая диагностика
    > bsl-platform-context         ← авторитетный справочник API
      > ask_ai_assistant           ← совещательный голос (не доверять отсутствию ошибок)
```

When results differ, the source higher in the hierarchy wins.

## Report Format

At the end of verification, provide a structured report:

1. **LSP:** errors / warnings (or "LSP clean / unavailable").
2. **Buddy:** found issues + recommendations (or "no remarks - BUT this is not a guarantee").
3. **Platform API:** confirmed / unconfirmed references.
4. **Conclusion:** type of each issue - `syntax` / `API error` / `standard violation` / `runtime risk`.

## Errors and Limitations

| Problem | Workaround |
|----------|---------------|
| LSP unavailable | Skip layer 1, move to layer 2. Note it in the report. |
| Buddy unavailable | Skip layer 2. Strengthen layer 3. |
| `bsl-platform-context` does not know the type | Type from the project (not a platform type) - check via `navigate_symbol`. |
| False "undeclared variable" from Buddy | Normal for global methods - filter it out. |
| `search_syntax_reference` is empty | Clarify the type name (Russian/English spelling), check the version. |
| Buddy recommends a non-existent БСП function | Check via `search_ssl_functions`. |

## Capabilities

| Capability | Layer | Purpose |
|------------|------|------------|
| `get_diagnostics` | 1 | File LSP diagnostics |
| `ask_ai_assistant` | 2 | VALIDATE_BSL via Buddy |
| `search_ssl_functions` | 2 | Checking БСП recommendations |
| `search_syntax_reference` | 3 | Platform type lookup |
| `get_type_info` | 3 | Type information |
| `getMembers` | 3 | List of type members |
| `getMember` | 3 | Check a specific member |
| `getConstructors` | 3 | Check constructor |
| `get_hover_info` | 3 | Type of a variable/expression value under the cursor |
| `navigate_symbol` | 3 | Determine a variable's type by declaration (fallback to `get_hover_info`) |
| `v8-runner syntax …` | * | Final compiler check (CLI; see the `v8-runner` skill) |

---
depends_on: [syntax-checking, buddy-prompting, code-navigation]
---

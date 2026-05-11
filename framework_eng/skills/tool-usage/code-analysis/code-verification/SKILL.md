---
name: code-verification
description: "Comprehensive BSL code verification after edits. Orchestrates LSP diagnostics, validation through Buddy (VALIDATE_BSL), and platform API verification through bsl-platform-context."
uses_capabilities:
  - get_diagnostics
  - ask_ai_assistant
  - search_syntax_reference
  - getMembers
  - getMember
  - getConstructors
---

# Code Verification

This skill describes the **procedure for checking BSL code after changes are made**.
Three verification layers, each catches its own class of errors.

## When to use

| Trigger | Action |
|---------|----------|
| After changing BSL code | Full cycle (all 3 layers) |
| Reviewing someone else's code | Layer 2 + Layer 3 |
| User question "check the syntax" | Full cycle |

## Verification Layers

### Layer 1 — LSP diagnostics (fast)

Goal: immediate feedback on the modified file.

1. `get_diagnostics(uri)` — get errors/warnings from BSL Language Server.
2. If there is an `error` level issue — fix it before moving to layer 2.
3. If LSP is unavailable — proceed to layer 2, noting that the LSP check was skipped.

### Layer 2 — Buddy (VALIDATE_BSL)

Goal: syntax check, standards check, search for analogs in BСП.

**What to pass:** entire procedures/functions that were changed.
Not fragments, not individual lines — full method bodies.

**Call:** `ask_ai_assistant` with the VALIDATE_BSL template from buddy-prompting.

**How to interpret the result:**

| Situation | Action |
|----------|----------|
| Buddy found errors | Analyze each one. Filter out false "undeclared variable" reports for global methods. Fix the rest or justify them. |
| Buddy found no errors | **DO NOT treat this as proof of correctness.** Buddy has limited context — it does not see the project. Proceed to layer 3. |
| Buddy recommends a replacement from BСП | Verify through `search_ssl_functions` that the recommended function exists. |

### Layer 3 — Platform API verification

Goal: confirm that every platform object, method, property, and constructor used in the code **actually exists** on the specified type.

**Algorithm:**

1. Extract all platform API references from the changed code:
   - `New <Type>(...)` — object creation
   - `<Object>.<Method>(...)` — method calls
   - `<Object>.<Property>` — property reads/writes
   - References to managers, enumerations, predefined values

2. Verify each reference:

   | What is in code | How to check | Capability |
   |------------|---------------|------------|
   | `New <Type>` | Does the type exist? | `search_syntax_reference` → `get_type_info` |
   | `New <Type>(param1, param2)` | Constructor with such parameters? | `getConstructors` |
   | `Object.Method()` | Does the method exist on this type? | `getMember` |
   | `Object.Property` | Does the property exist on this type? | `getMember` |
   | Type is unclear | Search by name | `search_syntax_reference` → `getMembers` |

3. **Special attention to collection types.** The APIs of `Structure`, `Map`, `ValueTable`, `Array` differ. Do not assume the same methods — verify against the specific type.

4. If `navigate_symbol` is available — use it to determine a variable's type at the declaration/assignment site, then verify the API through platform-context.

## Trust hierarchy

```
v8-runner syntax …  (compiler)   ← formal check, final verdict
  > get_diagnostics (LSP)          ← fast diagnostics
    > bsl-platform-context         ← authoritative API reference
      > ask_ai_assistant           ← advisory voice (do not trust absence of errors)
```

When results differ, the source higher in the hierarchy wins.

## Report format

At the end of the check, provide a structured output:

1. **LSP:** errors / warnings (or "LSP clean / unavailable").
2. **Buddy:** found issues + recommendations (or "no remarks — BUT this is not a guarantee").
3. **Platform API:** confirmed / unconfirmed references.
4. **Result:** type of each issue — `syntax` / `API error` / `standard violation` / `runtime risk`.

## Errors and limitations

| Problem | Workaround |
|----------|------------|
| LSP unavailable | Skip layer 1, proceed to layer 2. Note it in the report. |
| Buddy unavailable | Skip layer 2. Strengthen layer 3. |
| `bsl-platform-context` does not know the type | Type from the project (not a platform type) — check through `navigate_symbol`. |
| False "undeclared variable" from Buddy | Normal for global methods — filter it out. |
| `search_syntax_reference` returns nothing | Clarify the type name (Russian/English spelling), check the version. |
| Buddy recommends a non-existent BСП function | Verify through `search_ssl_functions`. |

## Capabilities

| Capability | Layer | Purpose |
|------------|------|------------|
| `get_diagnostics` | 1 | LSP diagnostics for the file |
| `ask_ai_assistant` | 2 | VALIDATE_BSL through Buddy |
| `search_ssl_functions` | 2 | Checking BСП recommendations |
| `search_syntax_reference` | 3 | Searching for a platform type |
| `get_type_info` | 3 | Type information |
| `getMembers` | 3 | List of type members |
| `getMember` | 3 | Checking a specific member |
| `getConstructors` | 3 | Checking a constructor |
| `navigate_symbol` | 3 | Determining a variable's type |
| `v8-runner syntax …` | * | Final compiler check (CLI; see the `v8-runner` skill) |

---
depends_on: [syntax-checking, buddy-prompting, code-navigation]
---

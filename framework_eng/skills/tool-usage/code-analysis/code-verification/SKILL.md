---
name: code-verification
description: "Comprehensive verification of BSL code after changes. Orchestrates LSP diagnostics, a check through Buddy (VALIDATE_BSL), and validation of the platform API via bsl-platform-context."
---

# Code Verification (Code Verification)

The skill describes the **process for checking BSL code after edits**.
Three layers of checks, each catching its own class of errors.

## When to apply

| Trigger | Action |
|---------|--------|
| After changing BSL code | Full cycle (all 3 layers) |
| Reviewing someone else’s code | Layer 2 + Layer 3 |
| User asks “check the syntax” | Full cycle |

## Verification layers

### Layer 1 — LSP diagnostics (fast)

Goal: immediate feedback on the modified file.

1. `get_diagnostics(uri)` — get errors/warnings from the BSL Language Server.
2. If there are any `error`-level issues, fix them before moving to layer 2.
3. If the LSP is unavailable, proceed to layer 2 and note that LSP checking was skipped.

### Layer 2 — Buddy (VALIDATE_BSL)

Goal: syntax, standards, and searching for БСП analogues.

**What to send:** the full procedures/functions that were edited.
No fragments, no isolated lines — complete method bodies.

**Call:** `ask_ai_assistant` with the VALIDATE_BSL template from buddy-prompting.

**How to interpret the result:**

| Situation | Action |
|----------|--------|
| Buddy found issues | Analyze each one. Filter out false “undeclared variable” complaints on global methods. Fix or justify the rest. |
| Buddy found no issues | **DO NOT treat this as proof of correctness.** Buddy has limited context — it cannot see the project. Move to layer 3. |
| Buddy suggests replacing with a БСП function | Verify via `search_ssl_functions` that the suggested function exists. |

### Layer 3 — Platform API verification

Goal: confirm that every platform object, method, property, and constructor used in the code **really exists** on the specified type.

**Algorithm:**

1. Extract all platform API references from the modified code:
   - `Новый <Тип>(...)` — object creation
   - `<Объект>.<Метод>(...)` — method calls
   - `<Объект>.<Свойство>` — property reads/writes
   - Manager references, enumerations, predetermined values

2. For each usage, verify:

   | What in the code | How to check | Capability |
   |------------------|--------------|------------|
   | `Новый <Тип>` | Does the type exist? | `search_syntax_reference` → `get_type_info` |
   | `Новый <Тип>(парам1, парам2)` | Is there a constructor with those parameters? | `getConstructors` |
   | `Объект.Метод()` | Is the method present on that type? | `getMember` |
   | `Объект.Свойство` | Is the property present on that type? | `getMember` |
   | Type is unclear | Search by name | `search_syntax_reference` → `getMembers` |

3. **Pay special attention to collection types.** APIs differ between `Структура`, `Соответствие`, `ТаблицаЗначений`, `Массив`. Do not assume the same methods apply — verify on the actual type.

4. If `navigate_symbol` is available, use it to determine the variable’s type from its declaration/assignment, then validate the API via platform-context.

## Trust hierarchy

```
check_syntax (компилятор)    ← формальная проверка, финальный вердикт
  > get_diagnostics (LSP)    ← быстрая диагностика
    > bsl-platform-context   ← авторитетный справочник API
      > ask_ai_assistant     ← совещательный голос (не доверять отсутствию ошибок)
```

When sources disagree, the higher one in the hierarchy wins.

## Report format

After the check, provide a structured result:

1. **LSP:** errors / warnings (or “LSP clean / unavailable”).
2. **Buddy:** detected issues + recommendations (or “no remarks — BUT this is not a guarantee”).
3. **Platform API:** confirmed / unconfirmed usages.
4. **Conclusion:** classify each issue as `syntax` / `API error` / `standard violation` / `runtime risk`.

## Errors and limitations

| Problem | Workaround |
|---------|------------|
| LSP unavailable | Skip layer 1 and move to layer 2. Note it in the report. |
| Buddy unavailable | Skip layer 2 and strengthen layer 3. |
| `bsl-platform-context` does not know the type | If the type is from the project (not platform) — check via `navigate_symbol`. |
| False “undeclared variable” from Buddy | Normal for global methods — filter it out. |
| `search_syntax_reference` returns nothing | Clarify the type name (Russian/English spelling) and check the version. |
| Buddy suggests a non-existent БСП function | Verify via `search_ssl_functions`. |

## Capabilities

| Capability | Layer | Purpose |
|------------|-------|---------|
| `get_diagnostics` | 1 | LSP diagnostics for the file |
| `ask_ai_assistant` | 2 | VALIDATE_BSL via Buddy |
| `search_ssl_functions` | 2 | Verify БСП recommendations |
| `search_syntax_reference` | 3 | Locate the platform type |
| `get_type_info` | 3 | Type information |
| `getMembers` | 3 | List members of the type |
| `getMember` | 3 | Verify a specific member |
| `getConstructors` | 3 | Check constructors |
| `navigate_symbol` | 3 | Determine the variable type |
| `check_syntax` | * | Final compiler check (if available) |

---
depends_on: [syntax-checking, buddy-prompting, code-navigation]
---

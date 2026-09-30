# Data Set Query

Operations: `set-query`, `patch-query`.

## set-query — full replacement of the query text

Value — **the full query text** or a link to the file `@path/to/query.sql`.

```bash
xml-gen skd edit Schema.xml set-query "ВЫБРАТЬ Ссылка, Наименование ИЗ Справочник.Номенклатура"
xml-gen skd edit Schema.xml set-query "@queries/sales.sql"
xml-gen skd edit Schema.xml set-query "@queries/sales.sql" --dataSet Продажи
```

Behavior:

- Completely replaces the contents of the `<query>` in the specified data set (`--dataSet`, default — the first one).
- When the value is `@path/to/file.sql`, reads the text from the file. The path is resolved **relative to Schema.xml**, then relative to the current working directory.
- **Does not support batch** — the query may literally contain `;;`; the separator would break the text.
- `<fields>`, `<dataSource>`, `<role>` and the rest of the data set wrapper **do not change**. If the new query returns different columns, the fields must be adjusted separately (`add-field`/`modify-field`/`remove-field`).

Use when:

- You are changing the query logic entirely (rewrote the temporary table, changed the source, added/removed large chunks).
- You generated the query with an external tool and want to load it from a file.

## patch-query — targeted replacement in the query text

Shorthand: `"old => new [@once]"`.

```bash
xml-gen skd edit Schema.xml patch-query "СубконтоДт1) В => СубконтоКт1) В"
xml-gen skd edit Schema.xml patch-query "КАК ВТ_СтароеИмя => КАК ВТ_НовоеИмя @once"
xml-gen skd edit Schema.xml patch-query "&СтарыйПарам => &НовыйПарам @once ;; &ОтЧего => &ОтПериода @once"
```

### Default behavior

Replaces **all occurrences** of the substring in the `<query>` of the specified data set (`--dataSet`).

### @once flag — assert "exactly one occurrence"

With the `@once` flag, the operation:

1. Counts the number of occurrences of the substring.
2. If **0** — error "substring not found", the file is not changed.
3. If **≥ 2** — error "substring is ambiguous: N matches", the file is not changed.
4. If **exactly 1** — replaces, validates, writes.

Use `@once` whenever:

- You are changing an identifier that, by your assumption, is unique (the name of a temporary table, the name of a field in SELECT, the name of a parameter).
- You want to enforce an invariant for the reviewer: "this change is targeted, it does not affect other places."
- You are afraid of hitting a comment or a same-named item elsewhere in the query.

Without `@once` — mass replacement (for example, renaming one field everywhere it appears).

### Multiline substrings

Supported. Newlines in `old`/`new` are compared **literally**, including indentation:

```bash
xml-gen skd edit Schema.xml patch-query "ГДЕ
    Дата >= &НачалоПериода => ГДЕ
    Дата >= &НачалоОтчетногоПериода @once"
```

When composing a multiline substring, make sure the indentation in the shell argument matches the text of the query.

### Batch

Supported via `;;`. Each patch is applied sequentially; an error in any of them rolls back the entire batch (the operation is atomic at the file level).

## Contract: when to use what

| Situation | Operation |
|----------|----------|
| Query is rewritten 80%+ | `set-query` |
| Replacing 1–5 identifiers with known uniqueness | `patch-query @once` (one per patch) |
| Mass rename (for example, field `Цена` → `ЦенаСУчётомСкидки` everywhere) | `patch-query` without `@once` |
| Large chunk of a template that is definitely unique | multiline `patch-query @once` |
| Query lives in a `.sql` file and changes often | `set-query "@queries/...sql"` after generation |

## Edge cases

| Case | Behavior |
|--------|-----------|
| `patch-query "x => y"` and `x` occurs 5 times — without `@once` | All 5 are replaced. Whether this is good or not is the caller's responsibility. |
| `patch-query "x => y @once"` and `x` occurs 0 times | Error, the file is not changed. |
| `patch-query "x => y @once"` and `x` occurs 2+ times | Error "ambiguous", the file is not changed. |
| `set-query "@queries/q.sql"`, the file does not exist | Error "query file not found". |
| `set-query` without `--dataSet` when there are multiple data sets | Applied to the first one. It is better to specify `--dataSet` explicitly. |
| The replacement breaks XML well-formedness (for example, it introduced an unmatched `<`) | Error at the post-validation stage, the file is not changed. |

## Reviewer Invariant

When reviewing outgoing query patches:

- Any `patch-query` without `@once` must be accompanied by the justification "a mass replacement is needed here."
- In the typical case of a targeted edit, add `@once` — it is cheap protection against mistakes in an obvious place.
- Long multiline `patch-query` are better replaced with `set-query "@queries/...sql"` by storing the query in a file.

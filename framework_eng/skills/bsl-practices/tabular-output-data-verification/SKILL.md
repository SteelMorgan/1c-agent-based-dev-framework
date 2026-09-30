---
name: tabular-output-data-verification
description: "Check data correctness with a 1C query and record a data contract before any tabular output: СКД, an MXL print form, direct writing to a `ТабличныйДокумент`, or filling an Excel-import scaffold."
---

# Tabular Output Data Verification

Separate proof of data correctness from layout and visual acceptance. Do not begin
composition, filling areas, or writing cells until the validation query and data contract
confirm the future output set.

## When to apply

| Trigger | Action |
|---|---|
| An СКД report is being designed or changed | Verify each set with a query before running composition |
| A print form renders MXL areas manually | Independently verify rows and totals before filling areas |
| Code writes data directly to `ТабличныйДокумент` | Verify the future set before the first runtime write |
| The visual scaffold comes from an Excel import | Verify the data independently of the imported scaffold |
| A print form receives a document object | Verify the document rows and totals with a query; do not treat the object as proof |

## Mandatory gate

Always follow the chain:

```text
data requirements
  -> safe 1C query
  -> recorded verification result
  -> data contract for columns, parameters, and cells
  -> composition or manual output
```

The output mechanism and the origin of the visual scaffold do not cancel any step. An Excel-import
scaffold proves only the presentation structure. A document object is convenient for runtime
filling, but by itself it does not prove row completeness, absence of duplicates, or correct
totals.

## Verification order

1. Record the requirements for each set: fields, business meaning, row key, expected
   cardinality, rules for empty values, totals, period, parameters, and active filters.
2. Through `platform-data-core`, verify the existence of metadata and source types. For a new
   validation query, apply `query-patterns`.
3. Compose a minimal safe 1C query sufficient to prove the requirements.
   Do not copy the production query in full unless necessary and do not access the DB directly.
4. Run the query with the same period, parameters, and filters that the future output will receive.
5. For each set, record all checks from the mandatory matrix.
6. Prepare a data contract and explicitly separate proven facts and assumptions.
7. Allow composition or manual output only on a full `PASS`. If there is a mismatch, stop the
   dependent route, fix the data source or requirements, and repeat the verification.

## Mandatory evidence matrix

For each set, record:

| Check | What to prove |
|---|---|
| Field composition | Each field exists and has the expected technical and business meaning |
| Row count | Actual cardinality matches the requirements with filters enabled |
| `NULL` and empty values | Required fields are filled in, allowable empty values are classified |
| Duplicates | There are no unexpected repetitions by business key; expected repetitions are explained |
| Aggregates and totals | Checksums, counts, and other totals match the requirements |
| Representative sample | Boundary and typical rows are visible, not just a convenient positive example |

Do not replace row verification with an aggregate alone: a correct grand total can hide duplicates or
offsetting errors. Do not replace an aggregate with a sample alone: a few correct rows do not
prove the complete total.

### Printed form from the document object

If the runtime code receives a document object, create a separate verification query by the
document reference. Check at minimum:

- the composition and number of rows in each output tabular section;
- duplicates by the row business key;
- required empty values;
- document totals and row totals;
- the correspondence of parameters, period, and filters of the future printout.

## Data contract

Create the contract separately for each set. Minimum template:

| Layout column/parameter | Source | Empty value rule | Row key | Expected cardinality | Checked total | Knowledge status |
|---|---|---|---|---|---|---|
| `<name>` | `<set.field or expression>` | `<forbidden/allowed/replacement>` | `<business key>` | `<1, 0..1, N>` | `<sum/count/not applicable>` | `<proven/assumption>` |

The contract must link each displayed column and each area/cell parameter to a source.
Do not leave visual parameters without data-binding and do not mask an assumption as
a query result.

Completed example of a contract and evidence context:
`references/data-contract-example.md`.

Also record the evidence context:

| Set | Period | Parameters | Active filters | Row count | Empty values | Duplicates | Totals | Sample | Verdict |
|---|---|---|---|---:|---|---|---|---|---|
| `<name>` | `<value>` | `<values>` | `<conditions>` | `<N>` | `<result>` | `<result>` | `<result>` | `<link/snippet>` | `PASS/FAIL` |

## Correct and Incorrect

Correct: first execute a separate minimal query with runtime parameters, save
matrix results, and only then pass the proven contract into `skd-report-patterns` or
`print-form-patterns`.

Incorrect:

```bsl
Объект = Ссылка.ПолучитьОбъект();
// Немедленный вывод строк объекта без независимой проверки набора и итогов.
ВывестиПечатнуюФорму(Объект);
```

Such code may produce a visually correct document with incomplete, duplicated,
or incorrectly aggregated data.

## Completion Criteria

The gate is closed only when, for each set:

- the 1C platform query has been executed;
- all six mandatory checks have been recorded;
- the period, parameters, and active filters have been accounted for;
- each column/parameter is linked by data contract to the source and rules;
- proven facts are separated from assumptions;
- the verdict is `PASS` before runtime composition or manual output begins.

---
depends_on:
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/bsl-practices/skd-report-patterns/SKILL.md
  - framework/skills/bsl-practices/print-form-patterns/SKILL.md
---

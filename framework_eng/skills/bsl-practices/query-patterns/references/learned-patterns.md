# Learned Patterns — query-patterns

Verified techniques and anti-patterns accumulated from real tasks.
`confirmed` — confirmed rule, `candidate` — requires reconfirmation.

---

```
status: candidate
area: column aliases in the SELECT list of a 1C query
anti-pattern: do NOT give a SELECT field an alias that matches a reserved word of the query
              language (ЕСТЬ, ДАТА, ЗНАЧЕНИЕ, etc.). Such an alias breaks query compilation
              with the error «Ожидается имя» (name expected), AND the error position points
              at the alias itself rather than at the field expression before it, so
              diagnosing by cursor position easily goes astray (people fix the expression,
              not the name).
pattern: on «Ожидается имя» in a SELECT list, first check the field alias (КАК Имя) for a
         collision with a reserved query-language word, and only then the field expression
         itself. Fix by renaming the alias, not by replacing the literal or rearranging the
         expression.
why: the query-language parser reserves service words (including those used in constructs
     such as ЕСТЬ NULL, ДАТАВРЕМЯ, etc.) and does not allow them as a free alias
     identifier, even when semantically it is an ordinary field.
source: real task (2026-07), developer-code: `ВЫБРАТЬ ПЕРВЫЕ 1 ИСТИНА КАК Есть`
        failed with «Ожидается имя» because of the alias `Есть` (collision with ЕСТЬ NULL);
        fix — rename the alias to `ЕстьЗапись`.
```

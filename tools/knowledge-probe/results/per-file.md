# Knowledge Probe — вердикт по файлам-источникам

Для каждого файла: доминирующий вердикт по каждой модели и рекомендация. Решение — по ХУДШЕЙ модели-исполнителю.


## framework/skills/bsl-practices/coding-standards/SKILL.md
expected_in_weights (ожидание): **да**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| haiku | 0 | 0 | 0 | 0 | 1 | **unstable** |

**Вывод (worst-case):** Худшая модель НЕ знает / нестабильна — знание НУЖНО оставить в навыке (страхует worst-case исполнителя).

## framework/skills/bsl-practices/query-patterns/SKILL.md
expected_in_weights (ожидание): **да**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| haiku | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

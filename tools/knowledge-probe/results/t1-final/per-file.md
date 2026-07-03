# Knowledge Probe — вердикт по файлам-источникам

Для каждого файла: доминирующий вердикт по каждой модели и рекомендация. Решение — по ХУДШЕЙ модели-исполнителю.


## framework/skills/bsl-practices/api-design/SKILL.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 1 | 0 | 0 | 0 | 0 | **knows** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

## framework/skills/bsl-practices/background-jobs/SKILL.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 0 | 1 | **unstable** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 0 | 0 | 0 | 1 | **unstable** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 0 | 0 | 1 | 0 | 0 | **knows_not** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/coding-standards/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 1 | 0 | 0 | 0 | 0 | **knows** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

## framework/skills/bsl-practices/data-exchange/SKILL.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 1 | 0 | 0 | 0 | **partial** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель знает лишь частично — знание полезно оставить (уточняет/дополняет частичное знание в весах).

## framework/skills/bsl-practices/error-handling/SKILL.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 1 | 0 | 0 | 0 | **partial** |
| deepseek-pro | 2 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 1 | 0 | 0 | **knows_not** |
| gpt-5.5 | 1 | 1 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 0 | 1 | 0 | 1 | **knows_not** |
| opus | 1 | 1 | 0 | 0 | 0 | **partial** |
| sonnet | 1 | 1 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель НЕ знает / нестабильна — знание НУЖНО оставить в навыке (страхует worst-case исполнителя).

## framework/skills/bsl-practices/form-patterns/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 1 | 0 | 0 | 0 | **partial** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 1 | 0 | 0 | 0 | **partial** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель знает лишь частично — знание полезно оставить (уточняет/дополняет частичное знание в весах).

## framework/skills/bsl-practices/form-patterns/references/learned-patterns.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 0 | 1 | **unstable** |
| gpt-5.4-mini | 0 | 0 | 1 | 0 | 0 | **knows_not** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| opus | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| sonnet | 0 | 0 | 0 | 1 | 0 | **hallucinates** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/form-visual-requirements/SKILL.md
expected_in_weights (ожидание): **да**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/integration-patterns/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| gpt-5.4-mini | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 0 | 0 | 0 | 1 | **unstable** |
| opus | 0 | 0 | 0 | 0 | 1 | **unstable** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/metadata-object-design/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 1 | 0 | 0 | 0 | **partial** |
| opus | 0 | 0 | 0 | 0 | 1 | **unstable** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель НЕ знает / нестабильна — знание НУЖНО оставить в навыке (страхует worst-case исполнителя).

## framework/skills/bsl-practices/query-optimize/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 1 | 0 | 0 | 0 | 0 | **knows** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

## framework/skills/bsl-practices/query-patterns/SKILL.md
expected_in_weights (ожидание): **да**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 1 | 0 | 0 | 0 | **partial** |
| deepseek-pro | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.4-mini | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 1 | 0 | 0 | 0 | **partial** |
| opus | 0 | 1 | 0 | 0 | 0 | **partial** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель знает лишь частично — знание полезно оставить (уточняет/дополняет частичное знание в весах).

## framework/skills/bsl-practices/security/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 0 | 1 | **unstable** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 1 | 0 | 0 | 0 | **partial** |
| opus | 0 | 0 | 0 | 0 | 1 | **unstable** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель НЕ знает / нестабильна — знание НУЖНО оставить в навыке (страхует worst-case исполнителя).

## framework/skills/bsl-practices/security/references/auth.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 1 | 0 | **knows** |
| deepseek-pro | 2 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 2 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 2 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 1 | 1 | 0 | 0 | 0 | **knows** |
| opus | 2 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 2 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

## framework/skills/bsl-practices/security/references/review-checklist.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 0 | 0 | **knows** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 1 | 0 | 0 | 0 | 0 | **knows** |
| opus | 1 | 0 | 0 | 0 | 0 | **knows** |
| sonnet | 1 | 0 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** ВСЕ модели (включая худшую) знают это — знание в весах, в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).

## framework/skills/bsl-practices/security/references/secrets.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| gpt-5.4-mini | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 0 | 1 | 0 | 0 | **knows_not** |
| opus | 0 | 0 | 0 | 0 | 1 | **unstable** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/SKILL.md
expected_in_weights (ожидание): **частично**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 3 | 1 | **hallucinates** |
| deepseek-pro | 1 | 0 | 0 | 3 | 1 | **hallucinates** |
| gpt-5.4-mini | 1 | 1 | 1 | 1 | 1 | **knows** |
| gpt-5.5 | 3 | 1 | 0 | 1 | 0 | **knows** |
| haiku | 4 | 0 | 1 | 0 | 0 | **knows** |
| opus | 4 | 0 | 0 | 0 | 1 | **knows** |
| sonnet | 4 | 0 | 0 | 1 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/commands-external.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 1 | 0 | 3 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 5 | 0 | **hallucinates** |
| gpt-5.4-mini | 0 | 3 | 1 | 1 | 0 | **partial** |
| gpt-5.5 | 2 | 2 | 0 | 1 | 0 | **knows** |
| haiku | 2 | 1 | 1 | 1 | 0 | **knows** |
| opus | 2 | 1 | 0 | 1 | 1 | **knows** |
| sonnet | 2 | 0 | 0 | 1 | 2 | **knows** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/data-exchange.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 4 | 0 | **hallucinates** |
| deepseek-pro | 1 | 1 | 1 | 1 | 1 | **partial** |
| gpt-5.4-mini | 1 | 2 | 1 | 1 | 0 | **partial** |
| gpt-5.5 | 3 | 2 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 3 | 1 | 0 | 1 | **partial** |
| opus | 2 | 2 | 0 | 1 | 0 | **knows** |
| sonnet | 2 | 1 | 0 | 2 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/esign-mcd.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 3 | 1 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 4 | 1 | **hallucinates** |
| gpt-5.4-mini | 1 | 0 | 1 | 2 | 1 | **hallucinates** |
| gpt-5.5 | 3 | 0 | 0 | 1 | 1 | **knows** |
| haiku | 0 | 1 | 2 | 1 | 1 | **knows_not** |
| opus | 2 | 1 | 1 | 1 | 0 | **knows** |
| sonnet | 2 | 0 | 0 | 3 | 0 | **hallucinates** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/files-and-versions.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 1 | 0 | 0 | 4 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 5 | 0 | **hallucinates** |
| gpt-5.4-mini | 1 | 0 | 0 | 1 | 3 | **unstable** |
| gpt-5.5 | 1 | 1 | 0 | 2 | 1 | **hallucinates** |
| haiku | 1 | 1 | 2 | 1 | 0 | **knows_not** |
| opus | 1 | 1 | 0 | 3 | 0 | **hallucinates** |
| sonnet | 0 | 3 | 0 | 2 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/forms-validation.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 2 | 1 | 0 | 1 | 1 | **knows** |
| deepseek-pro | 0 | 2 | 0 | 2 | 1 | **hallucinates** |
| gpt-5.4-mini | 2 | 3 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 3 | 1 | 0 | 1 | 0 | **knows** |
| haiku | 2 | 2 | 0 | 1 | 0 | **knows** |
| opus | 2 | 2 | 0 | 0 | 1 | **partial** |
| sonnet | 3 | 2 | 0 | 0 | 0 | **knows** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/protection-pd.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 5 | 0 | **hallucinates** |
| deepseek-pro | 0 | 1 | 0 | 4 | 0 | **hallucinates** |
| gpt-5.4-mini | 0 | 3 | 2 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 2 | 2 | 0 | 1 | **partial** |
| haiku | 0 | 2 | 2 | 1 | 0 | **knows_not** |
| opus | 0 | 0 | 2 | 1 | 2 | **knows_not** |
| sonnet | 0 | 1 | 2 | 2 | 0 | **knows_not** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/test-writing/SKILL.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| gpt-5.4-mini | 0 | 0 | 0 | 0 | 1 | **unstable** |
| gpt-5.5 | 0 | 1 | 0 | 0 | 0 | **partial** |
| haiku | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| opus | 0 | 1 | 0 | 0 | 0 | **partial** |
| sonnet | 0 | 0 | 0 | 1 | 0 | **hallucinates** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/test-writing/references/learned-patterns.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.4-mini | 1 | 0 | 0 | 0 | 0 | **knows** |
| gpt-5.5 | 1 | 0 | 0 | 0 | 0 | **knows** |
| haiku | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| opus | 0 | 1 | 0 | 0 | 0 | **partial** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

## framework/skills/bsl-practices/test-writing/references/yaxunit-cheatsheet.md
expected_in_weights (ожидание): **нет**

| модель | knows | partial | knows_not | halluc | unstable | доминирующий |
|---|---|---|---|---|---|---|
| deepseek-flash | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| deepseek-pro | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.4-mini | 0 | 1 | 0 | 0 | 0 | **partial** |
| gpt-5.5 | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| haiku | 0 | 0 | 0 | 1 | 0 | **hallucinates** |
| opus | 0 | 1 | 0 | 0 | 0 | **partial** |
| sonnet | 0 | 1 | 0 | 0 | 0 | **partial** |

**Вывод (worst-case):** Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен (без него будет уверенная ложь).

# Consilium wave prompts and verdict.md template (TD 4.1, 5, 3.5)

Prompts for participant waves are built by the `consilium.py` core from the templates below. The moderator
(primary agent) owns digests, synthesis, and the verdict - their templates are in the section
«Moderator Prompts». The core enforces formal invariants (digest gate, lint,
kill protection).

## Common structured-turn instruction (appended to each participant prompt)

````
В конце ответа ОБЯЗАТЕЛЬНО приложи fenced-блок структурированного хода:
```consilium-structured
{"elements": ["E1", "E2"], "new_findings": [{"id": "F-01", "text": "..."},
 "position_changes": [{"element": "E1 | M#:E1", "action": "agree|disagree|refine|withdraw", "refs": [1]}],
 "borrowed": [{"element": "E3", "source_ref": 2}],
 "risk_checklist_responses": [{"item_id": "<id пункта>", "verdict": "hit|clear|na", "note": "<текст>"}]}
```
- elements: стабильные id элементов твоей текущей модели;
- position_changes: по пунктам; чужие элементы адресуются как <anon_id>:<element>;
- borrowed: только с разрешимым source_ref на запись-источника (антинакрутка);
- risk_checklist_responses (CONS-03): ответы на ВСЕ применимые к этому типу хода пункты
  риск-чеклиста твоей роли; verdict строго lowercase hit|clear|na; для hit и na note
  обязателен (непустой после trim, не "-", >=3 символов); для clear note не нужен.
````

## Domain role block (CONS-03 E-3, in proposal/attack/response/redteam/confirmation prompts)

Role block is inserted by code (`consilium.py::_build_prompt` + `_role_block`) into ALL
wave templates for phases A/B/D (in confirmation/redteam - the role of phase D by cross-rotation) -
the static text of the templates below does not duplicate it:

```
Твоя роль: <title>. Линза: <lens>
Риск-чеклист домена (ответь по КАЖДОМУ пункту в structured.risk_checklist_responses):
- [<item_id>] <текст пункта>
Evidence: <evidence_requirements домена>.
```

Moderator prompts (digest, synthesis, verdict) do NOT change.
## Phase A — proposal (1 turn per participant, blind)

```
Архитектурный вопрос консилиума:
<question>

Твоя роль на фазу A: <role>.
Фаза A (дивергенция): выдвинь свою версию решения независимо и вслепую —
без оглядки на чужие позиции.
<structured instruction>
```

## Phase B — attack (round r)

```
Дайджест модератора:
<digest>

Анонимизированные позиции (bundle):
<bundle>

Фаза B, раунд <r>, твоя роль: <role>.
Атакуй слабые элементы чужих моделей по существу. Заимствование чужих решений
разрешено и поощряется — оформляй через borrowed с source_ref.
<structured instruction>
```

## Phase B — response (round r)

```
Дайджест модератора:
<digest>

Анонимизированные атаки (bundle):
<bundle>

Фаза B, раунд <r>, твоя роль: <role>.
Ответь на критику своей модели: по каждому пункту agree/disagree/refine/withdraw.
Заимствование разрешено.
<structured instruction>
```

## Final statement of the excluded model (FR-03b)

```
Дайджест модератора:
<digest>

Твоя модель исключена детерминированным критерием FR-03 (значения прокси-метрик — в kill-log).
Сформулируй финальное заявление: оно ДОСЛОВНО войдёт в minority report.
<structured instruction>
```

## Phase D — redteam (1 attack on the merge, cross-rotation role)

```
Дайджест модератора:
<digest>

Синтез модератора (фаза C):
<synthesis>

Фаза D (ред-тим), твоя роль по кросс-ротации: <role_D>.
Атакуй склейку: найди дефекты синтеза, потерянные элементы, внутренние противоречия.
<structured instruction>
```

## Phase D — confirmation (authors' confirmation/challenge)

```
Дайджест модератора:
<digest>

Синтез модератора (фаза C):
<synthesis>

Фаза D: подтверди или оспорь неискажённость твоего вклада в синтез
(position_changes: agree/disagree по элементам синтеза).
<structured instruction>
```

## Moderator Prompts (digest, synthesis, verdict)

These texts are authored by the moderator (primary agent), not the core. Below are working templates;
formal invariants are checked by the core in fail-closed mode.

### Wave digest (phases B and D, each wave after the first)

The digest is the only thing the participant receives about other participants' turns besides the anonymized
bundle (NFR-02). Requirements checked by the core (`lint_digest`): no real participant ids
(only `anon_id` values like `M1`, `M2`) and within the participant's `context_budget`.
Substantive requirements (not checked by the core, the moderator's responsibility):

```
Состояние полемики после <волны>:

Живые модели: <anon_id списком; замороженные unresponsive — с пометкой>.

По каждой живой модели (только anon_id, без авторства):
- <M#>: суть позиции в 2-3 предложениях; элементы, пережившие критику; элементы,
  снятые/опровергнутые; заимствования (с source_ref).

Ключевые развилки раунда:
- <по каким элементам позиции реально расходятся, а не пересказ всех атак>.

Открытые findings раунда: <F-идентификаторы и суть, без атрибуции>.
```

- Compress, do not retell: the digest is a map of the debate, not a transcript summary.
- Do not give ratings like strong/weak model - the kill criterion is deterministic, and the moderator's judgment
  is not included in what participants receive (protection against anchoring).
- Anonymize the author, but preserve addressability: the participant should understand which
  attacks apply to their model and be able to refer to others' elements as `<M#>:<E#>`.

### Phase C synthesis (`--synthesis-file`)

```
# Синтез модератора

<итоговая модель: каждый элемент — с traceability на источник: seq:<n> записи
transcript, откуда взят элемент>

Обоснование выбора между расходящимися позициями: <почему взяты именно эти
элементы живых моделей, какие аргументы полемики перевесили>.

Не вошло в склейку: <элементы живых моделей, отвергнутые синтезом, с seq-ссылкой
и одной строкой причины — это сырьё minority report>.
```

- Traceability `seq:<n>` is mandatory for each element (FR-05); the core records
  the synthesis as the moderator's turn in the transcript.
- The moderator assembles the synthesis from the live models and does not introduce new elements of its own -
  its own opinion is expressed only through participation via its adapter (FR-05).
### Phase E verdict (`--decision-file`)

Assembled from phase C synthesis + transcript materials using the verdict.md template below;
the core checks required sections fail-closed. The core inserts the Minority report
verbatim from the transcript — the moderator is forbidden to edit it (FR-03b/FR-05).

## Moderator synthesis format (CONS-02 OPT-3, mandatory)

Synthesis (phase C) is a bulleted list of items strictly following the template
`- [E<n>] <text> (refs: seq:<n>, seq:<m>)` + a section justifying the exclusion of contributions:

```markdown
# Синтез

- [E1] разделение ядра и доменов (refs: seq:2, seq:5)
- [E3] транспорт через события (refs: seq:3)

## Обоснование исключения вкладов

Вклад <модель> не вошёл: <почему каждый невошедший живой вклад исключён>.
```

- At least 1 item; ranges `seq:2-4` are forbidden (comma-separated listing only);
  any non-empty line outside the item template and outside the sections (heading; section
  justifying the exclusion of contributions) — the core rejects the synthesis fail-closed;
- conditional phase D: the core skips red-team only if all items trace
  to a single live active source model (membership: item id ∈
  structured.elements referenced record); any ambiguity (unresolvable ref,
  ref to a moderator record, source unresponsive/killed, ≥2 sources) → phase D
  is performed.

## verdict.md template (required sections, TD 3.5 — the core checks fail-closed)

```markdown
# Вердикт консилиума <session_id>

Вопрос: <question>

## Решение
<синтез модератора с traceability: каждый элемент ссылается на seq:<n> модели-источника>

## Kill-log (FR-03c)
| участник | раунд | survived | borrowed_in | upheld_against | tie-break |

## Minority report (FR-03b/FR-05, дословно)
<дословные тексты несогласных и финальные заявления исключённых — ТОЛЬКО из transcript;
редактирование модератором запрещено, ядро вставляет тексты записей дословно>

## Флаг состава (FR-06)
<кворум выполнен/деградировал; предупреждение при однородности family>

## Инциденты (FR-11)
<unresponsive-участники, retry, таймауты>

## Стоимость (NFR-01)
<фактические вызовы адаптеров vs диапазон 16–25 default, потолок 31>

## Завершение фазы B
<стоп-условие: converged | round_limit | stalemate | invocation_ceiling>

## Фаза D
<выполнена | пропущена (CONS-02 OPT-3): причина — модель-источник, число элементов,
сэкономленные вызовы>

## Эскалация
<ESCALATE_TO_HUMAN при material-разногласии, иначе «нет»>

---
Advisory-only: verdict не является acceptance/finalization gate (FR-13).
```
---
name: review-swarm
description: "MUST use WHEN a code diff needs second-opinion or multi-model review: light tier = one eligible independent cross-family reviewer selected from enabled participants outside the caller family (also the executor of blocking acceptance-bound review and the finalization gate APPROVE_COMPLETION), full tier = divergent swarm of all enabled models with dedup, validation rounds and arbitration. Findings must have code locations; architectural disputes without a location belong to agent-consilium."
capabilities: review,code-review,agent-governance,multi-model-review,cross-provider
---

# Рой-ревью (review-swarm) — правила для вызывающего

Этот навык — playbook вызывающего агента (Оркестратора, primary agent), который
запускает код-ревью diff лёгким тарифом (один eligible ревьюер, не caller) или
полным роем (все enabled-модели), ведёт туры, разрешает пограничные случаи и
принимает диспозиции находок. Механика ядра и схемы данных здесь не
документируются — вызывающему они не нужны (см. «Что вызывающему НЕ нужно
знать»).

## Что это и governance

- **Два тарифа, различающиеся шириной, не качеством моделей** (FR-15):
  - **лёгкий** — один ревьюер из enabled/eligible участников другого семейства
    относительно `--caller` (наследник gate-правила
    `cross-provider-review.md`); один обзорный вызов + re-review фиксов;
  - **полный рой** — все enabled-адаптеры реестра (включая семейство
    вызывающего): слепой тур 1 с линзами, механический дедуп, валидация
    туров 2–4, арбитраж Оркестратора, re-review фиксов, кластеризованный
    репорт без сведения к единому мнению.
- **Advisory для находок (advice-only).** Находки роя сами по себе не блокируют
  acceptance; диспозиции принимает Оркестратор. Исключение — gate-режим
  (FR-16): лёгкий тариф исполняет **блокирующий** acceptance-bound review и
  finalization gate `APPROVE_COMPLETION` (см. «Gate-наследование»).
- **Оркестратор — вы, не скрипт.** Ядро (`scripts/swarm.py` + `swarm_core.py`)
  контролирует детерминированные инварианты (state machine, лимиты ходов,
  анонимизация, кворум разнообразия, wall-clock бюджет, парность cleanup) и
  отказывает fail-closed; обойти предикаты ядра нельзя. Дедуп пограничных
  случаев, арбитраж по evidence, диспозиции и итоговое решение — ваши.
- **Участники read-only** и работают только с sandbox-копиями (focused-paths,
  НЕ `--full-context`); diff доставляется файлом в sandbox (`review.diff`),
  а не текстом промпта.
- Все обмены — structured-ходы по схемам references; свободный текст — только
  в `rationale`. Свободные мультиагентные «чаты» запрещены (FR-03).

## Когда вызывать

- **Код-ревью diff** перед acceptance: независимое второе мнение
  (лёгкий тариф) или дивергентная мультимодельная проверка (рой).
- **Acceptance-bound артефакты и finalization gate** — лёгкий тариф с
  `--gate` (блокирующая семантика, наследуется от gate-правила `cross-provider-review.md`).

**Граница режимов (проектное правило):** находка код-ревью обязана иметь
`location` (file:line) — ядро отклоняет находку без неё или с нерезолвящейся
location до учёта. «Спорное архитектурное решение» без location — территория
`agent-consilium`, не роя. Не смешивайте: архитектурные споры в рое
замусоривают статистику обоих механизмов (RISK-09).

## Тарифы и триаж (FR-15)

1. **Детерминированная карта критичности** (`references/criticality-map.md`):
   пути diff, пересекающиеся с паттернами карты, ВСЕГДА идут полным роем;
   привязка `glob → lens` даёт forced-линзу критичного пути. Карта —
   эскалатор тарифа, не таксономия (вердикт CMAP-01, 2026-07-30): critical
   объявляется только на путях-владельцах исполняемого/governance-инварианта
   (семантические auth/crypto/payment/billing/migrations, gate-протокол в
   обоих зеркалах, владельцы security/data-contracts инвариантов, спящий
   `secrets/**`). Управление картой — три однонаправленных артефакта
   (metrics → map-proposal → исполняемая карта), обязательный load-test
   перед записью, guard-тесты `tests/integration/test_criticality_map.py`,
   направленный инвариант покрытия; обоснования и политика обновления —
   `tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md`.
2. **Серая зона** (нет пересечения с картой) — ваша рубрика: вы и так читаете
   diff; отдельной модели оценки нет и не нужно. Ядро fail-closed требует
   явный выбор `--tier light|swarm`.
3. **Калибровочная квота (N = 4)**: каждое 4-е лёгкое ревью исполняется
   полным роем независимо от триажа (счётчик `.swarm-track-record/config.json
   → light_reviews_completed`, `calibration_every` в конфиге) — калибрует и
   триаж, и статистику (иначе выборка смещена: роем проверялось бы только
   сложное, RISK-07). Калибровочный прогон помечается
   `convene --calibration-run` и фиксируется в track record.

```bash
SKILL_DIR="<абсолютный каталог навыка review-swarm из каталога доступных skills>"
test -f "$SKILL_DIR/scripts/swarm.py"
python3 "$SKILL_DIR/scripts/swarm.py" triage --paths <пути diff/focused-набора> [--tier light|swarm]
# → {"tier", "reason": criticality_map | calibration_quota | orchestrator_rubric,
#    "calibration", "hits", "forced_lenses"}
```

## Жизненный цикл вызова

Команды (ядро — `scripts/swarm.py`, direct CLI из корня репозитория).
`convene` всегда требует явный валидный `--caller` как policy assertion
доверенной agent-среды:

```bash
SKILL_DIR="<абсолютный каталог навыка review-swarm из каталога доступных skills>"
test -f "$SKILL_DIR/scripts/swarm.py"

python3 "$SKILL_DIR/scripts/swarm.py" doctor --json                 # pre-flight: реестр, healthcheck, кворум
python3 "$SKILL_DIR/scripts/swarm.py" triage --paths <paths> [--tier light|swarm]
python3 "$SKILL_DIR/scripts/swarm.py" convene --tier light|swarm --diff <diff-файл> \
    --paths <focused paths> --caller <id участника реестра> \
    [--gate acceptance|completion] [--gate-reviewer <id>] \
    [--timeout-sec 900] [--calibration-run]
python3 "$SKILL_DIR/scripts/swarm.py" attack <session_id>           # тур 1 (слепой, параллельный)
python3 "$SKILL_DIR/scripts/swarm.py" dedup <session_id> [--journal-file склейки.json]
python3 "$SKILL_DIR/scripts/swarm.py" assess <session_id>           # тур 2: волны вердиктов
python3 "$SKILL_DIR/scripts/swarm.py" rebut <session_id>            # тур 3: ответы авторов (1 ход)
python3 "$SKILL_DIR/scripts/swarm.py" vote <session_id>             # тур 4: финальные вотумы
python3 "$SKILL_DIR/scripts/swarm.py" lineage-query <session_id> --provider-turn-id <provider_turn_id>
python3 "$SKILL_DIR/scripts/swarm.py" clarify <session_id> --shard-task-id <task_id> \
    --idempotency-key <key> --prompt-file <уточнение.md>
python3 "$SKILL_DIR/scripts/swarm.py" arbitrate <session_id> --finding F-NNN --decision-file решение.json
python3 "$SKILL_DIR/scripts/swarm.py" report <session_id>
python3 "$SKILL_DIR/scripts/swarm.py" gate-verdict <session_id> [--dispositions-file д.json] [--claim-file claim.md] \
    [--conditional-decision confirm|reject] [--gate-reviewer <id>]
python3 "$SKILL_DIR/scripts/swarm.py" rereview <session_id> --finding F-NNN --fix-diff <fix.diff> [--developer-claim fixed]
python3 "$SKILL_DIR/scripts/swarm.py" status <session_id>
python3 "$SKILL_DIR/scripts/swarm.py" close <session_id> [--keep --keep-reason "<причина>"]
```

Пошагово (полный рой):

1. **`doctor --json`** — pre-flight: доступность CLI/адаптеров и кворум
   разнообразия (≥2 enabled-участников из ≥2 family; при нарушении `convene`
   полного тарифа откажет fail-closed — рой без разнообразия бессмысленен).
2. **`triage`** — тариф (см. выше).
3. **`convene --tier swarm --diff ... --paths ... --caller <id>`** — создаёт
   сессию: состав = все enabled (включая вашу семью — вы участвуете как
   Оркестратор, а не gate-ревьюер; состав полного роя не меняется), ядро назначает
   каждому **одну линзу** из каталога `code-review` (security, correctness,
   concurrency, performance, data-contracts, tests): forced-линзы критичных
   путей принудительно, остальные — сбалансированной ротацией (exploration,
   запрет повтора до исчерпания пула, min n_eff ячейки «модель × роль»;
   argmax-by-strengths отсутствует намеренно). `--caller` обязателен и обязан
   резолвиться в registry; это policy assertion доверенной agent-среды, не
   authentication hostile caller. Family сохраняется только как metadata/для
   diversity quorum.
4. **`attack`** — тур 1: слепая параллельная атака. Участники не видят находок
   и ходов друг друга (слепота — условие измеримости уникальности); каждый
   получает одинаковый контекст (diff файлом в sandbox + focused paths) и свою
   линзу с чеклистом. Чеклист не ограничивает модель: находки вне линзы
   принимаются с `in_lens: false`. Ядро валидирует находки (location обязана
   резолвиться в проверяемый набор; невалидные отклоняются до учёта с записью
   причины) и нумерует принятые `F-NNN`.
5. **`dedup [--journal-file ...]`** — механический дедуп ядром по
   (location, category) с окном перекрытия 4 строки → три группы:
   **неуникальные** (нашли ≥2 слепые модели — автоподтверждённые, бесплатная
   валидация), **уникальные неподтверждённые** (→ тур 2), **пограничные**
   (строгое перекрытие диапазонов при разных категориях — печатаются вам).
   Пограничные случаи разрешаете вы: ручная склейка/разделение только через
   `--journal-file` с обязательным обоснованием (dedup-journal сессии,
   fail-closed). При скоррелированных ложных срабатываниях (≥2 модели
   повторили одну ошибку) — override автоподтверждения записью
   `override_auto_confirm` с обоснованием (маркер `auto_confirmed_overridden`
   в track record). **Повторный `dedup --journal-file` из состояния DEDUP
   разрешён** (E2E-F1): дельта дописывается в сырой append-only журнал, результат
   — проекция ВСЕГО журнала единым fold'ом в порядке файла (дубли записей
   отсекаются на входе fold'а; маркер реплея — в чекпоинте `dedup_complete`).
   Из TOUR2 и позже `dedup` по-прежнему недоступен (fail-closed).
6. **`assess`** — тур 2 (атака): соседние модели (не автор) выносят по каждой
   уникальной неподтверждённой находке вердикт `upheld/overruled/reclassify/
   uncertain` с обязательным **новым** evidence; автор анонимен (`anon_id`).
   Все `upheld` → находка подтверждена досрочно, туры 3–4 по ней не идут.
7. **`rebut`** — тур 3 (ответ автора, ровно один ход): `maintain` (с
   контр-evidence) / `withdraw` / `accept_reclassify`.
8. **`vote`** — тур 4 (финальный вотум, один ход). Неснятое disagreement →
   `contested`.
9. **`arbitrate --finding F-NNN --decision-file ...`** — арбитраж contested
   severity ≥ major (P1–P2) **обязателен** до репорта (ядро отклонит `report`).
   Решение — по коду, не по риторике: `evidence_quote` (дословная цитата
   спорного места) и `location`, резолвящаяся в реальный файл, проверяются
   ядром fail-closed; решение финальное, второго круга апелляций нет.
10. **`report`** — кластеризованный репорт с атрибуцией и статусами
    (confirmed/withdrawn/reclassified/contested; `unvalidated` — деградация по
    wall-clock, не тихий обрыв). **Без сведения к единому мнению**: нет
    verdict, нет kill, нет консенсуса — несогласие фиксируется, minority-
    находки — главная ценность режима. Диспозиции находок — за вами.
11. **`rereview --finding F-NNN --fix-diff ...`** — re-review фикса **автором
    находки**: новая focused-сессия того же адаптера (старая не сохраняется),
    fix-diff доставляется файлом + обязательный `sync`; вердикт
    `fixed/partially/not_fixed/introduced_new_issue` (новая проблема уходит в
    общий пул). Конфликт «разработчик утверждает, что исправил / автор — нет»
    (расхождение с `--developer-claim`) → эскалация на ваш арбитраж
    (`arbitrate`, тот же механизм evidence; решение — итоговый статус фикса).
12. **`close` — обязательно всегда** (см. cleanup-дисциплину ниже).

Лёгкий тариф: `convene --tier light` (ядро выбирает eligible ревьюера другого
семейства относительно `--caller`; пустой пул — отказ, literal self-review и
caller-family review запрещены. Совпадение семейства ревьюера с фактическим
автором артефакта допустимо только по утверждённой матрице и при выполненном
capability/effort floor, потому что автор и `--caller` — разные identity) →
`attack` (1 обзорный вызов) → `report` (по шаблону
`references/review-report-template.md`) → опционально `gate-verdict` →
`rereview` по фиксам → `close`. Туров 2–4 и дедупа нет — команды полного
тарифа в лёгкой сессии недоступны (fail-closed). **Карта критичности не
обходится флагом** (F-003): если пути/diff пересекаются с
`references/criticality-map.md`, `convene --tier light` отказывает fail-closed
с указанием полного тарифа — триаж в рой обязателен (AC-15).

## Протокол туров 1–4 — лимиты ходов (FR-07)

- **Тур 1** = N вызовов (число enabled-адаптеров), полный контекст. После
  завершения тура 1 родительская сессия каждого участника запечатывается:
  последующие вопросы в неё не отправляются, а её provider checkpoint служит
  неизменяемым основанием для нативных fork.
- **Туры 2–4 исполняются через native fork-шарды**. Для каждого участника
  создаётся один шард от его запечатанного checkpoint. Шарды разных участников
  выполняются параллельно, но задачи внутри одного шарда — строго
  последовательно в одной дочерней provider-сессии. `start` новой независимой
  сессии и искусственное копирование контекста не являются fallback: если
  реестр, capability probe или lineage proof не подтверждают native fork,
  соответствующая фаза отказывает fail-closed.
- **Туры 2–4 — фокусные** (находка + локальный фрагмент), на порядок дешевле
  тура 1; верхняя граница: тур 2 ≤ U×(N−1), тур 3 ≤ U×1, тур 4 ≤ U×(N−1),
  где U — число уникальных неподтверждённых; досрочная остановка при всех
  `upheld` обязательна.
- **Потолок 2 обмена** после первичной атаки: ответ автора — обмен 1,
  финальный вотум — обмен 2; третий обмен невозможен (ядро отклоняет).
- **Каждый ход обязан нести новое evidence**: повтор `(path, line, quote)`
  в треде отклоняется ядром (`stale_evidence`; нормализация пробелов не
  обходит предикат).
- **Запрет новых находок в турах 2–4**: новый баг в ответе в тред не
  принимается — маршрутизируется в общий пул отдельной находкой.
- Невалидный structured-ход — ровно один retry, затем участник `unresponsive`
  (протокол продолжается без него); таймаут вызова — `unresponsive` без retry.
- Связь `execution → snapshot → shard → task → provider turn`
  дописывается в append-only `lineage.jsonl`. Для разбора позднего вопроса
  сначала восстановите исходные task и shard по provider turn через
  `lineage-query`. Когда backend маршрутизировал вопрос в clarification-task,
  возьмите её `shard_task_id` из `status.pending_clarifications` и продолжите
  именно тот же дочерний шард командой `clarify` с новым устойчивым
  `--idempotency-key`. Передавать `clarify` id исходной review-task нельзя:
  команда принимает только уже маршрутизированную pending clarification.
  Перенаправление уточнения в другой шард или в запечатанного родителя
  запрещено; повтор того же ключа идемпотентен.

## Обязанности Оркестратора

- **Дедуп пограничных случаев — только с журналом**: каждая ручная
  склейка/разделение/override — записью в dedup-journal с обоснованием
  (fail-closed; ядро отклоняет записи не от Оркестратора и без reason).
  Механическую группировку не переопределяйте молча. Пограничные пары
  печатает первый `dedup`; решения по ним применяйте ПОВТОРНЫМ
  `dedup --journal-file` — дельта допишется в журнал, итог пересчитается
  реплеем всего журнала (повторная передача того же файла безопасна:
  дубли отсекаются на входе fold'а).
- **Арбитраж — по evidence с осмотром кода**: вы **обязаны сами посмотреть
  спорное место в коде** (read-only) и процитировать его в `evidence_quote`
  решения; решение по коду, не по риторике. Решение финальное; эскалация к
  человеку — при material-разногласии (меняющем итоговое решение).
- **Re-review фиксов** инициируете вы после диспозиций: по каждой исправленной
  находке — `rereview` (проверяет автор находки); конфликт разрешаете
  арбитражем, выслушав обе стороны по коду.
- **Диспозиции находок** (`agree/partial/disagree/withdrawn/out_of_scope`)
  принимаете вы по real artifacts и evidence — репорт роя их не содержит
  (advisory). В маршруте Claude → Codex/GPT дополнительно явно оцените риск
  оверинжиниринга по каждой находке (наследуемое правило, см. шаблон
  `references/review-report-template.md`).

## Gate-наследование (FR-16, AC-16/AC-24)

Лёгкий тариф — наследник gate-правила `cross-provider-review.md` и исполнитель его
блокирующей gate-семантики. Blocking authority и fail-closed guards сохранены:

- **Правило identity/capability**: literal caller/self-review и участник
  caller-family не удовлетворяют gate. Gate-роли исполняют только участники
  другого семейства с `gate_legal` в реестре.
  Direct CLI требует явный валидный `--caller`; это policy assertion внутри
  доверенной agent-среды, а не authentication hostile caller. Автор артефакта
  и caller — разные субъекты; identity автора не подставляется вместо caller.
  Граница threat model и отдельная потребность во внешнем authenticated
  runtime contract описаны в
  `references/design-decisions-e2e02.md`.
- **Полный рой НЕ заменяет gate** (FR-15): acceptance-bound артефакт, уведённый
  триажем в рой, обязан получить gate-проход отдельно — лёгким тарифом либо
  явно назначенным участником роя другого семейства относительно caller,
  который не совпадает с caller и имеет `gate_legal`
  (`convene --gate acceptance|completion [--gate-reviewer <id>]`). Такой
  участник — **двойная роль (AC-24)**: в туре 1 он рядовой ревьюер, а его
  находки и gate-вердикт помечаются `gate_pass` и **выводятся из
  advisory-статистики** track record. Факт и форма gate-прохода фиксируются в
  review trace репорта (секция «Gate-проход»).
- **Gate-вердикт — отдельный structured-вызов ПОСЛЕ `report` и диспозиций**
  (`gate-verdict`), ≤3 итераций review/rework/delta; на дельта-итерациях ядро
  выполняет `sync` sandbox перед вызовом.
- **Пустые диспозиции при нуле находок** (F-002): если ревьюер не выдал ни
  одной находки, валидный файл диспозиций — `{}` (передаётся явно через
  `--dispositions-file`); при непустом пуле находок пустой файл отклоняется
  fail-closed.
- **`conditional_accept` — НЕ approved** (F-007): условное принятие переводит
  gate в промежуточный статус `conditional`; терминальный `approved`
  выставляет только ваше явное решение — `gate-verdict
  --conditional-decision confirm` (условия подтверждены кодом) или `reject`
  (разногласие → дельта-итерация/эскалация по счётчику). Решение фиксируется
  в session и review trace.
- **Переназначение gate-ревьюера** (F-012): если назначенный ушёл в
  `unresponsive`, передайте `gate-verdict --gate-reviewer <id>` — замена
  проходит тот же floor (enabled + healthy + family != caller-family + не
  caller + `gate_legal`), при
  необходимости стартует новый focused-sandbox (парность cleanup сохраняется);
  пересоздание сессии не требуется. Активного ревьюера переназначить нельзя
  (fail-closed).
- **Durable след исхода gate** (F-006): при `close` исход блокирующего gate
  (режим, caller, ревьюер, статус, итерации, вердикты, диспозиции, эскалация,
  переназначения) пишется в `.swarm-track-record/gate-outcomes.jsonl` и
  переживает удаление эфемерной сессии.

### Протокол acceptance-bound (наследуется дословно по смыслу)

Для acceptance-bound артефактов:

1. Запустите независимого eligible ревьюера (`convene --tier light
   --gate acceptance` либо полный рой с `--gate acceptance`).
2. Наблюдайте через `status`, если review выполняется долго.
3. Проверьте каждый finding по real artifacts и зафиксируйте диспозицию
   `agree`/`partial`/`disagree`/`withdrawn`/`out_of_scope` (для gate — через
   `gate-verdict --dispositions-file`, обязателен на 1-й итерации). В маршруте
   Claude → Codex/GPT дополнительно явно зафиксируйте для каждого finding
   оценку риска оверинжиниринга с evidence (по шаблону
   `references/review-report-template.md`); вывод о риске оверинжиниринга сам
   обязан ссылаться на artifacts/evidence, без них он не основание не
   устранять подтверждённый риск.
4. Добавьте собственные findings как `C-01...`, когда это нужно.
5. Если source artifacts изменились после rework — дельта-итерация
   (`gate-verdict` повторно; ядро выполнит `sync` sandbox).
6. Останавливайтесь на согласии (`accept`) или эскалации после 3 итераций.
   `conditional_accept` — промежуточный статус (F-007): примите явное решение
   (`--conditional-decision confirm|reject`), auto-approve не происходит.
7. **🔴 MUST — `close <session_id>`, как только review больше не нужен** (см.
   «КРИТИЧНО: cleanup-дисциплина»). Это не «когда удобно», а обязательный шаг
   закрытия: без него sandbox и сессия остаются на диске навсегда. `close`
   вызывается даже если ревью завершилось отказом/ошибкой.
8. Зафиксируйте final report: unified findings, disagreements с обеими
   позициями, iteration count, recommendation, session id, **cleanup status**
   и релевантное status/log evidence. Перед закрытием задачи прогоните
   CHECKPOINT из раздела «КРИТИЧНО».

### Finalization Gate Protocol (blocking)

Для final orchestrator completion review (`--gate completion`; промпт —
`references/final-orchestrator-completion-review-prompt.md`):

1. Подготовьте completion claim с direct evidence (`gate-verdict
   --claim-file`).
2. Запустите gate-ревьюера, отличного от caller, и передайте evidence
   package.
3. Наблюдайте через `status`, если review выполняется долго.
4. Разберите findings и зафиксируйте свою позицию.
5. Выполните rework или предоставьте evidence, затем запросите delta review
   при существенных изменениях (повторный `gate-verdict`).
6. После 3 итераций с material disagreement — стоп и эскалация пользователю с
   обеими позициями и evidence (ядро фиксирует `escalated` в session и review
   trace).
7. **🔴 MUST — `close` после задокументированного приговора PASS или user
   override'а.** Закрытие gate-review обязательно ВО ВСЕХ исходах, включая
   эскалацию после 3 итераций. Затем прогоните CHECKPOINT.

**Запрещено:**

- Объявлять completion без `APPROVE_COMPLETION`, user override или
  зафиксированной эскалации после 3 итераций. Gate-ревьюер при completion
  сужает свою advisory authority: он не определяет стратегию и не внедряет
  фиксы, но его `BLOCK_COMPLETION` блокирует completion задачи; обходить
  final completion gate нельзя.
- Закрывать задачу при наличии `.swarm-sessions/` либо живых/содержащих payload
  каталогов в `.review-sandboxes/`. Каталог, содержащий только стабильный
  invocation lock, является терминальным закрытым tombstone, а не активным
  sandbox, и сам по себе completion не блокирует. Каноническую форму tombstone
  определяет harness, caller не должен дублировать имя lock-файла.

## Наблюдаемость: heartbeat/liveness и progress.jsonl (FR-13)

`status <session_id>` (JSON) в любой момент показывает: состояние state
machine, тариф, участников с линзами и счётчиками вызовов/retries,
unresponsive, счётчики находок (принято/отклонено/маршрутизировано/
автоподтверждено), статусы тредов, арбитражи, gate-состояние, wall-clock
(`elapsed/budget/status ok|warn|exceeded`), плюс:

- **поля активности и liveness-класс каждого участника**: `activity
  {last_activity_at, last_heartbeat_at}` и `liveness.class`:
  - `active` — «модель думает»: события CLI идут (активность свежее порога
    тишины, default 120 с — `--silence-threshold-sec`);
  - `quiet` — тишина дольше порога при живом watcher: диагностический маркер,
    **не kill** (длинный мыслительный ход легален; вмешательство — ваше
    решение);
  - `dead_watcher` — heartbeat старше 10 с: процесс-адаптер не жив (жёсткий
    сигнал);
- **`last_checkpoint`** — последний чекпоинт `progress.jsonl` сессии.

`progress.jsonl` (в каталоге сессии, append-only) — семантические чекпоинты на
границах этапов, не поток сознания: `convened`, `tour1_complete`,
`dedup_complete`, `tour2_complete`, `tour3_complete`, `tour4_complete`,
`arbitration_complete`, `report_ready`, `rereview_complete`, `gate_complete`,
`closed`. Строка: `{ts, session_id, tool: "swarm", checkpoint, summary,
counters}` — counters это снимок (invocations, findings, unique_unconfirmed,
unresponsive). Читайте файл напрямую или последний чекпоинт через `status`.

## Track record (FR-12)

Durable `.swarm-track-record/` (`observations.jsonl` — источник истины,
`strengths.json` — генерируемый кэш, `config.json` — счётчики,
`gate-outcomes.jsonl` — durable след исходов блокирующих gate, F-006); НЕ
смешивается с консилиумным и исключён из cleanup-checkpoint. Пишется ядром на
`report`/`close` (идемпотентно): одна observation на участника на сессию с
метриками (уникальные подтверждённые/неподтверждённые, неуникальные и охват,
upheld/overruled как автор, атаки и их precision, доля дошедших до `fixed`,
nit P4 отдельным счётчиком, in-lens/out-of-lens, quota-режим,
calibration_run). Ячейка формул — «модель × роль» (линза), decay half-life 8,
floor n_eff ≥ 3; категория — атрибут наблюдения, не ячейка.

**Из strength-статистики исключаются целиком** наблюдения с маркерами
`forced` (forced-линза критичного пути — не свободный выбор модели) и
`gate_pass` (двойная роль gate-ревьюера). Маркеры `auto_confirmed_overridden`
и `calibration_run` записываются, но наблюдение НЕ исключают. Пустые ячейки
(< 3 наблюдений) → round-robin при назначении линз. Подробная схема —
`references/track-record-swarm.md`.

## 🔴 КРИТИЧНО: cleanup-дисциплина

> **Уровень: CRITICAL / MUST.** По прецеденту gate-правила `cross-provider-review.md`, усилено
> двойным контуром. Автоматической уборки нет — ни atexit, ни обработчиков
> сигналов, ни TTL/age-sweep: только парность и checkpoint. Осиротевшие
> sandbox и каталоги сессий остаются на диске навсегда.

| Требование | Описание |
|-----------|----------|
| Парность convene↔close | Любой `convene` ОБЯЗАН иметь парный `close` на ЛЮБОМ пути завершения: репорт, эскалация gate, отказ от ревью, ошибка адаптера, деградация по wall-clock |
| Парность start↔close участников | Каждый участник — сессия адаптера с собственным sandbox в `.review-sandboxes/`; `close` роя закрывает ВСЕХ участников, включая focused-сессии re-review |
| Cleanup fail-closed | При неуспешном close участника сессия НЕ удаляется и НЕ помечается closed: `cleanup.status=failed`, exit code 5; повторный `close` идемпотентен — **retry обязателен**, пока cleanup не пройдёт |
| `--keep` только forensic | `close --keep --keep-reason "<письменная причина>"` сохраняет эфемерный каталог сессии — ТОЛЬКО для forensic/debug с явной письменной причиной (без причины ядро откажет) |
| Lock-only tombstone | После успешного adapter `close` допустим каталог review, который `adapter_contract.is_lock_only_tombstone()` признаёт каноническим: payload уже удалён, provider-сессия закрыта, lock не удерживается. Это терминальное координационное состояние, не активный sandbox |
| Отчёт о cleanup | В итоговый отчёт задачи записывается cleanup status каждой сессии и каждого участника: `closed` или обоснованный `kept` |

**✅ ДВОЙНОЙ CHECKPOINT перед закрытием задачи (обязателен):**

```bash
ls -1 .swarm-sessions/ 2>/dev/null | wc -l    # ожидается 0
PYTHONPATH="$SKILL_DIR/../review-harness/scripts" python3 - <<'PY'  # ожидается 0 payload/live sandbox
from pathlib import Path
from adapter_contract import REVIEW_ROOT, is_lock_only_tombstone
root = Path(REVIEW_ROOT)
active = [str(path) for path in (root.iterdir() if root.is_dir() else ())
          if not is_lock_only_tombstone(path)]
print(len(active))
PY
```

Если первая проверка либо число payload/live sandbox не равно 0 — задача НЕ
завершена по части cleanup: закройте оставшиеся сессии и только потом закрывайте
задачу. Lock-only tombstone вручную не удаляйте и не ослабляйте классификатор:
неожиданный файл, каталог, symlink или иной payload обязан считаться активным и
блокировать completion. `.swarm-track-record/` — durable-хранилище, из checkpoint
ИСКЛЮЧЕНО: не удалять.

## Диагностика сбоев

- **Unresponsive-участник**: таймаут вызова (per-invocation, default 900 с,
  `--timeout-sec`) → `unresponsive` без retry; ошибка адаптера или невалидный
  structured-ход → ровно один retry → `unresponsive`. Протокол продолжается
  без него; факт — в `status`, инцидентах репорта и observations.
- **Отказы ядра (fail-closed)**: exit 2 — нарушение протокола/state machine/
  валидации/кворума; exit 3 — аварийное завершение (wall-clock); exit 5 —
  cleanup failed (retry `close`). Не пытайтесь «обойти» отказ ручными правками
  состояния сессии.
- **Wall-clock бюджет (NFR-08)**: сессия роя `max(3600, W×(T+240)+1800)` с,
  жёсткий потолок 16620 с, предупреждение на 75 %; W — модель волн,
  пересчитывается после `dedup` по фактическому U, а для keyed-волн — по
  точным upper bounds scheduler-slots туров 2–4, учитывающим cap и глубину
  FIFO-lane. При достижении «бюджет
  минус одна волна» новые волны не стартуют: сессия помечается `degraded`,
  доводите до репорта из текущего состояния (незавершённые треды —
  `unvalidated`, не тихий обрыв) и закрывайте.
- **Где смотреть**: `status <id>`; промпты ходов — `.swarm-sessions/<id>/prompts/`;
  логи и история адаптеров участников — `status`/`log` адаптера из
  `review-harness/scripts/adapters/` по `review_id` участника.

## Безопасность

- Ревьюеры работают в скопированных sandbox workspaces (`.review-sandboxes/`),
  а не в реальном проекте; реальный проект менять запрещено.
- По умолчанию — focused-paths копии, НЕ `--full-context`.
- Промпты и system-prompts адаптеров включают read-only инструкции; codex —
  read-only sandbox mode; claude — только `Read,Grep,Glob,LS`; kimi —
  встроенный read-only agent profile.
- Оркестратор остаётся ответственным за rework и финальное решение; для
  final completion review требуется независимый `APPROVE_COMPLETION` до того,
  как задача может быть объявлена завершённой.

## Что вызывающему НЕ нужно знать

Нижележащие детали — для maintainer'ов механизма (все — в `references/`):

- `references/finding-schema.md` — схема находки, таблица severity P1–P5,
  валидация location, дедуп.
- `references/verdict-schemas.md` — схемы ходов туров 2–4, арбитража,
  re-review и gate-вердикта.
- `references/track-record-swarm.md` — схема `.swarm-track-record/`, метрики
  и проекция strengths, калибровочная квота.
- `references/criticality-map.md` — machine-readable карта критичности и
  привязка forced-линз (эскалатор тарифа по вердикту CMAP-01; обоснования
  glob'ов и политика обновления —
  `tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md`,
  guard-тесты — `tests/integration/test_criticality_map.py`).
- `references/design-decisions-e2e02.md` — зафиксированные дизайн-решения по
  находкам E2E-02 (F-010: семантика `content_retry_decision`; F-011:
  bounded policy assertion `--caller` и граница threat model).
- `references/report-template.md` — шаблон репорта полного роя;
  `references/review-report-template.md` — шаблон репорта лёгкого тарифа
  (наследуемый, включая поле оценки риска оверинжиниринга).
- `references/review-prompt.md`,
  `references/final-orchestrator-completion-review-prompt.md` — наследуемые
  промпты ревьюера и finalization gate.
- Общий слой (адаптеры, контракт `start/ask/status/close/sync`, реестр
  `adapters.yaml`, track record, liveness/progress, квотный выбор) —
  `review-harness` (библиотека, не инструмент): `{{runtime-ref:framework/skills/tool-usage/review/review-harness/SKILL.md}}`,
  канонические контракты — `review-harness/references/adapter-contract.md` и
  `track-record-schema.md`.

---
depends_on:
  - framework/skills/tool-usage/review/review-harness/SKILL.md
---

---
name: full-cycle
description: "Для средних и сложных задач вести полный цикл с ревью"
---

# Воркфлоу: Полный цикл разработки (Full Cycle)

> Детерминированный воркфлоу с кросс-ревью на каждой фазе. Для задач средней и высокой сложности.

> **Место в ретиринге (Слой 3, read-on-choice).** Это детальная фазовая механика. Дисциплина
> оркестрации и форма фаз уже durable в **профиле оркестратора** (`framework/subagents/orchestrator.md`,
> Слой 2). Оркестратор НЕ «загружает этот документ как правило» — он поднимает фазовую механику
> отсюда **по входу в фазу**, из своего профиля. Запуск full-цикла — решение Lead-слоя
> (классификация «средняя/сложная»), а не загрузка внешнего документа в произвольную сессию.

## Фазы

### Phase 0: Классификация (Explorer → Economy)

Explorer исследует кодовую базу → модули, графы вызовов, зависимости. Оркестратор классифицирует (Lead-слой профиля): Простая → short-цикл (навык `quick-fix`); Средняя/Сложная → Phase 1.

Артефакты Explorer передаются в Phase 1 и Phase 2 как контекст.

### Phase 1: Анализ (Analyst → Mid/High)

Вход: задача + `explorer-context.md`. Analyst создаёт спеку MADR 4.0 + RFC 2119. Ревью Reviewer (Premium). Макс. 3 итерации BLOCK. Ревью + cross-provider-review + **STOP: ждём ОК пользователя**.

В Test Plan Analyst ОБЯЗАН разнести требования по runtime-слоям и назначить обязательный тип
проверки: серверная логика/серверный контекст → YaxUnit; UI/клиентский контекст → сценарный
UI/BDD-тест; связанный пользовательский процесс → end-to-end сценарий процесса; интеграция/фоновые
задания → integration/job-проверка. Для существующего покрытия план должен явно сказать, какой тест
актуализируется и перепрогоняется; если покрытия нет — какой тест создаётся.

Approval gate Phase 1 нужен, потому что спецификация фиксирует бизнес-решения (уровни RFC 2119, границы scope, выбор между альтернативами), которые пользователь ОБЯЗАН подтвердить ДО того, как Architect потратит ресурс на дизайн, опирающийся на возможно неверный контракт. Пропуск этого gate исторически приводил к множественным итерациям: cross-provider-review или Architect находили противоречия в спеке, которые можно было устранить одним уточнением у пользователя на этой стадии.

### Phase 2: Архитектура (Architect → High/Premium)

Вход: утверждённая спека + `explorer-context.md`. Architect → `technical-design.md` + `task-breakdown.json`. Ревью + **STOP: ждём ОК пользователя**.

### Phase 3: ПОСЛЕДОВАТЕЛЬНО (3a → 3b → 3c → 3d)

Фазы 3a–3d идут строго последовательно. Каждая следующая начинается только после ревью предыдущей (и cross-provider-review в advisory).

- **3a (Scenario-Author → Mid):** перед написанием новых UI/форменных сценариев проводит исследование формы через Vanessa MCP workflow (`vanessa-authoring`: запуск VA manager → `connect_test_client` → VA-tools → `close_test_client`) и фиксирует точные команды/элементы/обязательные поля в своём контексте. Затем intent-сценарии спеки → `.feature` Vanessa с пометкой `# unknown_step_candidate` для не найденных шагов. Ревью (scope=bdd).
- **3b (Developer-Tests → Mid/High):** MUST-сценарии Test Plan, относящиеся к серверной логике/серверному контексту, → YaxUnit unit/интеграционные тесты (Red). Если серверный метод изменён и тест уже есть — актуализирует и перепрогоняет его; если теста нет — создаёт. Ревью (scope=tests).
- **3c (Scenario-Coder → Mid):** делает `.feature` 3a исполняемыми — подбирает/реализует шаги Vanessa (`@exportscenarios` или, как escape hatch, BSL-шаги в `vanessa-tests/support/`), заменяет `unknown_step_candidate`. Если шаг зависит от реального UI-состояния, проверяет его через Vanessa MCP workflow и закрывает тест-клиент после проверки. Red-гейт: `v8-runner test va` на сценариях задачи показывает падение на отсутствующей прод-логике, не на неизвестных шагах. Ревью (scope=bdd-steps).
- **3d (Developer-Code → High):** вход — всё из Phase 2 + тесты 3b + Red-executable `.feature` 3a/3c. Пишет код (Green для unit-тестов Phase 3b И сценариев 3a). При `test_failure` + `suspected_test_error` → Reviewer-арбитраж → маршрутизация (в 3b если юнит-тест, в 3c если шаг, иначе в 3d).

**Зачем разделены 3a и 3c.** Scenario-Author отвечает за **что** должно произойти (бизнес-намерение, читаемый Gherkin). Scenario-Coder отвечает за **как** это выражено в шагах Vanessa (техническая реализация step-library, переиспользование). Раньше эту работу никто явно не делал — шаги либо висели `TODO`, либо доделывались Developer-Code с размытием Green-гейта. Разделение ролей даёт: (а) чистый Red-гейт на уровне сценариев до написания прод-кода, (б) ответственного за качество и переиспользование step-library, (в) возможность параметризовать шаги по функциональности предметной области, а не по задаче.

**Место vendor workflow Vanessa MCP.** Исследовательский MCP workflow не заменяет Red/Green-гейты и не является отдельной фазой full-cycle. Он является обязательной техникой внутри 3a/3c для UI/форменных сценариев: сначала получить runtime-карту формы и справочных данных через live VA-tools, затем писать или чинить Gherkin. Для визуальных артефактов применяется `va-visual-check`: VA MCP — предпочтительный маршрут, browser/web fallback допустим после фиксации выполненных VA-шагов, причины и остаточного риска.

### Phase 4: Покрытие и регрессия (Tester → Mid/High)

Tester запускает все тесты, дописывает edge-cases, интеграционные, регрессионные. Перед закрытием
Phase 4 он проверяет матрицу покрытия из Test Plan: каждый server/server-context MUST закрыт
YaxUnit, каждый UI/client-context MUST закрыт сценарным UI/BDD-тестом, каждый связанный процесс —
end-to-end сценарием. Ревью (High). Phase 4 НЕ дублирует Phase 3.

---

## Передача артефактов

| От → К | Артефакт |
|--------|----------|
| 0 → 1, 2 | `explorer-context.md` |
| 1 → 2 | `spec.md` |
| 2 → 3a, 3b | spec + technical-design + task-breakdown.json |
| 3a → 3c | `.feature` (intent) с `unknown_step_candidate` |
| 3b → 3d | test-модули (.bsl) |
| 3c → 3d | `.feature` с реализованными шагами + новые `@exportscenarios` / BSL-шаги в `vanessa-tests/support/` |
| 3d → 4 | BSL + `.feature` + зелёные unit и сценарные тесты |

**Обязательные поля:** Спецификация — Context, Requirements, Scope, Test Plan. Technical Design — компоненты, интерфейсы. Task Breakdown JSON — task_id, task_type, depends_on, spec_refs, критерии завершения. Код — coding-standards. Тесты — связь с MUST-сценариями.

---

## Обработка ошибок

| Ситуация | Действие |
|----------|----------|
| BLOCK, <= 3 итерации | Вернуть автору |
| BLOCK, > 3 | Эскалация пользователю |
| Пользователь отклонил Phase 1 | Analyst перерабатывает |
| Пользователь отклонил Phase 2 | Architect перерабатывает |
| `test_failure` в Phase 3d | Developer-Code: если свой код → исправить; если unit-тест → `suspected_test_error` → Reviewer-арбитраж → 3b; если шаг Vanessa → `suspected_step_error` → Reviewer-арбитраж → 3c |
| Шаг в Phase 3c требует API вне `technical-design.md` | Scenario-Coder: `clarification_needed` → Architect (Phase 2) доопределяет контракт |
| Сценарий Phase 3c зелёный до прод-кода | Признак мока в шаге → Scenario-Coder удаляет мок, перезапускает Red-гейт |
| `test_failure` в Phase 4 | Tester: свой тест → исправить; баг в коде → `implementation_error` → Developer |
| `check_syntax` падение | Developer исправляет до ревью |
| MCP/VA недоступен для UI-задачи | Применить fallback-правила `va-visual-check`; если fallback не даёт достаточного сигнала — blocker → эскалация |

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/skills/agent-process/quick-fix/SKILL.md
  - framework/subagents/explorer.md
  - framework/subagents/analyst.md
  - framework/subagents/architect.md
  - framework/subagents/scenario-author.md
  - framework/subagents/developer-tests.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/developer-code.md
  - framework/subagents/tester.md
  - framework/subagents/reviewer.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---

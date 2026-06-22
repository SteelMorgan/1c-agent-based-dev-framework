---
name: orchestrator
description: >
  Указатель на манинг оркестрации. После ретиринга (манифест §6, §7.2) операционный манинг
  оркестратора (Слой 1 — Lead/диспетчер, Слой 2 — дисциплина) переехал в ПРОФИЛЬ главного потока
  framework/subagents/orchestrator.md. Детальная фазовая механика (Слой 3) — framework/workflows/full-cycle/SKILL.md.
  Этот файл сохранён как стабильная точка входа и носитель ссылок depends_on; он НЕ дублирует тело манинга.
---

# Оркестратор: мета-воркфлоу (указатель)

> **Важно.** Раньше весь операционный манинг оркестратора жил в этом always-on документе и наследовался
> сабагентами — ровно то раздувание канала A, которое устранил ретиринг (см. манифест
> `docs/rules-skills-retiering/manifest.md`, §6, §7.1, §7.2). Теперь манинг — durable-идентичность
> главного потока в его системном промпте, а не загружаемый документ.

## Где что живёт

| Слой | Содержание | Место жительства | Durability |
|------|------------|------------------|------------|
| **1. Lead / диспетчер** | классификация; выбор цикла short/full; для short — self vs delegate под guard quick-fix | **профиль** `framework/subagents/orchestrator.md` | durable, каждый запрос (main-only) |
| **2. Дисциплина оркестрации** | «не исполняю — делегирую», routing, gates, review-cycle, BUG-routing, фильтр эскалации, cross-provider (§7), Infostart-аудит (§9), протокол ЛОГ | **профиль** `framework/subagents/orchestrator.md` | durable; активна только в full-режиме |
| **3. Детальная фазовая механика** | фазы Phase 0…4, передача артефактов, обработка ошибок | `framework/workflows/full-cycle/SKILL.md` | read-on-choice (по входу в фазу) |
| **Якорь / кросс-харнес мост** | тонкий само-промотирующий стаб, поднимающий профиль на любом харнесе | `framework/rules/framework-bootstrap/SKILL.md` (always-on) | переживает компакт, ре-триггерится |

## Как это работает в потоке

1. Главный поток стартует под профилем оркестратора (`--append-system-prompt` — рекомендуемый дефолт,
   либо `--agent orchestrator`; см. профиль § «Способ запуска» и манифест §6.1). На харнесах без этих
   флагов профиль поднимает портативный стаб `framework-bootstrap` (манифест §7.3).
2. Lead классифицирует задачу (Слой 1 профиля) → выбирает short (навык `quick-fix`) или full.
3. В full-режиме оркестратор работает по дисциплине Слоя 2 (профиль) и поднимает детальную фазовую
   механику из `full-cycle.md` по входу в каждую фазу.
4. «Эскалация quick-fix → full» = оркестратор у себя же поднимает фазовый манинг (он уже в профиле),
   а не передаёт во внешний документ.

## Запрет «не исполнять сам» — scoped к full-режиму

Главный агент — это **Lead**, надевающий одну из шляп, а не «всегда оркестратор». Запрет
«оркестратор НЕ исполнитель» действует ТОЛЬКО в full-режиме. В Lead/short-режиме main исполняет сам
или делегирует одного сабагента в границах quick-fix (`< 20 строк, 1 файл, без новых объектов
метаданных, без архитектуры`) с обязательным verify-шагом. Полные формулировки — в профиле
(Слой 1 §1.3, Слой 2 «ЗАПРЕЩЕНО»).

---
depends_on:
  - framework/subagents/orchestrator.md
  - framework/workflows/full-cycle/SKILL.md
  - framework/skills/framework-meta/quick-fix/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
  - framework/subagents/scenario-author.md
  - framework/subagents/scenario-coder.md
  - framework/subagents/debugger.md
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/runtime-investigation/SKILL.md
---

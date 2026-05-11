---
name: rlm-workflow
description: Правила работы с RLM-Toolkit (постоянная память между сессиями Claude Code). Ритуалы «контекст», «суммаризируем», «новая задача», уровни H-MEM, формат PENDING. Обязательно для оркестратора и любого агента, обслуживающего жизненный цикл сессии.
alwaysApply: true
---

# RLM Workflow

## Когда применять

| Ситуация | Ритуал |
|---|---|
| Пользователь начал сессию словом **«контекст»** / **«context»** | [«контекст»](#ритуал-контекст) |
| Пользователь сказал **«суммаризируем»** / **«summarize»** | [«суммаризируем»](#ритуал-суммаризируем) |
| Пользователь сказал **«новая задача»** / **«new task»** | [«новая задача»](#ритуал-новая-задача) |
| CRIT-инжект из `context-monitor.sh` (`≥80%` или `≥300k tokens`) | «суммаризируем» |
| Срабатывание `pre-compact.sh` перед компактом | «суммаризируем», короткий вид: только шаги 1, 3, 4 |
| Узнал устойчивый паттерн / принял архитектурное решение по ходу работы | [Запись по ходу работы](#запись-по-ходу-работы) |

## Ритуал «контекст»

1. `rlm_start_session(restore=true)` — ОБЯЗАТЕЛЕН до любых других вызовов RLM
2. `rlm_enterprise_context(query="<краткое описание текущей задачи или статус проекта>", task_hint="<тип задачи>")`
3. `rlm_search_facts(query="PENDING tasks next session", keyword_weight=0.8, semantic_weight=0.1, recency_weight=0.1, top_k=15)`
   - Фильтр: показывать факты без тега `[project: ...]` ИЛИ с `[project: <текущий проект>]`. Скрывать `[project: <чужой>]`
4. Если задача относится к домену — `rlm_get_facts_by_domain(domain="<имя>")`
5. Вывод пользователю: **Pending tasks** (из шага 3) первым списком, затем **Recent decisions and key facts** (шаги 2, 4)
6. PENDING непустой → объявить первую задачу и сразу взяться за неё. Уточнять только если PENDING пуст или задачи требуют разъяснения

## Ритуал «суммаризируем»

1. `cat ~/.claude/autocapture-buffer.jsonl 2>/dev/null || echo "empty"` → сгруппировать `Edit/Write` (файлы) и `Bash` (команды); затем очистить буфер: `: > ~/.claude/autocapture-buffer.jsonl`
2. Гигиена: `rlm_get_stale_facts()` (при нужде `rlm_delete_fact`); если ≥5 фактов — `rlm_consolidate_facts(min_facts=5)`
3. Запись:
   - факты — `rlm_add_hierarchical_fact(content, level, domain, …)`
   - решения — `rlm_record_causal_decision(decision, reasons, consequences, constraints, alternatives)`
   - финал — `rlm_sync_state()`
4. **ОБЯЗАТЕЛЬНЫЙ** PENDING-факт:
   ```
   rlm_add_hierarchical_fact(
     content="PENDING tasks next session [task_id: <id>]: 1) <task1>. 2) <task2>.",
     domain="workflow", level=1, ttl_days=30
   )
   ```
   - Префикс `PENDING tasks next session:` — без него ритуал «контекст» не найдёт факт
   - Сомнительное помечать ❓; cross-project — отдельный факт с `[project: <name>]`; пусто → `PENDING tasks next session: none`
5. `git status`: завершённые когерентные изменения → commit; WIP → в PENDING; пусто → skip
6. Кратко отчитаться: что сохранено + git-статус
7. Завершить сообщение РОВНО строкой `Контекст сохранён в RLM. Жми /clear.` Никаких добавлений после неё. При сбое шагов 1-6 — короткая пометка о сбое перед этой строкой, но строка обязательна

## Ритуал «новая задача»

1. `rlm_start_session(restore=false)` + очистить autocapture-буфер
2. Brainstorm если новая фича; багфикс — пропустить
3. `task_id = <project>-<feature>-YYYY-MM-DD`
4. `rlm_add_hierarchical_fact(content="TASK START [task_id]: <description>. Approach: <solo/team>. Expected files: <list>.", domain="retrospective", level=1)`
5. Сложные задачи → `TeamCreate` + RLM-блок в промпте сабагентов

## Запись по ходу работы

| Что узнал/сделал | Куда |
|---|---|
| Универсальный паттерн / антипаттерн | `rlm_add_hierarchical_fact(level=1, domain="retrospective")` |
| Деталь конкретного модуля/файла | `rlm_add_hierarchical_fact(level=2, domain="<домен>", module="<путь>", code_ref="<file:line>")` |
| Решение с альтернативами | `rlm_record_causal_decision(decision, reasons, consequences, alternatives, constraints)` |
| Временная заметка / гипотеза | `rlm_add_hierarchical_fact(level=3, ttl_days=7)` — авто-консолидация |

## Уровни H-MEM

| `level` | Имя | Когда |
|---|---|---|
| `0` | `L0_PROJECT` | Загружается всегда. Критичные глобальные знания, project pitfalls, инвариантные ограничения. Писать редко |
| `1` | `L1_DOMAIN` | По контексту. PENDING, решения сессии, устойчивые паттерны |
| `2` | `L2_MODULE` | По запросу. Детали реализации, конфиги, находки на уровне модуля |
| `3` | `L3_CODE` | Временные. Код-заметки, отладка. Авто-сворачивается в L2/L1 через `rlm_consolidate_facts` |

## SHOULD

- Cross-project факты — префикс `[project: <name>]` в `content`, чтобы фильтр в «контексте» отделял свой проект от чужих
- TTL: L3 → 7..14 дней; L2 → 30..90; L1/L0 — без TTL
- Архитектурные решения — через `rlm_record_causal_decision`, не одинокими `add_hierarchical_fact` (даёт причинно-следственную цепочку)
- После CRIT и сохранения — `rlm_get_hierarchy_stats`, `total_facts` должен вырасти; иначе сохранение реально не прошло
- Первая работа с проектом — `rlm_discover_project(project_root, task_hint)` один раз. Без него L0-сидов нет, факты пишутся в пустую структуру

## Анти-паттерны

| Анти-паттерн | Последствие |
|---|---|
| Запись факта без `rlm_start_session` | Молчаливый провал; узнаешь только при `get_hierarchy_stats` |
| PENDING без префикса `PENDING tasks next session:` | Шаг 3 ритуала «контекст» не найдёт факт — следующая сессия думает что всё сделано |
| Сохранить в RLM без очистки `autocapture-buffer.jsonl` | Следующий CRIT повторно перепишет старый буфер — дубли |
| Завершить «суммаризируем» произвольной фразой | Пользователь не увидит сигнал-маркер и не поймёт, безопасно ли `/clear` |
| Писать всё в L0 «на всякий случай» | L0 раздуется и попадёт во все будущие сессии — каждый старт станет дороже |

---
depends_on:
  - framework/rules/agent-context-protocol.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

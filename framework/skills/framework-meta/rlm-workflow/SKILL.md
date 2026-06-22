---
name: rlm-workflow
description: MUST use WHEN записываешь переиспользуемое знание в RLM (паттерн / арх-решение / устойчивый домен-факт) ИЛИ читаешь его перед нетривиальной задачей/решением в домене. Provides раскладку native-push vs RLM-pull, инструменты записи и чтения RLM, уровни H-MEM и гигиену.
alwaysApply: false
---
# RLM Workflow — запись и чтение знаний

> RLM — это **pull-хранилище универсальных переиспользуемых знаний**: паттерны, арх-решения, домен-факты 1С/БСП, находки по модулям. Достаётся по теме семантическим поиском (эмбеддинги, дообученные под 1С) и НЕ висит в контексте. Триггеры «когда писать / когда читать» — в правиле `rlm-workflow`; здесь — как именно.

## Раскладка памяти

| Знание | Слой | Инструмент |
|---|---|---|
| Always-on ядро (безопасность, инварианты, стоячие предпочтения) | native push | `MEMORY.md` + `memory/*.md` |
| Универсальное переиспользуемое (паттерны, решения, домен-факты) | **RLM pull** | этот навык |
| Transient состояние задачи (PENDING, следующий шаг, WIP) | agent-context | `task_dir/.context/*.md` |

## Запись в RLM

**Предусловие (MUST):** `rlm_start_session()` до любых записей — иначе молчаливый провал (узнаешь только через `rlm_get_hierarchy_stats`).

| Что записать | Вызов |
|---|---|
| Универсальный паттерн / антипаттерн | `rlm_add_hierarchical_fact(content, level=1, domain="retrospective")` |
| Деталь конкретного модуля/файла | `rlm_add_hierarchical_fact(content, level=2, domain="<домен>", module="<путь>", code_ref="<file:line>")` |
| Временная заметка / гипотеза | `rlm_add_hierarchical_fact(content, level=3, ttl_days=7)` — авто-консолидация |
| Решение с альтернативами | `rlm_record_causal_decision(decision, reasons, consequences, constraints, alternatives)` |

Финал записи — `rlm_sync_state()`.

**Уровни H-MEM:**

| `level` | Имя | Когда |
|---|---|---|
| `0` | `L0_PROJECT` | Грузится всегда. Критичные глобальные знания, project pitfalls. Писать **редко** (раздувает каждый старт) |
| `1` | `L1_DOMAIN` | По контексту. Устойчивые паттерны, решения сессии |
| `2` | `L2_MODULE` | По запросу. Детали реализации, находки на уровне модуля |
| `3` | `L3_CODE` | Временные. Авто-сворачивается в L2/L1 через `rlm_consolidate_facts` |

## Чтение из RLM

На границе работы (вход в нетривиальную задачу / перед арх-решением / при повторяющейся проблеме) — **один** вызов, а не перебор инструментов:

| Цель | Вызов |
|---|---|
| Контекст по теме (рекомендуемый, one-call) | `rlm_enterprise_context(query="<домен/симптом>", max_tokens=3000)` — авто-роутинг L0 + релевантные L1/L2 + causal-цепочки |
| Только релевантные факты в бюджете | `rlm_route_context(query, max_tokens=2000)` — L0 всегда + L1/L2 по похожести |
| Точечный гибридный поиск | `rlm_search_facts(query, semantic_weight, keyword_weight, recency_weight, top_k)` |

Веса в `rlm_search_facts`: смысловой запрос → поднять `semantic_weight`; поиск по точному термину/идентификатору → поднять `keyword_weight`; «что недавно меняли» → `recency_weight`.

## Гигиена

- `rlm_get_stale_facts()` → при нужде `rlm_delete_fact()`.
- ≥5 гранулярных фактов одной темы → `rlm_consolidate_facts(min_facts=5)` (L3→L2→L1 + дедуп).
- TTL: L3 → 7..14 дней; L2 → 30..90; L1/L0 — без TTL.
- Первая работа с проектом — `rlm_discover_project(project_root, task_hint)` один раз (сеет L0-структуру).

## Анти-паттерны

| Анти-паттерн | Последствие |
|---|---|
| Запись без `rlm_start_session` | Молчаливый провал |
| Писать всё в L0 «на всякий случай» | L0 грузится всегда → каждый старт дороже |
| Держать универсальное знание в native always-on ядре | Раздувает контекст каждый ход — этому место в RLM (pull) |
| Класть transient (PENDING, WIP) в RLM | Засоряет хранилище знаний; transient → agent-context |
| Читать RLM на каждом ходу | Pull вырождается в push; читать на границе работы |

---
depends_on:
  - framework/rules/rlm-workflow/SKILL.md
  - framework/rules/agent-context-protocol/SKILL.md
  - framework/rules/search-before-write/SKILL.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

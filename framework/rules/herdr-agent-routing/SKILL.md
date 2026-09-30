---
name: herdr-agent-routing
description: Read-on-choice контракт Оркестратора для маршрутизации phase owner по семействам внутри и вне Herdr.
alwaysApply: false
---

# Herdr-aware маршрутизация агентов

Правило определяет только выбор транспорта и ownership. Синтаксис команд и безопасность управления панелями берутся из vendor-навыка `herdr`; не дублируй и не изменяй его.

## Проверка среды

Перед каждым запуском phase owner вычисли:

```bash
test "${HERDR_ENV:-}" = 1
```

- `HERDR_ENV=1` — только первый признак Herdr-контекста. Дополнительно `command -v herdr`, `herdr status --json` и `herdr pane current --current` должны успешно подтвердить доступный client/server и текущую pane; иначе верни `BLOCKED_HERDR_CONTEXT_INVALID`.
- Любое другое значение или отсутствие переменной — управление Herdr запрещено; используй только qualified модели семейства текущего harness.

`current_family` берётся из фактической runtime identity harness (`claude`, `gpt/codex`, `kimi`), а не угадывается по тексту задачи. Внутри Herdr identity можно подтвердить через current pane/agent metadata. Неизвестная family — `BLOCK`, не silent fallback.

`target_family`, exact model и effort берутся из канонической role-routing matrix. Оркестратор вправе изменить effort, но обязан записать причину и сохранить capability/reviewer floor.

## Алгоритм внутри Herdr

1. Запиши в orchestration trace: role, `current_family`, `target_family`, exact model/effort, route и rationale.
2. Если `target_family == current_family`, запускай штатного native child текущего harness. Это полноценный phase owner, а не консультация; передай self-contained handoff без fork истории.
3. Если family различаются:
   - прочитай vendor-навык `herdr` и актуальную CLI help;
   - топология «1 исполнитель = 1 pane = 1 tab»: создай для исполнителя отдельный tab в текущем workspace/cwd (`herdr tab create ... --no-focus` или `pane move <pane> --new-tab --label <agent-name> --no-focus`) — НЕ сплитуй tab оркестратора; это явное требование пользователя к топологии, перекрывающее vendor-default «sibling pane в текущем tab»;
   - для `target_family=claude` проверь в целевой интерактивной shell, что alias `cc` раскрывается в `/home/vscode/bin/claude-safe.sh`, и запусти его через `herdr pane run <pane-id> "cc <provider-native-args...>"`;
   - для `target_family=gpt/codex` аналогично проверь alias `cx` → `/home/vscode/bin/codex-safe.sh` и запусти `herdr pane run <pane-id> "cx <provider-native-args...>"`;
   - `cc`/`cx` являются shell aliases, а не Herdr kinds: `--kind cc|cx` недопустим. Для Claude/Codex также запрещён `herdr agent start --kind claude|codex`, потому что он обходит safe-wrapper и запускает raw executable без полного проектного runtime-контракта;
   - если обязательный alias отсутствует или раскрывается не в ожидаемый wrapper, верни `BLOCKED_AGENT_ALIAS_UNAVAILABLE`; silent fallback на `claude`/`codex` запрещён;
   - дождись обнаружения агента по `pane id` в пределах startup timeout, назначь уникальное имя через `herdr agent rename <pane-id> <name>` и только затем используй `herdr agent prompt ... --wait`;
   - для прочих target families используй штатный `herdr agent start --kind <kind>`, передав поддерживаемые provider-native аргументы exact model/effort после `--`;
   - передай самодостаточную фазовую задачу через `herdr agent prompt ... --wait` и дождись terminal result;
   - сохрани agent name, pane id, provider session identity, exact tuple и result path в `sessions.json`/role context.
4. `idle`/`done` после наблюдаемого рабочего перехода — допустимый terminal signal. `blocked` требует прочитать причину и маршрутизировать вопрос; `unknown` никогда не означает completion.
5. Progress определяй по lifecycle и новому output. Тишина сама по себе не является зависанием. После двух проверок без прогресса применяй штатный interrupt/restart limit Оркестратора.
6. После получения и проверки артефакта закрой только созданного этим запуском агента/pane. Чужие panes, tabs, workspaces и agents не трогай.

Если exact tuple не поддерживается фактической CLI schema, применяй только заранее разрешённый outage fallback из матрицы. Самостоятельно подбирать «ближайшую» модель запрещено.

## Алгоритм вне Herdr

1. Не вызывай `herdr` и не пытайся управлять focused session извне.
2. Выбери qualified маршрут той же family, что текущий harness, сохраняя role capability floor.
3. Если утверждённая матрица не содержит допустимого same-family маршрута, верни `BLOCKED_ROUTE_UNAVAILABLE`; не подменяй чужое семейство моделью текущего семейства по сходству названия.
4. Kimi K3 как аварийный fallback доступен вне Herdr только когда текущий harness сам относится к Kimi family.

## Ownership и cleanup

- Оркестратор остаётся единственным владельцем phase routing и событий fallback.
- Phase owner не запускает peer owner и не передаёт ownership.
- В `final-report.md` входят созданные agent/pane ids, terminal states и cleanup status.
- Нельзя объявлять задачу завершённой при активных ресурсах Herdr, созданных текущим Оркестратором.

---
depends_on:
  - framework/skills/tool-usage/herdr/SKILL.md
  - framework/rules/codex-subagent-orchestration/SKILL.md
  - framework/rules/self-recovery-limits/SKILL.md
---

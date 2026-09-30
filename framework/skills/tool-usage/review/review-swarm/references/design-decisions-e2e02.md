# Дизайн-решения по находкам E2E-02 (RVSW-01 rework)

Зафиксированные решения Оркестратора по находкам, принятым как
«документировать, не переделывать» (диспозиция rework E2E-02).

## F-010 (P3): `content_retry_decision` — предикат, зарезервированный для будущего использования

`swarm.content_retry_decision(valid, retried, invocation_kind)` декларирован
как реализация §6.3.1 («невалидный structured-ход → ровно один контент-retry →
unresponsive; таймаут → unresponsive БЕЗ retry»), но на продуктивном пути его
результат не влияет на управление: retry выполняется безусловно при первой
невалидности хода.

**Решение:** оставить как есть, семантику зафиксировать:

- текущее поведение продуктивного пути **эквивалентно** предикату: при первой
  невалидности решение всегда `retry`, второй невалидности не бывает (после
  одного retry участник помечается `unresponsive`); таймауты обрабатываются
  отдельной веткой `invocation_outcome` без контент-retry. Безусловный retry —
  это развёртка того же решения, а не расхождение семантики;
- предикат сохраняется как **явная точка фиксации протокола §6.3.1**: он
  покрыт unit-тестами (SU-FC01), используется в комментариях/проверках и
  является местом будущего изменения retry-политики (например, запрет retry по
  категориям отказа). Удалять его нельзя — тогда правило §6.3.1 потеряло бы
  единое machine-checkable выражение;
- если в будущем retry-политика станет зависеть от контекста (категория
  отказа, тур, лимиты бюджета), переход на вызов предиката — локальная правка
  в `cmd_attack`/`_verdict_wave`/`cmd_rebut`/`cmd_gate_verdict`/`cmd_rereview`.

## F-011 (P3): `--caller` — обязательный policy assertion доверенной agent-среды

Текущий review-swarm запускается агентом локально и не имеет внешнего
runtime-контракта аутентификации participant identity.

**Bounded решение A:**

- `convene` требует явный `--caller <id участника registry>` во всех тарифах;
  отсутствие или неизвестный id отказывают до создания session;
- значение — policy assertion вызывающего агента в текущей доверенной
  agent-среде, **не authentication** и не доказательство identity против
  hostile caller;
- threat model покрывает случайный self-review, пропуск аргумента и ошибочную
  registry identity. Намеренная подмена caller самим вызывающим находится вне
  текущей threat model;
- exact объявленный caller исключается из light selection, requested gate
  reviewer и reassignment; другой participant той же family допустим;
- identity автора артефакта — отдельная provenance-роль и не подставляется
  вместо caller;
- authenticated caller identity потребует отдельного external runtime contract
  (например, signed capability/tool metadata), его выбор и внедрение — отдельное
  решение пользователя, не скрытая обязанность этого CLI;
- session/report сохраняют `orchestrator_id` и compatibility-only
  `caller_family`; durable `gate-outcomes.jsonl` сохраняет `caller_id`,
  `caller_family` и фактический `reviewer_id`.

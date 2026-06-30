---
name: onec-server-maintenance-hooks
description: "Вебхуки обслуживания сервера 1С: restart контейнера и очистка кеша внешних компонент"
---

# Вебхуки обслуживания сервера 1С

> Используй этот навык, когда нужно управлять обслуживающими HTTP-вебхуками стенда 1С: перезапуском контейнера сервера 1С и очисткой кеша внешних компонент. Это tool-usage навык для инфраструктурных операций, не навык обновления ИБ.

## Когда применять

| Ситуация | Действие |
|----------|----------|
| Нужно перезапустить сервер 1С после обновления, очистки кеша или зависших процессов | `POST /restart/onec-server` |
| Нужно очистить распакованный кеш конкретной внешней компоненты 1С | `POST /external-components/cache/clear` с `component` |
| Нужно понять, потребуется ли перезапуск после очистки кеша ВК | Сначала вызвать cache clear с `dry_run=true` и проверить `restart_required` |
| Нужно менять сам webhook-сервис | Сначала read-only диагностика, затем явное подтверждение пользователя |

## Общие правила безопасности

- Токен брать из проектного секрета, принятого на стенде; не выводить токен в лог или ответ.
- Не использовать прямой `systemctl`, `docker restart`, `rm` по SSH, если есть контролируемый webhook.
- Деструктивные SSH-действия на сервере 1С выполнять только после явного подтверждения пользователя.
- Для очистки кеша передавать имя или узкую маску компоненты, а не путь к файлу.
- Не вызывать очистку с общей маской вроде `*`: hook должен работать по конкретной ВК.

## Healthcheck

```bash
curl -sS http://onec-infra:8765/health
```

Ожидаемый ответ содержит `status: "ok"` и список доступных endpoint.

## Перезапуск контейнера 1С

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  http://onec-infra:8765/restart/onec-server
```

Ожидать HTTP 200 и JSON:

```json
{"status": "restarted", "container": "onec-server"}
```

После перезапуска проверить готовность контейнера:

```bash
ssh -i "/workspaces/work/repos/1C Framework/1c-log-checker/.ssh/onec-infra/id_ed25519" \
  sandbox@192.168.250.2 \
  'sudo -n docker inspect -f "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}" onec-server'
```

Готовое состояние: `running healthy`.

## Очистка кеша внешней компоненты

Сначала всегда делать `dry_run=true`:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  -H "Content-Type: application/json" \
  --data '{"container":"onec-server","component":"WebTransportAddIn","dry_run":true}' \
  http://onec-infra:8765/external-components/cache/clear
```

Поля ответа:

| Поле | Смысл |
|------|-------|
| `matches` | Найденные файлы кеша, которые будут удалены при `dry_run=false` |
| `deleted` | Файлы, удалённые при реальном запуске |
| `loaded` | Отображения компоненты в живых процессах по `/proc/*/maps` |
| `restart_required` | `true`, если компонент уже загружен процессом и удаления файла недостаточно |

Реальная очистка:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  -H "Content-Type: application/json" \
  --data '{"container":"onec-server","component":"WebTransportAddIn","dry_run":false}' \
  http://onec-infra:8765/external-components/cache/clear
```

## Нужно ли перезапускать 1С после очистки

- Если `restart_required=false`, файл кеша не загружен в живой процесс; можно повторить проверку или тест без restart.
- Если `restart_required=true`, компонент уже загружен в процесс (`addnhost`, `rphost` и т.п.). Удаление файла с диска не выгрузит старый код из памяти. Нужно вызвать `/restart/onec-server`, затем дождаться `running healthy`.

## Проверка результата после очистки

Повторить `dry_run=true`. Для успешно очищенной компоненты ожидаемо:

```json
{
  "matches": [],
  "loaded": [],
  "restart_required": false
}
```

Если после restart компонент снова появился старой версией, значит 1С заново распаковала старый payload из макета или расширения. В этом случае проверять версию и содержимое поставляемой ВК в конфигурации, а не чистить кеш повторно.

## Где живёт hook на стенде

Обычно на сервере `onec-infra`:

- сервис: `onec-restart.service`;
- скрипт: `/opt/onec-restart/restart_svc.py`;
- доступ для диагностики может быть описан в проектном окружении или в документации log-checker.

Править удалённый hook можно только как инфраструктурное изменение: сначала read-only диагностика, затем явное подтверждение пользователя, после правки — синтаксическая проверка, бэкап старого файла и restart только webhook-сервиса, не сервера 1С.

---
depends_on: []
---

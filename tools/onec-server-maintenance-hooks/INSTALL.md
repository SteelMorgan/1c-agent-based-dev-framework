# Установка onec-server maintenance hooks

Эта группа содержит воспроизводимую версию webhook-сервиса обслуживания контейнера 1С.

## Файлы

- `restart_svc.py` — HTTP webhook без внешних Python-зависимостей.
- `onec-restart.service` — systemd unit для запуска webhook-сервиса.

## Endpoint

- `GET /health` — проверка сервиса.
- `POST /restart/<container>` — restart whitelisted Docker-контейнера.
- `POST /external-components/cache/clear` — очистка кеша конкретной внешней компоненты внутри контейнера.

## Предусловия на сервере

- Docker установлен и доступен пользователю сервиса через группу `docker`.
- Пользователь сервиса: `sandbox`.
- Контейнер 1С называется `onec-server`.
- Секретный токен хранится на сервере в `/etc/onec-restart/token`.

Токен не хранится в git. Для локальных вызовов из workspace используется файл `/workspaces/work/secrets/onec_restart_token`.

## Первичная установка

Команды выполнять на сервере `onec-infra`:

```bash
sudo install -d -o root -g root -m 0755 /opt/onec-restart
sudo install -d -o root -g root -m 0755 /etc/onec-restart
sudo install -o root -g root -m 0755 restart_svc.py /opt/onec-restart/restart_svc.py
sudo install -o root -g root -m 0644 onec-restart.service /etc/systemd/system/onec-restart.service
```

Создать токен:

```bash
sudo sh -c 'umask 077; openssl rand -hex 32 > /etc/onec-restart/token'
```

Включить сервис:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now onec-restart.service
sudo systemctl status onec-restart.service --no-pager
```

## Обновление установленного hook

Скопировать новую версию на сервер, затем:

```bash
ts=$(date -u +%Y%m%dT%H%M%SZ)
sudo cp /opt/onec-restart/restart_svc.py /opt/onec-restart/restart_svc.py.bak.$ts
sudo install -o root -g root -m 0755 restart_svc.py /opt/onec-restart/restart_svc.py
sudo systemctl restart onec-restart.service
sudo systemctl is-active onec-restart.service
```

При обновлении webhook-сервиса перезапускается только `onec-restart.service`. Контейнер `onec-server` перезапускать не нужно, если не выполняется отдельная операция `/restart/onec-server`.

## Проверка

```bash
curl -sS http://onec-infra:8765/health
```

Restart контейнера:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  http://onec-infra:8765/restart/onec-server
```

Dry-run очистки кеша ВК:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  -H "Content-Type: application/json" \
  --data '{"container":"onec-server","component":"WebTransportAddIn","dry_run":true}' \
  http://onec-infra:8765/external-components/cache/clear
```

Если ответ содержит `restart_required: true`, компонент уже загружен в процесс. После реальной очистки файла нужно отдельно вызвать `/restart/onec-server`, иначе старый код останется в памяти процесса.

## Ограничения безопасности

- Не передавать в `component` путь к файлу.
- Не использовать общую маску `*`.
- Не коммитить `/etc/onec-restart/token` и любые значения Bearer-токенов.
- Не расширять whitelist контейнеров без явной причины и ревью.

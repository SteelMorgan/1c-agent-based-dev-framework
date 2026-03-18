---
name: rac-use
description: Администрирование кластера серверов 1С через утилиту RAC — просмотр/завершение сеансов, управление блокировками, соединениями, информационными базами и другими объектами кластера.
---

# RAC — утилита администрирования кластера 1С

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно проверить/убить сеансы в базе | `session list` / `session terminate` |
| Нужно отключить соединения | `connection list` / `connection disconnect` |
| Нужно заблокировать вход в базу | `infobase update --sessions-deny=on` |
| Нужно запретить регл. задания | `infobase update --scheduled-jobs-deny=on` |
| Нужно посмотреть блокировки | `lock list` |
| Нужна информация о кластере/базах | `cluster list` / `infobase summary list` |

---

## Подключение

**Бинарник:** `/opt/1cv8/current/rac` (всегда актуальная версия).

**Данные подключения:** `<project_root>/configs/yaxunit-runner.yml`, секция `app.connection` — сервер, база, логин, пароль.

**Адрес агента кластера:** по умолчанию `localhost:1545`. Если сервер отличается — указать явно как последний аргумент: `rac <command> <host>:<port>`.

---

## Первый шаг — получить cluster UUID

Все команды требуют `--cluster=<uuid>`. Получи его первым:

```bash
/opt/1cv8/current/rac cluster list
```

Вывод содержит `cluster : <uuid>` — запомни и используй далее.

---

## Основные сценарии

### Просмотр сеансов базы

```bash
# Найти UUID базы
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> summary list

# Список сеансов базы
/opt/1cv8/current/rac session --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### Принудительное завершение сеанса

```bash
/opt/1cv8/current/rac session --cluster=<cluster_uuid> terminate \
  --session=<session_uuid> \
  --error-message="Сеанс завершён агентом для выполнения задачи"
```

### Блокировка входа в базу

```bash
# Включить блокировку
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=on \
  --denied-message="База заблокирована для обслуживания" \
  --permission-code="secret123"

# Снять блокировку
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=off
```

### Управление регламентными заданиями

```bash
# Запретить
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=on

# Разрешить
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=off
```

### Просмотр блокировок

```bash
/opt/1cv8/current/rac lock --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### Просмотр и разрыв соединений

```bash
# Список соединений базы
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> list --infobase=<infobase_uuid>

# Разрыв конкретного соединения
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> disconnect \
  --process=<process_uuid> --connection=<connection_uuid>
```

---

## Все режимы RAC

| Режим | Назначение |
|-------|-----------|
| `cluster` | Кластеры: список, создание, удаление, администраторы |
| `infobase` | Информационные базы: создание, обновление, удаление, блокировка сеансов/рег.заданий |
| `session` | Сеансы: список, информация, принудительное завершение |
| `connection` | Соединения: список, разрыв |
| `lock` | Блокировки: просмотр |
| `process` | Рабочие процессы |
| `server` | Рабочие серверы |
| `manager` | Менеджеры кластера |
| `agent` | Агент кластера |
| `service` | Сервисы менеджера |
| `rule` | Требования назначения |
| `profile` | Профили безопасности |
| `counter` | Счётчики потребления ресурсов |
| `limit` | Ограничения потребления ресурсов |

Справка по любому режиму: `rac help <mode>`.

---

## Типичные ошибки

| Ошибка | Причина | Решение |
|--------|---------|---------|
| `Агент кластера недоступен` | Не запущен `ragent` или неверный адрес | Проверить `localhost:1545` или указать правильный адрес |
| `Неверный идентификатор кластера` | UUID скопирован с ошибкой | Повторить `cluster list` |
| `Недостаточно прав` | Нужны credentials администратора кластера | Добавить `--cluster-user` / `--cluster-pwd` |
| `Информационная база не найдена` | Неверный UUID базы | Проверить через `infobase summary list` |

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
requires:
  - tools
---

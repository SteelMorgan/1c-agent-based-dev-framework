# Заимствования БСП 3.1.11

Источник: `https://github.com/brake71/1c-ssl-skills`
Commit: `85783eececb3a658ea15fc793b095ac370b5339c`
Версия источника: БСП 3.1.11
Дата импорта: 2026-06-30
Локальный каталог: `references/bsp-3.1.11/`

Этот файл фиксирует, какие материалы взяты из внешнего набора, с какими нашими навыками они связаны и зачем они нужны агенту. При обновлении upstream сначала сравните этот файл, затем обновите соответствующие reference-файлы и `metadata` в `SKILL.md`.

## Политика использования

- Используйте внешний слой как справочник точных БСП API: реальные имена модулей, сигнатуры, регионы API, хуки, deprecated/service-границы.
- Не заменяйте им наши концептуальные навыки. Если у нас есть общий навык про проектирование фоновых заданий, безопасность или обмены, он задает инженерную политику, а reference БСП дает конкретные вызовы.
- При расхождении с текущей конфигурацией приоритет у исходников проекта и `get_signature_help` / `scripts/bsp_api.py`.
- Для актуализации версии меняйте не только файлы, но и строку `bsp_reference_version`, `borrowed_commit`, `borrowed_at` в `SKILL.md`.

## Вес добавочного знания

| Вес | Что означает |
|---|---|
| Очень высокий | Модель часто знает идею, но не может надежно восстановить точные модули, сигнатуры, регионы и запреты БСП. |
| Высокий | Общая логика известна, но reference существенно снижает риск галлюцинаций по API. |
| Средний | У нас уже есть близкий навык или знание общеизвестно; reference полезен как проверка и источник примеров. |
| Низкий | Брать только как справочное покрытие, не поднимать в основной маршрут. |

## Таблица заимствований

| Upstream reference | Локальный файл | Связанные наши навыки | Вес | Что взяли |
|---|---|---|---|---|
| `fundamentals.md` | `references/bsp-3.1.11/fundamentals.md` | `ssl-patterns`, `api-design`, `coding-standards` | Очень высокий | Суффиксы модулей, stable/service/deprecated, хуки `*Переопределяемый`, карта подсистем и типовые галлюцинации. |
| `base-common.md` | `references/bsp-3.1.11/base-common.md` | `ssl-patterns`, `error-handling`, `security`, `form-patterns` | Средний | Точные сценарии `ОбщегоНазначения*`, строк, дат, XML/JSON, реквизитов по ссылке, безопасного хранилища. |
| `longs-and-jobs.md` | `references/bsp-3.1.11/longs-and-jobs.md` | `background-jobs`, `ssl-patterns`, `vanessa-run-loop` | Высокий | Конкретный API `ДлительныеОперации*` и `РегламентныеЗадания*` поверх наших правил идемпотентности. |
| `users-access.md` | `references/bsp-3.1.11/users-access.md` | `security`, `ssl-patterns`, `api-design` | Очень высокий | RLS, профили групп доступа, внешние пользователи, проверки прав и границы служебного API. |
| `commands-external.md` | `references/bsp-3.1.11/commands-external.md` | `ssl-patterns`, `form-patterns`, `api-design` | Очень высокий | Подключаемые команды, внешние отчеты/обработки, регистрация, запуск и настройки. |
| `print-reports.md` | `references/bsp-3.1.11/print-reports.md` | `ssl-patterns`, `query-optimize`, `form-patterns` | Очень высокий | Менеджер печати, коллекция печатных форм, варианты отчетов, программное формирование отчетов. |
| `forms-validation.md` | `references/bsp-3.1.11/forms-validation.md` | `form-patterns`, `form-visual-requirements`, `ssl-patterns` | Высокий | Запрет редактирования реквизитов, свойства, дополнительные реквизиты, даты запрета изменения. |
| `files-and-versions.md` | `references/bsp-3.1.11/files-and-versions.md` | `ssl-patterns`, `security`, `form-patterns` | Высокий | Файлы БСП, тома, двоичные данные, версионирование объектов, подключение файлов к формам. |
| `data-exchange.md` | `references/bsp-3.1.11/data-exchange.md` | `data-exchange`, `background-jobs`, `ssl-patterns` | Высокий | API обмена данными БСП, планы обмена, регистрация изменений, SaaS-области, deprecated-замены. |
| `comms.md` | `references/bsp-3.1.11/comms.md` | `integration-patterns`, `security`, `ssl-patterns` | Высокий | Почта, SMS, шаблоны сообщений, обсуждения, взаимодействия и хранение коммуникаций. |
| `contact-info.md` | `references/bsp-3.1.11/contact-info.md` | `ssl-patterns`, `form-patterns` | Высокий | Контактная информация, адреса, классификатор, строковые представления и формы ввода. |
| `currencies-banks.md` | `references/bsp-3.1.11/currencies-banks.md` | `ssl-patterns`, `query-patterns` | Высокий | Курсы валют, банки, БИК, производственные календари, графики работы. |
| `prefixes.md` | `references/bsp-3.1.11/prefixes.md` | `ssl-patterns`, `coding-standards` | Средний | Префиксы номеров и кодов, префикс ИБ, нестандартные форматы номеров. |
| `update.md` | `references/bsp-3.1.11/update.md` | `ssl-patterns`, `error-handling`, `test-writing` | Высокий | Обработчики обновления, версия ИБ, безопасная запись при обновлении, хуки обновления. |
| `esign-mcd.md` | `references/bsp-3.1.11/esign-mcd.md` | `security`, `integration-patterns`, `ssl-patterns` | Очень высокий | ЭП, МЧД, криптография, DSS, проверка подписи и границы прикладного/служебного API. |
| `protection-pd.md` | `references/bsp-3.1.11/protection-pd.md` | `security`, `ssl-patterns`, `form-patterns` | Очень высокий | 152-ФЗ, согласия, уничтожение ПДн, регистрация доступа, хуки жизненного цикла. |
| `perf-monitoring.md` | `references/bsp-3.1.11/perf-monitoring.md` | `query-optimize`, `db-performance`, `runtime-investigation` | Высокий | Оценка производительности, ключевые операции, бизнес-статистика, центр мониторинга. |
| `admin-tools.md` | `references/bsp-3.1.11/admin-tools.md` | `runtime-investigation`, `v8-session-manager`, `security` | Средний | Завершение работы пользователей, удаление помеченных, профили безопасности. |
| `backup.md` | `references/bsp-3.1.11/backup.md` | `runtime-investigation`, `security` | Средний | Резервное копирование ИБ и сценарии администрирования через БСП. |
| `bp-tasks.md` | `references/bsp-3.1.11/bp-tasks.md` | `ssl-patterns`, `form-patterns` | Высокий | Бизнес-процессы, задачи, исполнители, состояние и формы задач. |
| `classifiers.md` | `references/bsp-3.1.11/classifiers.md` | `ssl-patterns`, `integration-patterns` | Средний | Загрузка и обновление нормативно-справочной информации. |
| `external-components.md` | `references/bsp-3.1.11/external-components.md` | `integration-patterns`, `security`, `ssl-patterns` | Высокий | Внешние компоненты, OData, безопасные границы использования. |
| `multilang.md` | `references/bsp-3.1.11/multilang.md` | `coding-standards`, `ssl-patterns` | Средний | Мультиязычность, `НСтр`, текущий язык и локализация. |
| `report-dedup.md` | `references/bsp-3.1.11/report-dedup.md` | `ssl-patterns`, `query-patterns`, `form-patterns` | Высокий | Поиск дублей, групповое изменение объектов, структура подчиненности. |
| `scripts/bsp_api.py` | `scripts/bsp_api.py` | `code-navigation`, `code-verification`, `ssl-patterns` | Очень высокий | Детерминированный поиск методов и модулей БСП в выгрузке `src/cf` с регионом API. |

## Актуализация

1. Получите новый upstream commit и зафиксируйте его SHA.
2. Сравните список `references/*.md` и `scripts/bsp_api.py`.
3. Обновите только изменившиеся файлы в `references/bsp-3.1.11/` или заведите новый каталог версии, если изменилась версия БСП.
4. Обновите эту таблицу: `Commit`, `Дата импорта`, связи, вес и краткое описание изменений.
5. Обновите `metadata` в `SKILL.md`.
6. Прогоните проверку структуры и, если есть выгрузка БСП, `python scripts/bsp_api.py modules --src src/cf`.

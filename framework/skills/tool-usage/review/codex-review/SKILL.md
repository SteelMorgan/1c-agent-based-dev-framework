---
name: codex-review
description: Ревью через внешние LLM (Codex). Навык учит агента запускать независимое ревью артефактов через Codex CLI (GPT) и собирать результат. Используй при запросе второго мнения, вызове /review-gpt, /review-all, или когда пользователь просит проверить план, спецификацию, код или архитектуру альтернативной моделью. Навык можно использовать для передачи произвольной задачи другой LLM по просьбе пользователя.
---

# Ревью через Codex CLI

## Когда применять

| Триггер | Действие |
|---------|----------|
| `/review-gpt` | Запустить ревью через Codex CLI |
| `/review-all` | GPT + Opus параллельно (см. `opus-review`) |
| «второе мнение» | Предложить `/review-gpt` или `/review-all` |
| Сложная архитектура, > 5 файлов | Рекомендовать ревью |
| Перед реализацией спецификации | Предложить ревью плана |

---

## Шаг 0: определить режим Codex

Перед вызовом определи режим работы Codex:

```bash
if [[ "${CUSTOM_CODEX_ENABLED:-0}" == "1" ]]; then
  echo true   # кастомный режим с профилями
else
  echo false  # стандартный режим
fi
```

| Результат | Режим | Команда |
|-----------|-------|---------|
| `true` | Кастомный API-сервер | см. [Режим: custom](#режим-custom) |
| `false` | Стандартный ChatGPT auth | см. [Режим: default](#режим-default) |

---

## Режим: custom

Кастомный сервер настроен через `base_url` — используй профили из `config.toml`.

```bash
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -p cx_gpt-5_3-codex-high \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

---

## Режим: default

Стандартный ChatGPT auth. Модель и effort передаются флагами.

```bash
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

---

## Передача промпта: прямо или через файл

### Прямо в аргументе (только одинарные кавычки)

Используй, когда промпт **короткий** (до ~100 символов), **однострочный**, **без обратных кавычек и `$`**:

```bash
codex exec -m gpt-5.4 -c 'model_reasoning_effort="high"' \
  --ephemeral \
  'Объясни назначение функции РассчитатьСумму в файле Module.bsl'
```

**Почему одинарные кавычки:**
- AI-агент при генерации команд в двойных кавычках часто забывает экранировать `$`, `` ` ``, `!` — shell раскрывает их, аргумент искажается, API возвращает `Invalid JSON body`
- Одинарные кавычки `'...'` передают содержимое as-is — безопасны для `!`, `-`, `#`, `$`, пробелов
- Одинарную кавычку внутри промпта передавай через файл

### Через файл (основной способ для ревью)

Используй всегда, когда промпт:
- **Многострочный** (любой ревью-промпт из шаблона)
- Содержит обратные кавычки `` ` ``, `$`, `\`, одинарные кавычки
- Длиннее ~100 символов

```bash
# 1. Записать промпт
PROMPT_FILE=$(mktemp /tmp/codex-prompt-XXXXXX.txt)
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)

cat <<'EOF' > "$PROMPT_FILE"
<текст промпта — любой длины и символы без ограничений>
EOF

# 2. Запустить (default режим)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < "$PROMPT_FILE"
```

---

## Формирование промпта

### Произвольная задача

Если задача — не ревью, агент формирует промпт самостоятельно. Принципы:

- **Одна задача — один промпт.** Чётко сформулируй что нужно сделать.
- **Контекст через файлы.** Если нужны данные из проекта — укажи пути, не вставляй содержимое в промпт. Codex прочитает файлы сам.
- **Результат через файл.** Если ожидается вывод (текст, код) — попроси записать в конкретный путь.

Пример — анализ файла:
```
Прочитай файл src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
Найди все места где выполняется запрос к базе данных вне транзакции.
Запиши список в /tmp/codex-result.txt в формате: имя функции, строка, описание проблемы.
```

Пример — генерация кода:
```
Прочитай спецификацию docs/specs/SPEC-резервирование.md.
Сгенерируй заготовку модуля BSL согласно спецификации.
Запиши результат в src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
```

### Ревью

Промпт для ревью состоит из трёх блоков. Полный шаблон: [references/prompt-template.md](references/prompt-template.md).

**Блок 1 — Задача:** что проверяем и в каком контексте (2-5 предложений).

**Блок 2 — Артефакт:** пути к файлам — ревьювер читает их сам. Предпочитай пути; вставляй текст только если артефакт не существует на диске (diff, сгенерированный фрагмент) или нужен короткий критичный контекст.

**Блок 3 — Навыки:** пути к `SKILL.md` по типу артефакта:

| Тип | Навыки |
|-----|--------|
| Код BSL | `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns` |
| Спецификация | `spec-standard` |
| Форма (UI) | `form-patterns`, `form-visual-requirements` |
| Архитектура | `ssl-patterns`, `query-patterns`, `coding-standards` |
| Тесты | `coding-standards`, `error-handling` |

Пути: `framework/skills/bsl-practices/<name>/SKILL.md`

---

## Мониторинг и сбор результата

Запускать в фоне (`run_in_background: true`). Запомнить `RESULT_FILE`.

**Проверка завершения:**

```
# Способ A — TaskOutput
TaskOutput(task_id, block=false)

# Способ B — файл -o (создаётся атомарно при завершении)
Read(RESULT_FILE)  — если существует → Codex завершился
```

> Если `TaskOutput` вернул «no task found» — сразу проверяй файл `-o`.

Необязательные UX-подсказки — если в stdout встретились характерные слова, можно сообщить пользователю примерный статус (слова могут меняться между версиями CLI):
- `exec` → «GPT читает файлы проекта...»
- `codex` → «GPT формирует ответ...»
- `tokens used` → завершено

**Получение результата:**
```
Read(RESULT_FILE)  — чистый ответ без логов
```

Если файл пуст → `TaskOutput(task_id, block=true)`.

---

## Обработка ошибок

| Ситуация | Действие |
|----------|----------|
| `Invalid JSON body` | Промпт искажён shell-expansion (двойные кавычки) — передать через файл или одинарные кавычки |
| Codex CLI не установлен | `npm install -g @openai/codex` |
| Auth failure / `login required` | Запустить `codex login`; если custom-режим — проверить `config.toml` и API-ключ |
| Неизвестный профиль `-p` | Проверить имя профиля в `~/.codex/config.toml` |
| Rate limit / 429 | Сообщить пользователю, подождать 30-60 сек, повторить |
| Non-zero exit, файл `-o` не создан | Показать stderr из `TaskOutput`; проверить cwd, пути, модель |
| Таймаут | Показать частичный результат из stdout |
| Файл `-o` пуст | Взять последнее сообщение из `TaskOutput` |

---

## Связанные ресурсы

- [Шаблон промпта](references/prompt-template.md)
- [opus-review](../opus-review/SKILL.md) — ревью через второй инстанс Opus

---
depends_on:
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/error-handling/SKILL.md
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/form-patterns/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---

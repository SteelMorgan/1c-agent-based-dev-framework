Получи независимое ревью от Gemini по текущему плану, спецификации, коду или архитектуре.

Используй навык `gemini-review` из `framework/skills/tool-usage/gemini-review/SKILL.md`.

> **Важно:** Gemini запускается через **Codex CLI** с профилем `ag_gemini-3_1-pro-high`, не через Gemini CLI напрямую. Gemini CLI в headless режиме не выполняет агентный цикл и не читает файлы.

## Шаги

1. **Определи артефакт и тип ревью** — спроси пользователя если неясно:
   - Что ревьюируем: спецификация, код, архитектура, тесты?
   - Где артефакт: файлы проекта, git diff?

2. **Выбери навыки** по типу артефакта:
   - Код BSL → `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns`
   - Спецификация / план → `spec-standard`
   - Форма (UI) → `form-patterns`, `form-visual-requirements`
   - Архитектура → `ssl-patterns`, `query-patterns`, `coding-standards`
   - Тесты → `coding-standards`, `error-handling`

3. **Собери промпт** по шаблону из `framework/skills/tool-usage/gemini-review/references/prompt-template.md`

4. **Запусти в фоне** (Bash с `run_in_background: true`):

```bash
REVIEW_TS=$(date +%s)
PROMPT_FILE=/tmp/gemini-prompt-${REVIEW_TS}.txt
RESULT_FILE=/tmp/gemini-review-${REVIEW_TS}.txt

cat <<'EOF' > $PROMPT_FILE
<промпт>
EOF

codex exec -p ag_gemini-3_1-pro-high --sandbox read-only --ephemeral \
  -o $RESULT_FILE - < $PROMPT_FILE
```

5. **Мониторь прогресс** через TaskOutput и Read(RESULT_FILE):
   - Файл `-o` создаётся атомарно — появляется только когда Gemini завершил работу
   - Если TaskOutput вернул «no task found» — сразу читай RESULT_FILE

6. **Представь результат** пользователю

## Важно

- Модель: `ag/gemini-3.1-pro-high` через профиль `ag_gemini-3_1-pro-high` в `~/.codex/config.toml`
- При ошибке rate limit (400, «reset after Ns») — подождать N секунд и повторить

Получи независимое ревью одновременно от GPT, Gemini и второго Opus, затем синтезируй общий отзыв.

Используй навыки:
- `framework/skills/tool-usage/codex-review/SKILL.md` — GPT через Codex CLI
- `framework/skills/tool-usage/gemini-review/SKILL.md` — Gemini через Codex CLI
- `framework/skills/tool-usage/opus-review/SKILL.md` — второй Opus через Task tool

## Статус

- ✅ GPT — `codex exec -p cx_gpt-5_3-codex-high`
- ✅ Gemini — `codex exec -p ag_gemini-3_1-pro-high`
- ✅ Opus — Task tool (subagent_type: general-purpose)

## Шаги

1. **Определи артефакт и тип ревью**, выбери навыки по маппингу из навыков

2. **Собери промпт** — одинаковый по содержанию для всех трёх ревьюверов:
   - Для GPT/Gemini: по шаблону `codex-review/references/prompt-template.md`
   - Для Opus: по шаблону `opus-review/references/prompt-template.md` (добавить блоки «Роль» и «Формат ответа»)

3. **Запусти всех трёх ПАРАЛЛЕЛЬНО** — в одном сообщении: два Bash (`run_in_background: true`) + один Task:

   GPT (Bash, background):
   ```bash
   REVIEW_TS=$(date +%s)
   PROMPT_FILE=/tmp/review-prompt-${REVIEW_TS}.txt
   cat <<'EOF' > $PROMPT_FILE
   <промпт без блоков Роль/Формат>
   EOF
   codex exec -p cx_gpt-5_3-codex-high --sandbox read-only --ephemeral \
     -o /tmp/review-gpt-${REVIEW_TS}.txt - < $PROMPT_FILE
   ```

   Gemini (Bash, background):
   ```bash
   codex exec -p ag_gemini-3_1-pro-high --sandbox read-only --ephemeral \
     -o /tmp/review-gemini-${REVIEW_TS}.txt - < /tmp/review-prompt-${REVIEW_TS}.txt
   ```

   Opus (Task, foreground — можно параллельно с Bash):
   ```
   Task(
     subagent_type: "general-purpose",
     description: "Ревью <тип> через второй Opus",
     prompt: "<промпт с блоками Роль и Формат ответа>"
   )
   ```

4. **Собери результаты**:
   - GPT/Gemini: читать файлы `-o` (появляются атомарно по завершении)
   - Opus: результат уже в ответе Task

5. **Синтезируй**:

   ## Отзыв GPT
   [резюме]

   ## Отзыв Gemini
   [резюме]

   ## Отзыв Opus
   [резюме]

   ## Консенсус
   - Все трое согласны: [высокая уверенность — обязательно исправить]
   - Двое согласны: [средняя уверенность]
   - Только один отметил: [к сведению]

   ## Рекомендация
   [итоговая рекомендация]

6. **Совпадение у 2+ ревьюверов** = высокая уверенность в проблеме

## Важно

- Параллельный запуск экономит время, но утраивает расход токенов — использовать для значимых артефактов
- При ошибке одного ревьювера — представить результаты остальных с пометкой
- Opus и GPT/Gemini находят разные классы ошибок — ценность именно в совокупности
- При rate limit (400) — подождать указанное время и повторить запрос этой модели

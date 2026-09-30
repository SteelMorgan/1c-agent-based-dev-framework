# Project Rules — 1C Agent Framework

## Skills Language Policy

### Архитектура навыков

Навыки фреймворка существуют в двух параллельных деревьях:

- `framework/` — **источник правды**, русский язык, редактируется людьми и агентами
- `framework_eng/` — **зеркало**, английский язык, генерируется **только автоматически**

Симлинки в `.claude/skills/` указывают на `framework_eng/`.

### Правила для агента

**ОБЯЗАТЕЛЬНО:**

1. Если ты изменяешь любой файл в `framework/` (кроме `README.md`) —
   ты ОБЯЗАН немедленно синхронизировать зеркало:
   ```bash
   python3 tools/sync-skill.py framework/path/to/changed/file.md
   ```
   Не жди коммита — сделай это сразу как часть задачи.

2. Перед использованием навыка из `.claude/skills/` — убедись что он актуален.
   Если `.skills-sync-state.json` показывает статус `dirty` или `pending` —
   сообщи пользователю и предложи запустить синхронизацию.

**ЗАПРЕЩЕНО:**

3. Никогда не редактируй файлы в `framework_eng/` напрямую.
   Только через перевод из `framework/` с помощью `sync-skill.py`.

4. Никогда не добавляй перевод вручную без вызова скрипта.
   Ручной перевод нарушит хэши в `.skills-sync-state.json`.

**ИСКЛЮЧЕНИЯ:**

5. Файлы `README.md` в любом месте репо — не переводятся и не синхронизируются.

### Статусы синхронизации

| Статус | Значение | Действие |
|--------|----------|----------|
| `synced` | RU и EN идентичны по смыслу | Можно использовать |
| `pending` | EN-версия не создана | Запустить `/sync-skills <path>` |
| `dirty` | RU изменён, EN устарел | Запустить `/sync-skills <path>` |
| `error` | Последний перевод упал | Проверить ошибку, повторить |

### Команды синхронизации

```bash
# Проверить статус всех навыков
python3 tools/sync-skill.py --check

# Синхронизировать конкретный файл
python3 tools/sync-skill.py framework/skills/bsl-practices/coding-standards/SKILL.md

# Синхронизировать все устаревшие
python3 tools/sync-skill.py --all

# Первичная синхронизация (один раз при инициализации)
python3 tools/sync-skill.py --init-all
```

---

## Git Workflow

- Ветка для работы агента: `agent/<task>-<yyyymmdd>`
- Никогда не пушить в `main` или `master`
- PR открывать через `gh pr create`

---

## Hook Installation

При клонировании репо хук нужно установить вручную:

```bash
cp tools/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Simlinc Policy
@.claude/rules/framework-meta-skill-symlink.mdc
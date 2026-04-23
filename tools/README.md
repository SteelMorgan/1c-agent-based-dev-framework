# Инструменты фреймворка

## validate-framework-deps.py

Валидатор зависимостей фреймворка. Проверяет корректность `depends_on:[]`, находит недекларированные ссылки и группы взаимозависимостей.

```bash
# Проверка
python3 tools/validate-framework-deps.py

# Экспорт взаимозависимостей для install.py
python3 tools/validate-framework-deps.py --export-cycles tools/framework-cycles.json

# Автоисправление предупреждений (добавить недостающие depends_on)
python3 tools/validate-framework-deps.py --fix
```

### Что проверяется

| Проверка | Тип | Описание |
|----------|-----|----------|
| Существование зависимостей | ❌ Ошибка | Файлы в `depends_on:[]` должны существовать |
| Несуществующие навыки | ❌ Ошибка | Навыки в `skills:[]` агентов должны существовать |
| Недекларированные упоминания | ⚠️ Предупреждение | Ссылки в тексте должны быть в `depends_on:[]` |
| Взаимозависимости | 💡 Информация | Группы компонентов, зависящих друг от друга |

### Паттерны ссылок

1. **YAML frontmatter:** `depends_on: [framework/skills/.../SKILL.md]`
2. **Навыки агентов:** `skills: [coding-standards, ...]`
3. **Markdown-ссылки:** `[текст](framework/skills/...)`
4. **Прямые упоминания:** `framework/rules/mandatory-tools.md`

### Интеграция с install.py

`--export-cycles` генерирует `framework-cycles.json`. Данные автоматически загружаются `install.py` для:
- Отображения иконки ↔ у связанных компонентов в дереве
- Автовыбора/автоотключения связанных компонентов

Подробности: [INSTALL-PY-INTEGRATION.md](./INSTALL-PY-INTEGRATION.md)

## install.py

CLI (clone, install). См. `python tools/install.py --help`.

## ai-session-analizer

Локальный анализатор сессий `Claude Code` и `Codex`.

Позволяет разбирать локальные логи по:
- `working_directory`
- `task_id`
- `task_dir`
- `agent_type`
- `step_type`

Поддерживает эвристические категории шагов:
- `vanessa_log_analysis`
- `screenshot_analysis`
- `code_reading`
- `code_writing`
- `test_execution`
- `review`
- `planning`

Запуск:

```bash
python3 tools/ai-session-analizer/analyzer.py build
python3 tools/ai-session-analizer/analyzer.py serve --port 8765
```

## Файлы

- `validate-framework-deps.py` — валидатор зависимостей
- `framework-cycles.json` — сгенерированные данные о взаимозависимостях
- `install.py` — CLI (clone, install)
- `README-validate-deps.md` — подробная документация валидатора
- `INSTALL-PY-INTEGRATION.md` — интеграция взаимозависимостей в install.py

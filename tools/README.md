# Инструменты фреймворка

## validate-framework-deps.py

Валидатор зависимостей фреймворка. Проверяет корректность `depends_on:[]`, находит недекларированные ссылки и группы взаимозависимостей.

```bash
# Проверка
python3 tools/validate-framework-deps.py

# Экспорт взаимозависимостей для 1c-ai-agent-cli
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

### Интеграция с 1c-ai-agent-cli

`--export-cycles` генерирует `framework-cycles.json`. Данные автоматически загружаются `1c-ai-agent-cli` для:
- Отображения иконки ↔ у связанных компонентов в дереве
- Автовыбора/автоотключения связанных компонентов

Подробности: [INSTALL-PY-INTEGRATION.md](./INSTALL-PY-INTEGRATION.md)

## 1c-ai-agent-cli

CLI (clone, install). См. `python tools/1c-ai-agent-cli.py --help`.

## Файлы

- `validate-framework-deps.py` — валидатор зависимостей
- `framework-cycles.json` — сгенерированные данные о взаимозависимостях
- `1c-ai-agent-cli.py` — CLI (clone, install)
- `README-validate-deps.md` — подробная документация валидатора
- `INSTALL-PY-INTEGRATION.md` — интеграция взаимозависимостей в 1c-ai-agent-cli

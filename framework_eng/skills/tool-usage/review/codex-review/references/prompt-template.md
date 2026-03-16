# Prompt Template for Codex Review

This template is used by Claude Code when crafting a prompt for an external reviewer (Codex CLI / Gemini CLI).

---

## Prompt Structure

```markdown
# Задача
<описание: что проверяем, зачем, в каком контексте>

# Артефакт для ревью
Тип: <спецификация | код | тесты | архитектура | форма>

<ссылки на файлы — см. правила ниже>

# Навыки (критерии проверки)
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
<список путей к SKILL.md>

# Контекст
<опционально: номер задачи, затронутые объекты, вопросы>
```

---

## Sections

### Task

Brief, 2-5 sentences. What exactly is being reviewed and why. Keep it concise.

**Good:**
> Review the refactoring specification for the print subsystem. The goal is to move the print forms
> to the BSP "Печать" mechanism. Task #87.

**Bad:**
> Just tell me what is wrong.

---

### Artifact

**Principle: the reviewer works within the project context and reads the files themselves. Never paste file contents into the prompt—only paths.**

If there are no files (the artifact exists only in chat) — provide the text as an exception.

#### Specification / plan

Provide the path to the specification file. Additionally include the paths to the source materials (task, analysis) on which it was based:

```
Прочитай спецификацию: docs/specs/SPEC-резервирование-товаров.md
Исходные материалы (задача, анализ): docs/tasks/task-42.md
```

If there is no specification file — include the plan text in the prompt (exception).

#### Code

Name the basis for the changes (specification or task) and mention that the full list of changes is available via git:

```
Основание изменений: docs/specs/SPEC-резервирование-товаров.md
Список изменённых файлов: выполни `git diff --name-only HEAD~1` или `git status`
Ключевые файлы изменений:
- src/Документы/ЗаказПокупателя/МодульОбъекта.bsl
- src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
```

If a specification was not created — link the task instead:
```
Основание изменений: docs/tasks/task-42.md
```

#### Tests

Provide the path to the tests files or folder along with the basis for them:

```
Тесты: tests/ДССЛ_ТестРезервирование/
Тестируемый модуль: src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
Основание (спецификация): docs/specs/SPEC-резервирование-товаров.md
```

#### Form (UI)

```
Прочитай модуль формы: src/Документы/ЗаказПокупателя/Формы/ФормаДокумента/Module.bsl
Основание изменений: docs/specs/SPEC-резервирование-товаров.md
```

#### Architecture

```
Архитектурное решение: docs/specs/ARCH-интеграция-crm.md
Ключевые файлы реализации:
- src/ОбщиеМодули/ДССЛ_ИнтеграцияCRM/Module.bsl
- src/ОбщиеМодули/ДССЛ_ИнтеграцияCRM_Клиент/Module.bsl
```

---

### Skills

Choose the relevant skills based on the artifact type. Provide paths (the reviewer reads the files themselves):

**For BSL code:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/ssl-patterns/SKILL.md
- framework/skills/bsl-practices/form-patterns/SKILL.md
```

**For specifications / plans:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/spec-writing/spec-standard/SKILL.md
```

**For forms (UI):**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/form-patterns/SKILL.md
- framework/skills/bsl-practices/form-visual-requirements/SKILL.md
```

**For architecture:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/ssl-patterns/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/coding-standards/SKILL.md
```

**For tests:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
```

---

### Context (optional)

Additional information — affected metadata objects, specific questions for the reviewer:

```markdown
# Контекст
- Номер задачи: #42
- Затронутые объекты метаданных: Документ.ЗаказПокупателя, РегистрНакопления.ОстаткиТоваров
- Особые вопросы:
  1. Корректен ли выбранный подход к блокировкам?
  2. Есть ли готовые механизмы БСП, которые автор мог пропустить?
```

Provide current data — the example above only illustrates the format.

---

## Sample prompts by artifact type

### Specification (file exists)

```
# Задача
Проведи ревью спецификации механизма автоматического резервирования товаров
при проведении заказа покупателя. Доработка типовой УТ. Задача #42.

# Артефакт для ревью
Тип: спецификация

Прочитай спецификацию: docs/specs/SPEC-резервирование-товаров.md
Исходные материалы: docs/tasks/task-42.md

# Навыки (критерии проверки)
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/spec-writing/spec-standard/SKILL.md

# Контекст
- Номер задачи: #42
- Затронутые объекты: Документ.ЗаказПокупателя, РН.СвободныеОстатки, РН.РезервыТоваров
```

### Code

```
# Задача
Проведи ревью реализации механизма автоматического резервирования товаров. Задача #42.

# Артефакт для ревью
Тип: код

Основание изменений: docs/specs/SPEC-резервирование-товаров.md
Список изменённых файлов: выполни `git diff --name-only HEAD~1`
Ключевые файлы:
- src/Документы/ЗаказПокупателя/МодульОбъекта.bsl
- src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl

# Навыки (критерии проверки)
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/ssl-patterns/SKILL.md

# Контекст
- Номер задачи: #42
- Затронутые объекты: Документ.ЗаказПокупателя, РН.СвободныеОстатки, РН.РезервыТоваров
```

### Tests

```
# Задача
Проведи ревью тестов механизма резервирования товаров. Задача #42.

# Артефакт для ревью
Тип: тесты

Тесты: tests/ДССЛ_ТестРезервирование/
Тестируемый модуль: src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
Основание (спецификация): docs/specs/SPEC-резервирование-товаров.md

# Навыки (критерии проверки)
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md

# Контекст
- Номер задачи: #42
```

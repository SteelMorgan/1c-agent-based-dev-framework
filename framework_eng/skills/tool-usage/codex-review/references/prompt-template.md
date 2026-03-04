# Prompt template for Codex Review

This template is used by Claude Code when forming a prompt for an external reviewer (Codex CLI / Gemini CLI).

---

## Prompt structure

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

Keep it short, 2–5 sentences. Describe exactly what is being reviewed and why. No fluff.

**Good:**
> Conduct a review of the refactoring specification for the printing subsystem. The goal is to move
> the print forms to the БСП “Print” mechanism. Task #87.

**Bad:**
> Look at what is wrong here.

---

### Artifact

**Principle: the reviewer works within the project context and reads the files themselves. Never paste file contents into the prompt — only paths.**

If there are no files (the artifact exists only in the chat) — provide the text as an exception.

#### Specification / plan

Specify the path to the specification file. Additionally, include paths to the source materials (task, analysis) that formed it:

```
Прочитай спецификацию: docs/specs/SPEC-резервирование-товаров.md
Исходные материалы (задача, анализ): docs/tasks/task-42.md
```

If there is no specification file — provide the plan text in the prompt (exception).

#### Code

Explain the basis for the changes (specification or task) and note that the full list of changes is available via git:

```
Основание изменений: docs/specs/SPEC-резервирование-товаров.md
Список изменённых файлов: выполни `git diff --name-only HEAD~1` или `git status`
Ключевые файлы изменений:
- src/Документы/ЗаказПокупателя/МодульОбъекта.bsl
- src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
```

If the specification was not created — link to the task:
```
Основание изменений: docs/tasks/task-42.md
```

#### Tests

Specify the path to the test files or directory, as well as what they are based on:

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

Choose skills based on the artifact type. Provide paths (the reviewer reads the files themselves):

**For BSL code:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/ssl-patterns/SKILL.md
- framework/skills/bsl-practices/form-patterns/SKILL.md
```

**For a specification / plan:**
```markdown
Прочитай следующие файлы навыков проекта и используй их как критерии при ревью:
- framework/skills/spec-writing/spec-standard/SKILL.md
```

**For a form (UI):**
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

## Sample prompts by type

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

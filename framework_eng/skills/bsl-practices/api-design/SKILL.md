---
name: api-design
description: "Design or review public APIs of 1C subsystems"
---

# API Design — design and review of 1C subsystem interfaces

Based on the Infostart article "API Base": `https://infostart.ru/1c/articles/2683808/`.

## Purpose

1C in enterprise solutions is a modular monolith: libraries consist of functional subsystems, and subsystems communicate through declared interfaces. An arbitrary call across subsystem boundaries is an architectural defect, not a convenient technique. This skill teaches the agent to classify export methods correctly, determine whether changes are allowed, and design APIs with compatibility in mind.

---

## When to use

| Trigger | Action |
|---------|---------|
| Designing a new common module or export method | Apply interface classification (5 categories), document the contract |
| Changing the signature of an existing export method | Check backward compatibility against the table below |
| Adding a parameter to an export method | Determine whether it is mandatory or optional; assess the impact on the version |
| Deleting or renaming an export method | Create a deprecated wrapper in `УстаревшиеПроцедурыИФункции` |
| Code review: calling a method from another subsystem | Check the category of the called method (is the call allowed?) |
| Code review: changing behavior without changing the signature | Check whether this breaks the contract (`ПрограммныйИнтерфейс`) |
| Question about a version bump when releasing changes | Apply versioning rules (section "Versioning") |

---

## Classification of export methods (5 БСП categories)

Each export method must belong to exactly one of the 5 categories. The category is determined by the module `#Область` in which the method is located.

### 1. `#Область ПрограммныйИнтерфейс`

Public contract for **external consumers** - other libraries, application solutions, integrations.

- Backward compatibility is **mandatory**.
- Any change that breaks existing consumers requires a deprecated wrapper.
- Adding an optional parameter is allowed, but requires a version bump.

```bsl
#Область ПрограммныйИнтерфейс

// Возвращает курс валюты на указанную дату.
//
// Параметры:
//  Валюта    - СправочникСсылка.Валюты - валюта, курс которой нужно получить.
//  ДатаКурса - Дата - дата, на которую нужен курс.
//              Если не указана, используется текущая дата сеанса.
//
// Возвращаемое значение:
//  Число - курс валюты. 0 если курс не найден.
//
Функция КурсВалюты(Валюта, ДатаКурса = Неопределено) Экспорт
    Возврат РаботаСВалютами.КурсВалюты(Валюта, ДатаКурса);
КонецФункции

#КонецОбласти
```

### 2. `#Область СлужебныйПрограммныйИнтерфейс`

Contract for calls **from other modules within the same library** (not for external consumers).

- Direct backward compatibility is not guaranteed, but changes are documented.
- A call from another library is a defect unless there is an explicit agreement.

```bsl
#Область СлужебныйПрограммныйИнтерфейс

// Обновляет кэш курсов валют при изменении данных.
// Вызывается только из подписки на событие РаботаСВалютамиОбновлениеКурсов.
//
Процедура ОбновитьКэшКурсовВалют() Экспорт
    // ...
КонецПроцедуры

#КонецОбласти
```

### 3. `#Область ПереопределяемыйИнтерфейс`

Extension point: the library **calls** a consumer method (through overridable modules).

- You cannot add **new mandatory** procedures or parameters.
- You cannot change parameter types.
- You cannot remove parameters that existing implementations may receive.
- New **optional** procedures and parameters are allowed if old implementations continue to work.

```bsl
// Модуль: РаботаСФайламиПереопределяемый
#Область ПереопределяемыйИнтерфейс

// Определяет настройки хранения присоединённых файлов.
//
// Параметры:
//  НастройкиХранения - Структура - настройки, которые нужно заполнить.
//
Процедура ОпределитьНастройкиХраненияФайлов(НастройкиХранения) Экспорт

    // Обязательно вызовите Базовую реализацию или заполните структуру самостоятельно.

КонецПроцедуры

#КонецОбласти
```

### 4. `#Область ДляВызоваИзДругихПодсистем`

Stable integration zone **between subsystems of one solution**.

- Methods are stable, but they are not the library's public API.
- A call from another **library** without an explicit contract is a defect.
- Changes are coordinated with consumer teams.

### 5. `#Область СлужебныеПроцедурыИФункции`

Internal implementation of one functional subsystem. **Not exported.**

- A call from another subsystem is a defect (except for cases explicitly documented in the project).
- If the method is exported and located in this area, either remove `Export` or move it to the appropriate category.

---

## Backward compatibility rules

### What does not break compatibility (can be done in a build-bump)

| Change | Condition |
|--------|-----------|
| Adding an optional parameter | Old calls work without it |
| Adding a new method to `ПрограммныйИнтерфейс` | Does not conflict with consumer names |
| Changing the implementation without changing behavior | The contract is not violated |
| Fixing a bug in the implementation | Documented in the changelog |

### What breaks compatibility (requires a version-bump or deprecated wrappers)

| Change | Requirement |
|--------|-------------|
| Adding a **mandatory** parameter | Preserve the old signature as deprecated, the new method must have a new name or overload |
| Removing a method from `ПрограммныйИнтерфейс` | Deprecated wrapper in `УстаревшиеПроцедурыИФункции` + migration path |
| Renaming a method | Deprecated wrapper with the old name |
| Changing a parameter type (incompatible) | Deprecated wrapper, new parameter through an optional parameter or overload |
| Changing the meaning of a parameter (behavior break) | New parameter name or documenting the breaking change |
| Direct access to another subsystem's data | Forbidden without an explicit API |

### Compatibility takes precedence over style

Backward compatibility requirements **override** cosmetic standards. You cannot rename a public method "for better style" without a deprecated wrapper.

---

## Deprecated areas and migration

When deprecating a method:

1. Move the method to `#Область УстаревшиеПроцедурыИФункции`.
2. Add to the method header: `// Deprecated. Use <NewMethod>().`
3. Call the new method from within it (adapter pattern).
4. Specify the removal version or removal condition.

```bsl
#Область УстаревшиеПроцедурыИФункции

// Устарела. Используйте КурсВалютыНаДату().
// Будет удалена в версии 4.0.
//
// Параметры:
//  Валюта    - СправочникСсылка.Валюты
//  ДатаКурса - Дата
//
// Возвращаемое значение:
//  Число
//
Функция ПолучитьКурсВалюты(Валюта, ДатаКурса) Экспорт
    Возврат КурсВалютыНаДату(Валюта, ДатаКурса);
КонецФункции

#КонецОбласти
```

---

## Interface versioning

| Change type | Bump |
|-------------|------|
| Fixing a bug without changing API behavior | `build` (x.x.x.**N**) |
| New optional parameter, new method in PI | `minor` (x.**N**.0.0) |
| Breaking change with a deprecated wrapper | `minor` + documentation |
| Removal of a deprecated method, incompatible type | `major` (**N**.0.0.0) |

**Forbidden:** introducing a public API expansion (new methods, new parameters) in a release with only a `build` bump. Build is for bug fixes only.

---

## API review workflow

### Step 1. Reconnaissance

Use `code-navigation` (grep over source files) to search for:
- The method by name - find its declaration and determine `#Область`.
- All method call sites - assess the consumer base.
- `#Область УстаревшиеПроцедурыИФункции` sections - check for deprecated wrappers.

```bsl
// Поиск объявления метода
// grep: "Функция КурсВалюты" или "Процедура КурсВалюты"

// Поиск вызовов
// grep: "РаботаСВалютами.КурсВалюты"

// Поиск deprecated-области
// grep: "Устаревшие процедуры и функции"
```

### Step 2. Classify the change

Determine which category the method belongs to, and choose from the table below:

| Change type | Method category | Requirement |
|-------------|-----------------|-------------|
| New method | Any | Place it in the correct `#Область` |
| New optional parameter | `ПрограммныйИнтерфейс` | Allowed, version bump |
| New mandatory parameter | `ПрограммныйИнтерфейс` | Deprecated wrapper required |
| Method removal | `ПрограммныйИнтерфейс` | Deprecated wrapper required |
| Behavior change | `ПрограммныйИнтерфейс` | Considered a breaking change |
| Any change | `СлужебныеПроцедурыИФункции` | Free (within the subsystem) |
| New mandatory parameter | `ПереопределяемыйИнтерфейс` | Forbidden |

### Step 3. Verify through `syntax-checking`

After changing the signature:
- Run static analysis on the calling modules.
- Make sure there are no BSL LS warnings such as "method not found" or "incorrect number of parameters".
- If warnings are suppressed without documenting the reason, that is a red flag.

### Step 4. Document the contract

Every method in `ПрограммныйИнтерфейс` and `ПереопределяемыйИнтерфейс` must have:
- Purpose description.
- Parameters with types and descriptions (including optional parameters and default values).
- Return value (for functions).
- Notes about behavior (exceptions, edge cases).

---

## Usage scenarios

### Scenario 1: Adding a new method to a common module

**Context:** developer asks to add the `ПолучитьОстатокТоваров()` function to the common module `УправлениеЗапасами`.

**Steps:**
1. Determine who the method is for - external consumers or only this subsystem.
2. If it is for external consumers, place it in `#Область ПрограммныйИнтерфейс`.
3. Write a full comment header with parameter types.
4. Make sure nothing unnecessary is exported (the implementation belongs in `СлужебныеПроцедурыИФункции`).
5. Note in the changelog: new Program Interface method -> minor version bump.

### Scenario 2: Review of a signature change

**Context:** reviewer receives a PR where a new **mandatory** parameter `ИсточникКурса` has been added to `РаботаСВалютами.КурсВалюты()`.

**Steps:**
1. `code-navigation`: find all calls to `РаботаСВалютами.КурсВалюты(` - assess the number of consumers.
2. Check the method's `#Область` - if it is `ПрограммныйИнтерфейс`, then it is a breaking change.
3. Require that the old signature be moved to `УстаревшиеПроцедурыИФункции` with a call to the new method.
4. The new method with a mandatory parameter should have a new name (`КурсВалютыИзИсточника`) or the parameter should be made optional.

### Scenario 3: Designing an overridable module

**Context:** architect is designing a new extension point for a notification mechanism.

**Steps:**
1. Determine the procedures that will be called by the library.
2. Make all parameters either structures (easy to extend) or strictly document the types.
3. Do NOT add mandatory procedures after release - only optional ones.
4. Document the contract: what the library passes in, and what it expects on output.
5. Place it in `#Область ПереопределяемыйИнтерфейс`.

### Scenario 4: Detecting a violation during review

**Context:** reviewer notices a call to `ЧастнаяПодсистема.ВнутреннийМетод()` from another module.

**Steps:**
1. Find the `#Область` of the called method.
2. If it is `СлужебныеПроцедурыИФункции`, it is a defect; record it in the review comment.
3. Suggest an alternative: create a method in `ДляВызоваИзДругихПодсистем` or `ПрограммныйИнтерфейс`.
4. If the method is `СлужебныйПрограммныйИнтерфейс` and the call comes from another library, request an explicit agreement or a refactor.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|---------|-------------|--------------|
| Calling `СлужебныеПроцедурыИФункции` from another subsystem | Fragile integration, break during refactoring | Check `#Область` before calling |
| Adding a mandatory parameter without a deprecated wrapper | Breaks all calls in CI | Always create an adapter in `УстаревшиеПроцедурыИФункции` |
| API expansion in a build-only release | Versioning violation, confusion for consumers | Build is for bug fixes only |
| A method in `ПереопределяемыйИнтерфейс` with a new mandatory parameter | Errors in all existing implementations | Only optional parameters in overridable interfaces |
| Changing the meaning of a parameter without documentation | Silent behavioral break | Document in the changelog, consider a version bump |
| Suppressing BSL LS warnings without a reason | Hidden breaking changes | Always document the reason for suppression |
| Direct access to another subsystem's data tables | Tight coupling, architectural debt | Use only documented API |

---

## API-first design checklist

Before implementing a new API:

- [ ] The API is really needed (there is no simpler way)
- [ ] Use cases and consumers are defined
- [ ] A category (`#Область`) is chosen for each method
- [ ] The contract is described: parameters, types, exceptions, idempotency
- [ ] A versioning strategy is defined
- [ ] Tests that model consumer calls are written
- [ ] The changelog and consumer notification are planned

---

## Related resources

- [coding-standards](../coding-standards/SKILL.md) — module structure (`#Область`), rules for commenting export methods
- [test-writing](../test-writing/SKILL.md) — writing tests that model API calls
- [ssl-patterns](../ssl-patterns/SKILL.md) — БСП working patterns (subsystems, interface calls)
- Article "API Base": `https://infostart.ru/1c/articles/2683808/`

---
depends_on:
  - bsl-practices/coding-standards
  - bsl-practices/test-writing
---

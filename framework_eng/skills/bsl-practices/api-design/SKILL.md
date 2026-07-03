---
name: api-design
description: "For designing and reviewing the public API of 1C subsystems"
---

# API Design — designing and reviewing 1C subsystem interfaces

Based on the Infostart article "API Base": `https://infostart.ru/1c/articles/2683808/`.

## Purpose

1C in enterprise solutions is a modular monolith: libraries consist of functional subsystems, and subsystems communicate through declared interfaces. An arbitrary call across subsystem boundaries is an architectural defect, not a convenient technique. This skill teaches the agent to classify export methods correctly, determine whether changes are allowed, and design APIs with compatibility in mind.

---

## When to use

| Trigger | Action |
|---------|----------|
| Designing a new common module or export method | Apply the interface classification (5 categories), document the contract |
| Changing the signature of an existing export method | Check backward compatibility using the table below |
| Adding a parameter to an export method | Determine whether it is required or optional; assess the impact on the version |
| Deleting or renaming an export method | Create a deprecated wrapper in `УстаревшиеПроцедурыИФункции` |
| Code review: calling a method from another subsystem | Check the category of the called method (is the call allowed?) |
| Code review: behavior change without a signature change | Check whether this violates the contract (`ПрограммныйИнтерфейс`) |
| Question about a version bump when releasing changes | Apply the versioning rules (section "Versioning") |

---

## Export method classification (5 БСП categories)

Every export method must belong to exactly one of the 5 categories. For categories 1, 2, 4, and 5, the category is determined by the module `#Область` containing the method. The exception is category 3: it is determined by the fact that the method is located in an overridable module (`*Переопределяемый`), not in a separate region - inside such a module, methods are in the ordinary `#Область ПрограммныйИнтерфейс`.

### 1. `#Область ПрограммныйИнтерфейс`

Public contract for **external consumers** - other libraries, application solutions, integrations.

- Backward compatibility is **mandatory**.
- Any change that breaks existing consumers requires a deprecated wrapper.
- Adding an optional parameter is allowed, but requires a version bump.

```bsl
#Область ПрограммныйИнтерфейс

// Returns the exchange rate on the specified date.
//
// Parameters:
//  Валюта    - СправочникСсылка.Валюты - the currency whose rate must be retrieved.
//  ДатаКурса - Дата - the date for which the rate is needed.
//              If not specified, the current session date is used.
//
// Return value:
//  Число - the exchange rate. 0 if the rate is not found.
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

// Updates the exchange rate cache when data changes.
// Called only from the subscription to the РаботаСВалютамиОбновлениеКурсов event.
//
Процедура ОбновитьКэшКурсовВалют() Экспорт
    // ...
КонецПроцедуры

#КонецОбласти
```

### 3. Overridable modules (`*Переопределяемый`)

Extension point: the library **calls** a consumer method through a module with the `Переопределяемый` suffix (for example, `РаботаСФайламиПереопределяемый`). There is no `ПереопределяемыйИнтерфейс` region - "overridability" is a property of the module itself (its role in the БСП architecture), not a region inside it; methods in such modules are in the ordinary `#Область ПрограммныйИнтерфейс`.

- You cannot add **new required parameters** to existing procedures.
- You cannot change parameter types.
- You cannot remove parameters that existing implementations may receive.
- New procedures and **optional** parameters are allowed if old implementations continue to work (an empty implementation of the new extension point preserves the previous behavior).

```bsl
// Module: РаботаСФайламиПереопределяемый
#Область ПрограммныйИнтерфейс

// Defines attached file storage settings.
//
// Parameters:
//  НастройкиХранения - Структура - settings to be populated.
//
Процедура ОпределитьНастройкиХраненияФайлов(НастройкиХранения) Экспорт

    // You must either call the Base implementation or fill the structure yourself.

КонецПроцедуры

#КонецОбласти
```

### 4. `#Область ДляВызоваИзДругихПодсистем`

Stable integration zone **between subsystems of the same solution**.

- Methods are stable, but they are not the public API of the library.
- A call from another **library** without an explicit contract is a defect.
- Changes are coordinated with consumer teams.

### 5. `#Область СлужебныеПроцедурыИФункции`

Internal implementation of one functional subsystem. **Not exported.**

- A call from another subsystem is a defect (except for cases explicitly documented in the project).
- If a method is exported and located in this area, either remove `Экспорт` or move it to the appropriate category.

---

## Backward compatibility rules

### What does not break compatibility (can be done in a build bump)

| Change | Condition |
|-----------|---------|
| Adding an optional parameter | Old calls work without it |
| Adding a new method to `ПрограммныйИнтерфейс` | Does not conflict with consumer names |
| Changing the implementation without changing behavior | The contract is not violated |
| Fixing a bug in the implementation | Documented in the changelog |

### What breaks compatibility (requires a version bump or deprecated wrapper)

| Change | Requirement |
|-----------|------------|
| Adding a **required** parameter | Preserve the old signature as deprecated, new method - a new name or overload |
| Removing a method from `ПрограммныйИнтерфейс` | Deprecated wrapper in `УстаревшиеПроцедурыИФункции` + migration path |
| Renaming a method | Deprecated wrapper with the old name |
| Changing the parameter type (incompatible) | Deprecated wrapper, new parameter - via optional parameter or overload |
| Changing the **meaning** of a parameter (behavior break) | New parameter name or documentation of the breaking change |
| Direct access to another subsystem's data | Prohibited without an explicit API |

### Compatibility takes priority over style

Backward compatibility requirements **override** cosmetic standards. You cannot rename a public method "for the sake of correct style" without a deprecated wrapper.

---

## Deprecated areas and migration

When a method becomes obsolete:

1. Move the method to `#Область УстаревшиеПроцедурыИФункции`.
2. Add to the method header: `// Устарела. Используйте <НовыйМетод>().`
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
|---------------|------|
| Bug fix without changing API behavior | `build` (x.x.x.**N**) |
| New optional parameter, new method in the public interface | `minor` (x.**N**.0.0) |
| Breaking change with a deprecated wrapper | `minor` + documentation |
| Removal of a deprecated method, incompatible type | `major` (**N**.0.0.0) |

**Prohibited:** introducing a public API extension (new methods, new parameters) in a release with only a build bump. Build is for bug fixes only.

---

## API review workflow

### Step 1. Reconnaissance

Use `code-navigation` (grep over sources) to search for:
- The method by name - find its declaration and determine the #Область.
- All call sites of the method - assess the consumer scope.
- Sections `#Область УстаревшиеПроцедурыИФункции` - check for deprecated wrappers.

```bsl
// Search for the method declaration
// grep: "Функция КурсВалюты" or "Процедура КурсВалюты"

// Search for calls
// grep: "РаботаСВалютами.КурсВалюты"

// Search for the deprecated area
// grep: "Устаревшие процедуры и функции"
```

### Step 2. Classify the change

Determine which category the method belongs to, and choose from the table below:

| Change type | Method category | Requirement |
|---------------|-----------------|------------|
| New method | Any | Place it in the correct #Область |
| New optional parameter | `ПрограммныйИнтерфейс` | Allowed, version bump |
| New required parameter | `ПрограммныйИнтерфейс` | Deprecated wrapper required |
| Method removal | `ПрограммныйИнтерфейс` | Deprecated wrapper required |
| Behavior change | `ПрограммныйИнтерфейс` | Considered a breaking change |
| Any change | `СлужебныеПроцедурыИФункции` | Free (within the subsystem) |
| New required parameter | Overridable modules (`*Переопределяемый`) | Prohibited |

### Step 3. Check through `syntax-checking`

After changing the signature:
- Run static analysis on the calling modules.
- Make sure there are no BSL LS warnings such as "method not found" or "incorrect number of parameters".
- If warnings are suppressed without documenting the reason, that is a red flag.

### Step 4. Document the contract

Every method from `#Область ПрограммныйИнтерфейс` - including methods of overridable modules (`*Переопределяемый`) - must have:
- Purpose description.
- Parameters with types and descriptions (including optional parameters and default values).
- Return value (for functions).
- Notes about behavior (exceptions, edge cases).

---

## Usage scenarios

### Scenario 1: Adding a new method to a common module

**Context:** developer asks to add the function `ПолучитьОстатокТоваров()` to the common module `УправлениеЗапасами`.

**Steps:**
1. Determine who the method is for - external consumers or only this subsystem.
2. If for external consumers, place it in `#Область ПрограммныйИнтерфейс`.
3. Write a complete comment header with parameter types.
4. Make sure nothing unnecessary is exported (the implementation is in `СлужебныеПроцедурыИФункции`).
5. Mark in the changelog: new public-interface method -> minor version bump.

### Scenario 2: Reviewing a signature change

**Context:** reviewer receives a PR where a new **required** parameter `ИсточникКурса` has been added to `РаботаСВалютами.КурсВалюты()`.

**Steps:**
1. `code-navigation`: find all calls to `РаботаСВалютами.КурсВалюты(` - assess the number of consumers.
2. Check the method #Область - if `ПрограммныйИнтерфейс`, then this is a breaking change.
3. Require the old signature to be moved to `УстаревшиеПроцедурыИФункции` with a call to the new method.
4. The new method with the required parameter should have a new name (`КурсВалютыИзИсточника`) or the parameter should be made optional.

### Scenario 3: Designing an overridable module

**Context:** architect is designing a new extension point for a notification mechanism.

**Steps:**
1. Determine the procedures that will be called by the library.
2. Make all parameters either structures (easy to extend) or document the types precisely.
3. Do NOT add required parameters to extension point procedures after release - only optional ones.
4. Document the contract: what the library passes in, what it expects in return.
5. Place it in a separate overridable module (`ИмяПодсистемыПереопределяемый`), in its ordinary `#Область ПрограммныйИнтерфейс` - not in a special region.

### Scenario 4: Detecting a violation during review

**Context:** reviewer notices a call to `ЧастнаяПодсистема.ВнутреннийМетод()` from another module.

**Steps:**
1. Find the #Область of the called method.
2. If it is `СлужебныеПроцедурыИФункции`, this is a defect, and it should be recorded in the review comment.
3. Suggest an alternative: create a method in `ДляВызоваИзДругихПодсистем` or `ПрограммныйИнтерфейс`.
4. If the method is in `СлужебныйПрограммныйИнтерфейс` and the call is from another library, request an explicit agreement or a refactoring.

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|-------------|--------------|
| Calling `СлужебныеПроцедурыИФункции` from another subsystem | Fragile integration, break during refactoring | Check #Область before calling |
| Adding a required parameter without a deprecated wrapper | Breaks all calls in CI | Always create an adapter in `УстаревшиеПроцедурыИФункции` |
| API expansion in a build-only release | Versioning violation, confusion for consumers | Build is for bug fixes only |
| A method in an overridable module (`*Переопределяемый`) with a new required parameter | Errors in all existing implementations | Only optional parameters are allowed in overridable modules |
| Changing the meaning of a parameter without documenting it | Silent behavioral break | Document it in the changelog, consider a version bump |
| Suppressing BSL LS warnings without a reason | Hidden breaking changes | Always document the reason for suppression |
| Direct access to another subsystem's data tables | Tight coupling, architectural debt | Use only the documented API |

---

## API-first design checklist

Before implementing a new API:

- [ ] The API is truly needed (there is no simpler way)
- [ ] Use cases and consumers are defined
- [ ] A category (#Область) has been chosen for each method
- [ ] The contract is described: parameters, types, exceptions, idempotency
- [ ] A versioning strategy is defined
- [ ] Tests that model consumer calls have been written
- [ ] Changelog and consumer notification are planned

---

## Related resources

- [coding-standards](../coding-standards/SKILL.md) - module structure (#Область), rules for commenting export methods
- [test-writing](../test-writing/SKILL.md) - writing tests that model API calls
- [ssl-patterns](../ssl-patterns/SKILL.md) - patterns for working with БСП (subsystems, interface-based calls)
- Article "API Base": `https://infostart.ru/1c/articles/2683808/`

---
depends_on:
  - bsl-practices/coding-standards
  - bsl-practices/test-writing
---

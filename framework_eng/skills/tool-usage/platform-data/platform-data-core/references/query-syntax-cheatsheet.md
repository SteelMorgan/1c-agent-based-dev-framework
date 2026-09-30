# 1C Query Language Syntax Cheat Sheet

Quick reference for 1C query language syntax for use with `execute_query` / `validate_query`.

> Query-writing patterns in BSL code (parameterization, temporary tables, queries in loops) - see the [`query-patterns`](../../../../bsl-practices/query-patterns/SKILL.md) skill.

---

## Basic Structure

```sql
ВЫБРАТЬ [РАЗРЕШЕННЫЕ] [РАЗЛИЧНЫЕ] [ПЕРВЫЕ N]
    <Поля выборки>
ИЗ
    <Источники данных>
[ГДЕ <Условия отбора>]
[СГРУППИРОВАТЬ ПО <Поля группировки>]
[УПОРЯДОЧИТЬ ПО <Поля сортировки>]
```

**РАЗРЕШЕННЫЕ** - take RLS into account (row-level access rights). Recommended by default.

---

## Data Sources

| Object type | Syntax |
|-------------|-----------|
| Справочник | `Справочник.ИмяСправочника` |
| Документ | `Документ.ИмяДокумента` |
| Document tabular section | `Документ.ИмяДокумента.ИмяТабличнойЧасти` |
| РегистрНакопления (balances) | `РегистрНакопления.ИмяРегистра.Остатки()` |
| РегистрНакопления (turnovers) | `РегистрНакопления.ИмяРегистра.Обороты()` |
| РегистрНакопления (balances and turnovers) | `РегистрНакопления.ИмяРегистра.ОстаткиИОбороты()` |
| Регистр сведений | `РегистрСведений.ИмяРегистра` |
| Регистр сведений (latest slice) | `РегистрСведений.ИмяРегистра.СрезПоследних()` |
| РегистрБухгалтерии | `РегистрБухгалтерии.ИмяРегистра` |
| ПланСчетов | `ПланСчетов.ИмяПлана` |

---

## Register Resource Suffixes

**Balances:** `<ResourceName>Balance` (for example: `КоличествоОстаток`)

**Turnovers:** `<ResourceName>Turnover`, `КоличествоПриход`, `КоличествоРасход`

**Balances and turnovers:** `<ResourceName>OpeningBalance`, `Receipt`, `Expense`, `Turnover`, `ClosingBalance`

---

## Filtering in Virtual Tables

Pass conditions into the virtual table parameters, not into WHERE:

❌ **Inefficient** - first fetch all balances, then filter:
```sql
ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки() КАК Остатки
ГДЕ Остатки.Склад.Наименование = "Основной склад"
```

✅ **Efficient** - filter during calculation:
```sql
ИЗ РегистрНакопления.ТоварыНаСкладах.Остатки(
    ,
    Склад.Наименование = "Основной склад"
) КАК Остатки
```

---

## Working with NULL

When using LEFT JOIN, fields from the right table can be NULL. Use `ЕСТЬNULL()`:

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Товары.Наименование,
    ЕСТЬNULL(Остатки.КоличествоОстаток, 0) КАК Остаток
ИЗ
    Справочник.Номенклатура КАК Товары
    ЛЕВОЕ СОЕДИНЕНИЕ РегистрНакопления.ТоварыНаСкладах.Остатки() КАК Остатки
    ПО Остатки.Номенклатура = Товары.Ссылка
```

**NULL check:** use `ЕСТЬ NULL` / `НЕ ЕСТЬ NULL`, never `= NULL`:

```sql
ГДЕ Поле ЕСТЬ NULL
ГДЕ Поле НЕ ЕСТЬ NULL
```

---

## ВЫРАЗИТЬ for Composite Types

Fields of a composite type (for example, `Регистратор`) when accessed through a dot create implicit JOINs to **all** possible tables. Narrow the type with `ВЫРАЗИТЬ`:

```sql
-- ПЛОХО: JOIN ко всем типам Регистратора
ВЫБРАТЬ Регистратор.Номер ИЗ РегистрНакопления.ОстаткиТоваров

-- ХОРОШО: один JOIN
ВЫБРАТЬ ВЫРАЗИТЬ(Регистратор КАК Документ.РеализацияТоваровУслуг).Номер
ИЗ РегистрНакопления.ОстаткиТоваров
ГДЕ Регистратор ССЫЛКА Документ.РеализацияТоваровУслуг
```

---

## WHERE Operators

| Operator | 1C syntax |
|----------|--------------|
| Equal / not equal | `=`, `<>` |
| Comparison | `<`, `>`, `<=`, `>=` |
| Range | `МЕЖДУ X И Y` (inclusive) |
| In list | `В (val1, val2, ...)` or `В (ВЫБРАТЬ ...)` |
| NULL | `ЕСТЬ NULL`, `НЕ ЕСТЬ NULL` (not `= NULL`) |
| Reference type check | `ССЫЛКА Документ.ИмяДокумента` |
| Pattern | `ПОДОБНО "шаблон"` - `%` any string, `_` one character |
| Logic | `И`, `ИЛИ`, `НЕ` |

**OR** worsens index usage. Replace it with `В (...)` or `ОБЪЕДИНИТЬ ВСЕ`.

---

## Predefined Values

```sql
ГДЕ ВидОперации = Значение(Перечисление.ВидыОпераций.Продажа)
ГДЕ Склад = Значение(Справочник.Склады.ОсновнойСклад)
```

---

## Working with Dates

```sql
ГДЕ Документы.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Документы.Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

**Format:** `ДАТАВРЕМЯ(Year, Month, Day[, Hour, Minute, Second])`

---

## String Values

Strings in queries use double quotes:

```sql
ГДЕ Номенклатура.Наименование = "iPhone 17 Pro Max, 512 Гб"
ГДЕ Контрагент.ИНН = "7707083893"
```

---

## Query Examples

### Inventory balances in warehouses

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Остатки.Номенклатура.Наименование КАК Товар,
    Остатки.Склад.Наименование КАК Склад,
    Остатки.КоличествоОстаток КАК Количество
ИЗ
    РегистрНакопления.ТоварыНаСкладах.Остатки() КАК Остатки
ГДЕ
    Остатки.КоличествоОстаток > 0
УПОРЯДОЧИТЬ ПО
    Товар
```

### Documents for a period

```sql
ВЫБРАТЬ ПЕРВЫЕ 50
    Док.Номер,
    Док.Дата,
    Dok.Контрагент.Наименование КАК Контрагент,
    Dok.СуммаДокумента
ИЗ
    Документ.РеализацияТоваровУслуг КАК Dok
ГДЕ
    Dok.Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Dok.Дата < ДАТАВРЕМЯ(2026, 2, 1)
    И Dok.Проведен = ИСТИНА
УПОРЯДОЧИТЬ ПО
    Dok.Дата УБЫВ
```

### Current prices (latest slice)

```sql
ВЫБРАТЬ ПЕРВЫЕ 100
    Цены.Номенклатура.Наименование КАК Товар,
    Цены.Номенклатура.Код КАК КодТовара,
    Цены.ТипЦен.Наименование КАК ТипЦены,
    Цены.Цена
ИЗ
    РегистрСведений.ЦеныНоменклатуры.СрезПоследних() КАК Цены
УПОРЯДОЧИТЬ ПО
    Товар
```

### Aggregate - totals for a period (without ПЕРВЫЕ)

```sql
ВЫБРАТЬ
    КОЛИЧЕСТВО(Ссылка) КАК Количество,
    СУММА(СуммаДокумента) КАК ИтогоСумма
ИЗ
    Документ.РеализацияТоваровУслуг
ГДЕ
    Дата >= ДАТАВРЕМЯ(2026, 1, 1)
    И Дата < ДАТАВРЕМЯ(2026, 2, 1)
```

### Search by name (fuzzy)

```sql
ВЫБРАТЬ ПЕРВЫЕ 50
    Ссылка,
    Наименование
ИЗ
    Справочник.Контрагенты
ГДЕ
    Наименование ПОДОБНО "%рога%"
```

### Existence check

```sql
ВЫБРАТЬ ПЕРВЫЕ 1
    Ссылка
ИЗ
    Справочник.Контрагенты
ГДЕ
    ИНН = "7707083893"
```

### Exclude marked for deletion

```sql
ГДЕ НЕ ПометкаУдаления
```

### Last N documents

```sql
ВЫБРАТЬ ПЕРВЫЕ 10
    Ссылка,
    Дата,
    Номер,
    СуммаДокумента
ИЗ
    Документ.РеализацияТоваровУслуг
УПОРЯДОЧИТЬ ПО
    Дата УБЫВ
```

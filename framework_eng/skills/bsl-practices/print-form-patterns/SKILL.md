---
name: print-form-patterns
installable: true
description: "Design and implementation of 1C print forms based on MXL layouts: obtaining the layout, filling parameters, outputting areas, integrating with the БСП print mechanism, and runtime validation."
---

# Print forms based on MXL layouts

## Purpose

Use this skill for print forms where the result is built as a `ТабличныйДокумент` from MXL areas.

Design the layout structure through `mxl-layout-patterns`. This skill describes the application layer of a print form:

- where to get the data;
- how to obtain the layout;
- how to fill parameters;
- in what order to output areas;
- how to return the result to the print mechanism.

## Difference from СКД on your own layout

The print form itself controls filling and output order:

```bsl
Макет = ПолучитьМакет("Макет");
Область = Макет.ПолучитьОбласть("Строка");
Область.Параметры.Поле = Значение;
ТабличныйДокумент.Вывести(Область);
```

СКД on your own layout fills parameters through СКД expressions and `groupTemplate`. Use `skd-custom-layout` for this.

## Workflow

1. Define the purpose of the print form and the print command.
2. Prepare the data without direct access to the DBMS.
3. Design the MXL layout through `mxl-layout-patterns`.
4. Define the areas:
   - header;
   - top section;
   - row;
   - group;
   - total;
   - footer.
5. Define the parameters of each area.
6. Write the code for filling parameters and outputting areas.
7. Integrate the result into the standard print mechanism.
8. Verify runtime generation and the visual result.

If the visual framework came from imported Excel/MXL, do not consider the task solved by the mere fact of import.
A print form begins where the code explicitly takes responsibility for:

- headings and service labels;
- binding data to parameters or coordinates;
- totals and recalculations;
- output order and hiding the editor grid, if a printable view is needed.

## Minimal Template

```bsl
ТабличныйДокумент = Новый ТабличныйДокумент;
Макет = ПолучитьМакет("Макет");

ОбластьШапка = Макет.ПолучитьОбласть("Шапка");
ЗаполнитьЗначенияСвойств(ОбластьШапка.Параметры, ДанныеШапки);
ТабличныйДокумент.Вывести(ОбластьШапка);

ОбластьСтрока = Макет.ПолучитьОбласть("Строка");
Для Каждого СтрокаДанных Из ТаблицаДанных Цикл
    ЗаполнитьЗначенияСвойств(ОбластьСтрока.Параметры, СтрокаДанных);
    ТабличныйДокумент.Вывести(ОбластьСтрока);
КонецЦикла;

Возврат ТабличныйДокумент;
```

Use the standard configuration patterns and the БСП patterns for obtaining the layout and registering the print form if the configuration works through the print subsystem.

## What to Check

- The layout is found in the standard way.
- All areas exist.
- All parameters are filled.
- The tabular document is formed without errors.
- The number of rows matches the data.
- Empty values are handled explicitly.
- The runtime result is saved or captured for verification.
- Visual verification passes through `mxl-layout-patterns`: layout, border, style, and negative checks.
- If the layout came from an Excel import, it is separately recorded what the import brought in and what was completed
  by the print form code.

## Related Skills

- `mxl-layout-patterns` — visual layout model.
- `ssl-patterns` — if the БСП print mechanism is used.
- `query-patterns` — if new 1C query logic is needed for the data.

---
depends_on:
  - mxl-layout-patterns
---

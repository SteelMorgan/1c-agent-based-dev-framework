---
name: skd-report-patterns
installable: true
description: "Design and analysis of standard 1C SKD reports: data sets, fields, resources, parameters, settings, variants, groupings, compiler, composition processor, and result output."
---

# SKD Reports

## Purpose

Use this skill for SKD as a data and composition mechanism:

- data sets;
- data set relations;
- fields;
- calculated fields;
- resources;
- parameters;
- settings;
- variants;
- filters;
- sorts;
- groupings;
- programmatic result generation.

If the report uses custom areas of the `Макеты` tab, also use `skd-custom-layout`.

## Basic Chain

Typical programmatic chain:

```bsl
Настройки = КомпоновщикНастроек.ПолучитьНастройки();
КомпоновщикМакета = Новый КомпоновщикМакетаКомпоновкиДанных;
МакетКомпоновки = КомпоновщикМакета.Выполнить(СхемаКомпоновкиДанных, Настройки, ДанныеРасшифровки);

ПроцессорКомпоновки = Новый ПроцессорКомпоновкиДанных;
ПроцессорКомпоновки.Инициализировать(МакетКомпоновки, ВнешниеНаборыДанных, ДанныеРасшифровки);

ПроцессорВывода = Новый ПроцессорВыводаРезультатаКомпоновкиДанныхВТабличныйДокумент;
ПроцессорВывода.УстановитьДокумент(ДокументРезультат);
ПроцессорВывода.Вывести(ПроцессорКомпоновки);
```

Platform graph:

```text
СхемаКомпоновкиДанных
  -> ИсточникДоступныхНастроекКомпоновкиДанных
  -> КомпоновщикНастроекКомпоновкиДанных
      -> НастройкиКомпоновкиДанных
          -> КомпоновщикМакетаКомпоновкиДанных.Выполнить()
              -> МакетКомпоновкиДанных
                  -> ПроцессорКомпоновкиДанных.Инициализировать()
                      -> ЭлементРезультатаКомпоновкиДанных*
                          -> ПроцессорВыводаРезультатаКомпоновкиДанныхВТабличныйДокумент
                              -> ТабличныйДокумент
```

`ДанныеРасшифровкиКомпоновкиДанных` are passed into layout generation, the composition processor, and the result form. External data sets are passed to `ПроцессорКомпоновкиДанных.Инициализировать()`.

For element-by-element output, use `НачатьВывод()`, the `ПроцессорКомпоновки.Следующий()` loop, `ВывестиЭлемент()`, and `ЗакончитьВывод()`. This mode is needed when you need to intervene in a result element before output.

## SKD Capabilities

Data sets:

- `DataSetQuery` retrieves data with a 1C query. Use it when the data can be expressed as a query and you need standard filters, parameters, and platform optimization.
- `DataSetObject` describes an external data set. Data is passed in code through the external data sets structure when the composition processor is initialized.
- `DataSetUnion` combines several data sets with a common field structure into one logical stream.

Use data set connections for master-detail, constraining the receiving set by a parameter, and linking query/object data sets. Make sure that the connection parameter is actually used in the receiving query; for the `В` operation, a parameter list must be allowed.

Use calculated fields for lightweight derived values that must participate in settings, filters, sorting, or resources. Move heavy algorithms and data access into the query or external data set preparation.

Resources are responsible for aggregation. Do not substitute a source field with a resource: the source field stores detail values, while the resource defines the aggregate expression and groupings.

If a value must be output at the grouping or total level, do not pull the "raw"
field into the grouped node as if it were already aggregated. For such a level, explicitly define a resource or calculated field with
an aggregate function, otherwise the schema may be assembled formally but fail during runtime generation because of
incorrect use of the field in the grouping.

Parameters are divided by purpose:

- query parameters;
- user data parameters;
- parameters with an available value list;
- hidden service parameters;
- functional option parameters;
- derived period parameters.

`Авто` is suitable for parameters included based on actual use. `Всегда` is needed for mandatory query parameters or field availability.

A settings variant fixes a specific output scenario. User settings are applied on top of the variant, and fixed settings are applied additionally through the composer.

The main output structure elements are grouping, table, chart, nested report, and detail records. With custom layouts, structure elements are linked to areas through `groupTemplates`; with standard output, the platform builds the result areas.

## XML Map

`СхемаКомпоновкиДанных` is usually located in `Templates/<ИмяСКД>/Ext/Template.xml`.

Typical XML sections:

- `<dataSet>` — datasets;
- `<dataSetLink>` — dataset links;
- `<calculatedField>` — calculated fields;
- `<totalField>` — resources;
- top-level `<parameter>` — schema parameters;
- `<settingsVariant>` and `dcsset:*` — variants and settings;
- `<template>` and `<groupTemplate>` — custom areas of the SKD layout.

For XML analysis, use `xml-gen skd info`, not manual parsing. If the runtime breaks on formally valid XML, compare the structure with a minimal reference that opens normally and record the generation defect while working with xml-gen.

## What to Check

- The dataset exists and returns the required fields.
- Fields, resources, and calculated fields are available in settings.
- Parameters have correct values and types.
- The variant structure matches the required output.
- Filters and sorts are described using SKD tools, if this is part of the report.
- External object datasets are passed under the names expected by the schema.
- The runtime result is obtained on the platform, not only the generated XML.
- If an external report has no own custom form, the runtime check goes through
  the standard report form and the standard generation command.

## Tools

```bash
xml-gen skd info <Template.xml> --mode overview
xml-gen skd info <Template.xml> --mode fields
xml-gen skd info <Template.xml> --mode resources
xml-gen skd info <Template.xml> --mode params
xml-gen skd info <Template.xml> --mode variant --name <Вариант>
xml-gen validate --type skd <Template.xml>
```

Use `skd-dsl` to create a schema. For targeted edits to an existing schema, use `skd-edit`.

---
depends_on: []
---

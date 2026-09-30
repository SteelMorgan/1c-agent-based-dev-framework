# Example `layout/border/style/data-binding` specification

A generalized example of what a completed print form specification looks like
for a document with one tabular section. Area and parameter names are examples;
the section structure follows a real, verified layout.

## Layout spec

Base grid:

- page: `A4-portrait`;
- columns: `12`;
- the table and attribute blocks sit on one shared grid so that the borders do not
  drift between sections.

Areas:

1. `Заголовок`
   - two rows;
   - row 1: document title across the full width;
   - row 2: document number and date without time.
2. `Реквизиты`
   - section heading and several data rows;
   - compact `label + value` rows with two fields per row;
   - full-width rows for long values (contract, source),
     so that the text is not truncated.
3. `ШапкаТаблицы`
   - one row;
   - columns: `№`, `Наименование`, `Количество`, `Курс, <валюта>`,
     `Сумма, <валюта>`;
   - the currency code is inserted into the monetary headings from the document data.
4. `Строка`
   - a repeating row;
   - one area per tabular section record.
5. `Итого`
   - one row with the total text, currency code, and amount.
6. `Подписи`
   - section heading;
   - signatory roles;
   - separate blank lines for handwritten signatures;
   - full names below the lines as labels.

## Border spec

- `Реквизиты`: each label/value cell has a closed rectangular `all` border
  in a muted color; the section heading has no border.
- `ШапкаТаблицы`: closed `all` borders in a higher-contrast color.
- `Строка`: each cell has `all`, in a color slightly lighter than the header.
- `Итого`:
  - left block: `top,bottom,left`;
  - currency: `top,bottom` only;
  - amount: `all`.
- `Подписи`: blank rows have only `bottom`; lines are separated
  by empty columns and do not merge across sides.

## Style spec

- heading: large bold, dark blue text, centered;
- subtitle: small gray-blue, centered;
- label in attributes: small bold on a light background;
- table header: bold on an accent light background;
- table rows: regular small font;
- numeric columns: right-aligned, precision according to the document attribute
  (quantity `ЧДЦ=8`, amounts `ЧДЦ=2`);
- total: accent background, bold;
- signatures: no fill, roles in semibold gray; bottom border only on the
  signature line, full name below it;
- row heights no less than the height of the corresponding font text;
- long attribute values wrap and do not extend beyond cells.

## Data-binding spec

`Заголовок`: `ЗаголовокДокумента`, `НомерДокумента`, `ДатаДокумента`.

`Реквизиты`: `Аккаунт`, `Договор`, `ТипПортфеля`, `ВалютаОценкиПортфеля`,
`ИсточникСоздания`, `ВнешнийID`.

`ШапкаТаблицы`: `ВалютаКурса`, `ВалютаСуммы`.

`Строка`: `НомерСтроки`, `Монета`, `Количество`, `КурсКВалютеОценки`,
`СуммаВВалютеОценки`.

`Итого`: `ВалютаИтога`, `ИтоговаяСумма`.

`Подписи`: `Управляющий`, `Клиент`, `Операционист`.

## Negative checks (verified on a real layout)

- the print form does not use СКД: no `DataCompositionSchema`,
  `КомпоновщикМакетаКомпоновкиДанных`, `ПроцессорКомпоновкиДанных`;
- table rows repeat in a loop over the data, not in a fixed grid;
- the signature block has no outer frame or side borders; bottom borders only on
  three separate lines;
- date without time;
- long values are fully readable;
- currency code is present in monetary headers.

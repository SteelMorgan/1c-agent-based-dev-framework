# OneScript — reference (syntax and differences from 1C)

## Differences from 1C

- .NET garbage collection, not reference counting. For explicit cleanup: `ВыполнитьСборкуМусора()`, `ОсвободитьОбъект()`.

## Date format

In the `Формат` function, the DF parameter adds the `р` format (Cyrillic) or `f` for fractional seconds:

```bsl
Строка = Формат(Дата, "ДФ=ЧЧ:мм:сс.ррр");
// "20:02:53.345"
```

Up to 6 digits in the fractional part.

## Constructor in an expression

In OneScript, it is allowed to access the result of a constructor in the same statement:

```bsl
Если Новый Файл("myfile.txt").Существует() Тогда
    // ...
КонецЕсли;
```

This is forbidden in 1C.

## Parameterized exceptions

You can pass an object with additional data to an exception:

```bsl
ВызватьИсключение Новый ИнформацияОбОшибке("Error text", ДополнительныеДанные);
```

In the `Исключение` block, `ИнформацияОбОшибке()` will have the `Параметры` property populated.

## Rethrow with ИнформацияОбОшибке

Rethrow an existing exception (for example, from a background job):

```bsl
ОшибкаВЗадании = МоеФоновоеЗадание.ИнформацияОбОшибке;
Если ОшибкаВЗадании <> Неопределено Тогда
    ВызватьИсключение ОшибкаВЗадании;
КонецЕсли;
```

## Library loader

At the root of the library, you can place `package-loader.os` with the procedure `ПриЗагрузкеБиблиотеки(Путь, СтандартнаяОбработка, Отказ)`. Calls to `ДобавитьКласс(ИмяФайла, Идентификатор)` and `ДобавитьМодуль(ИмяФайла, Идентификатор)` register types and modules. If `СтандартнаяОбработка = Ложь`, the built-in loading algorithm is not executed.

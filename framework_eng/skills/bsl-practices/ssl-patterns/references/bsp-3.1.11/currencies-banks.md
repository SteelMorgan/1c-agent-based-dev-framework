# Currencies, banks, and work schedules

Three related master-data subsystems: **Currencies** (rates,
conversion, amount in words), **Banks** (BIK classifier), and **WorkSchedules** /
**CalendarSchedules** (production calendars, calculation of working dates,
work schedule). Loading directories (banks, rates, calendars) is handled by
the `WorkWithClassifiers` mechanism - see `classifiers.md`; here - reading and calculations.

## Modules

Currencies - module names do **not** match the subsystem (there is no `Currencies` module):

- `WorkWithCurrencyRates` - server stable API: rate, conversion, amount
  in words, adding currencies. region `ProgrammaticInterface` (stable).
- `WorkWithCurrencyRatesClientServer` - `ConvertByRate` (without a server call,
  using prepared rate parameters). Client + Server, stable.
- `WorkWithCurrencyRatesClientLocalization` - `ShowCurrencyRatesLoad`
  (interactive rate loading). Client, stable.
- `WorkWithCurrencyRatesClient` - ⚠️ `ShowCurrencyRatesLoad` here is in the region
  `ServiceProgrammaticInterface` (not stable) - use the variant from
  `...ClientLocalization`.
- `WorkWithCurrencyRatesService` / `...ServerCall` / `...Global` - ⚠️ service.
- `WorkWithCurrencyRatesOverride` - hooks (do not call directly).

Banks:

- `WorkWithBanks` - server stable API: `BIKInfo`,
  `ExplanationOfInvalidBank`. `GetClassifierData` - ⚠️ obsolete
  (region `DeprecatedProceduresAndFunctions`).
- `WorkWithBanksClient` - `ChooseFromBIKDirectory` (selection UI). Client, stable.
- `WorkWithBanksService` / `...ServerCall` / `...Global` - ⚠️ service.
- `WorkWithBanksOverride` - hooks.

Work schedules and calendars:

- `CalendarSchedules` - main server API: date calculation, nearest working
  dates, schedule, form filling, main calendar. Accepts both
  `CatalogRef.ProductionCalendars` and `CatalogRef.Calendars`
  (internally switches to `WorkSchedules`). stable.
- `WorkSchedules` - work schedule API (`CatalogRef.Calendars`):
  `DateBySchedule`, `NearestDatesIncludedInSchedule`, `WorkSchedulesForPeriod`.
  ⚠️ `WorkSchedules.DateDifferenceByCalendar` - in `ServiceProgrammaticInterface`
  (not stable); for date difference use `CalendarSchedules.DateDifferenceByCalendar`.
- `WorkSchedulesClient` - string schedule row collection utilities (not for date calculations).
- `CalendarSchedulesService` / `CalendarSchedulesOverride` - ⚠️ service
  / hooks.

⚠️ Typical pitfall - inventing modules `Currencies`, `Banks`, `Organizations`. The real
names are `WorkWithCurrencyRates`, `WorkWithBanks`, `OrganizationsServer`.

## Scenarios

### 1. Get a currency rate and convert an amount

**Task:** read the currency rate on a date; convert an amount from one currency to
another using the rate for that date; convert on the client using previously received rates.

**Functions:**
`WorkWithCurrencyRates.GetCurrencyRate(Currency, RateDate) Export`
— Function -> `Structure` (`Rate, Multiplicity, Currency, RateDate`) / `Undefined`, region `ProgrammaticInterface` (stable). Server.
`WorkWithCurrencyRates.ConvertToCurrency(Amount, SourceCurrency, TargetCurrency, Date) Export`
— Function -> `Number`, region `ProgrammaticInterface` (stable). Server. Obtains both rates internally.
`WorkWithCurrencyRatesClientServer.ConvertByRate(Amount, CurrentRateParameters, NewRateParameters) Export`
— Function -> `Number`, region `ProgrammaticInterface` (stable). Client + Server. Conversion using ready-made rate structures.

**Parameters:**
- `Currency` (`CatalogRef.Currencies`), `RateDate` (`Date`).
- `Amount` (`Number`), `SourceCurrency` / `TargetCurrency` (`CatalogRef.Currencies`), `Date` (`Date`).
- `CurrentRateParameters` / `NewRateParameters` (`Structure`) - from `GetCurrencyRate`: `Currency, Rate, Multiplicity`.

**Example:**
```bsl
// Server: get rate and convert
Rate = WorkWithCurrencyRates.GetCurrencyRate(Object.Currency, Object.Date);
If Rate = Undefined Then
    CommonPurpose.NotifyUser(
        NStr("ru = 'Currency rate for the date is not set.'"), , , "Object", Cancel);
    Return;
EndIf;

AmountRUB = WorkWithCurrencyRates.ConvertToCurrency(
    Object.Amount, Object.Currency,
    Catalogs.Currencies.FindByCode("643"), Object.Date);

// Client: conversion using already received rates (without a server call)
NewAmount = WorkWithCurrencyRatesClientServer.ConvertByRate(Amount, SourceRate, NewRate);
```

**Notes / anti-patterns:**
- ❌ Direct query to `InformationRegisters.CurrencyRates` - bypasses the wrapper, breaks
  multiplicity and caching. Only `GetCurrencyRate`.
- `GetCurrencyRate` returns `Undefined` if there is no rate record -
  be sure to check before use.
- `ConvertToCurrency` obtains both rates itself; explicit `GetCurrencyRate` is needed
  only to show the rate to the user or validate it.
- `ConvertByRate` is convenient in client code: one server call for rates,
  then conversions on the client without repeated calls.

### 2. Amount in words and adding currencies by code

**Task:** format an amount in words in the required language; during initial
population, add currencies to the directory by the numeric OKV code.

**Functions:**
`WorkWithCurrencyRates.FormatAmountInWords(NumberedAmount, Currency, WithoutFractionalPart = False, By LangCode = Undefined, FractionalPartInWords = False) Export`
— Function -> `String`, region `ProgrammaticInterface` (stable). Server.
`WorkWithCurrencyRates.AddCurrenciesByCode(By CurrencyCodes) Export`
— Function -> `Array` of `CatalogRef.Currencies`, region `ProgrammaticInterface` (stable). Server. For initial population handlers.
`WorkWithCurrencyRates.ConnectPrintDataSourceNumberInWords(PrintDataSources) Export`
— Procedure, stable. Connects a data source for the print template of a number in words.

**Parameters:**
- `NumberedAmount` (`Number`), `Currency` (`CatalogRef.Currencies`).
- `WithoutFractionalPart` (`Boolean`) - `True` without kopecks.
- `LangCode` (`String`) - ISO 639-1 code (+ optional ISO 3166-1 via `_`): `"ru"`,
  `"ru_RU"`, `"en"`, `"en_US"`. Default is the configuration language.
- `FractionalPartInWords` (`Boolean`).
- `CurrencyCodes` (`Array` of `String`) - numeric codes (840, 978, 643 ...).

**Example:**
```bsl
Text = WorkWithCurrencyRates.FormatAmountInWords(1234.56, CurrencyRUB, , "ru");
// "One thousand two hundred thirty-four rubles 56 kopecks"

// Initial population of the currency directory
Codes = New Array; Codes.Add("840"); Codes.Add("978"); Codes.Add("643");
Refs = WorkWithCurrencyRates.AddCurrenciesByCode(Codes);
```

**Notes / anti-patterns:**
- ❌ Build the amount in words manually for a print form - use
  `FormatAmountInWords` or `ConnectPrintDataSourceNumberInWords`
  (data source for the template).
- If the currency classifier is missing, `AddCurrenciesByCode` creates items with
  the name `"Currency"`, and the symbolic code equals the numeric one.

### 3. Find a bank by BIK and explain an invalid bank

**Task:** by BIK (optionally with correspondent account) get bank details;
explain to the user why the bank is marked invalid.

**Functions:**
`WorkWithBanks.BIKInfo(By BIK, By CorrespondentAccount = Undefined, OnlyActual = True) Export`
— Function -> `ValueTable` (`Ref, BIK, CorrespondentAccount, Name, City, Address` ...), region `ProgrammaticInterface` (stable). Server.
`WorkWithBanks.ExplanationOfInvalidBank(Bank) Export`
— Function -> `FormattedString`, region `ProgrammaticInterface` (stable). Server.

**Parameters:**
- `BIK` (`String`), `CorrespondentAccount` (`String` / `Undefined`).
- `OnlyActual` (`Boolean`) - `True` (by default) only active banks.
- `Bank` (`CatalogRef.BankClassifier`).

**Example:**
```bsl
Table = WorkWithBanks.BIKInfo("044525225", , False);   // including inactive ones
If Table.Count() > 0 Then
    BankRef = Table[0].Ref;
    Name = Table[0].Name;
EndIf;

// Explanation for the bank form attribute
Explanation = WorkWithBanks.ExplanationOfInvalidBank(BankRef);
// FormattedString with a hyperlink to the new BIK, if one was found
```

**Notes / anti-patterns:**
- ❌ `WorkWithBanks.GetClassifierData(BIK = "", CorrespondentAccount = "", BankRecord = "")` -
  ⚠️ obsolete (region `DeprecatedProceduresAndFunctions`): uses the output
  parameter `BankRecord` instead of returning a value. In new code - `BIKInfo`.
- `ExplanationOfInvalidBank` returns a `FormattedString` for
  display in a form attribute, not a user message - for messages use
  `CommonPurpose.NotifyUser` (`base-common.md`).

### 4. Choose a BIK from a form

**Task:** from the BIK input field on an object form, open the selection form with a filter and
get the selected bank in the notification handler.

**Function:**
`WorkWithBanksClient.ChooseFromBIKDirectory(BIK, Form, NotificationHandler = Undefined) Export`
— Procedure, region `ProgrammaticInterface` (stable). Thin/Web client.

**Parameters:**
- `BIK` (`String`) - selection filter.
- `Form` (`ClientApplicationForm`) - source form.
- `NotificationHandler` (`NotificationDescription`) - `Result` = `CatalogRef.BankClassifier`
  (selected item) or `Undefined`; if absent - standard selection handler.

**Example:**
```bsl
&AtClient
Procedure BIKBeginChoice(Element, ChoiceData, StandardProcessing)
    StandardProcessing = False;
    WorkWithBanksClient.ChooseFromBIKDirectory(
        Object.BIK, ThisObject,
        New NotificationDescription("HandleBIKSelection", ThisObject));
EndProcedure

&AtClient
Procedure HandleBIKSelection(Result, AdditionalParameters) Export
    If Result <> Undefined Then
        Object.BIK = Result.BIK;                  // if Result is a reference, read attributes
        Object.BankName = Result.Name;
    EndIf;
EndProcedure
```

**Notes / anti-patterns:**
- ❌ Forgetting `StandardProcessing = False` - the standard directory choice will run
  instead of the BIK form.
- If there is only one record in the selection, the choice is made automatically (without showing the form).

### 5. Calculate a date by a production calendar / schedule

**Task:** planned date `DateFrom + N working days`; a chain of related dates; how many
working days are between two dates.

**Functions:**
`CalendarSchedules.DateByCalendar(By WorkSchedule, By DateFrom, By DaysCount, RaiseException = True) Export`
— Function -> `Date` / `Undefined`, region `ProgrammaticInterface` (stable). Server. `WorkSchedule` - `CatalogRef.ProductionCalendars` or `CatalogRef.Calendars`.
`CalendarSchedules.DatesByCalendar(By WorkSchedule, By DateFrom, By DaysArray, By CalculateNextDateFromPrevious = False, RaiseException = True) Export`
— Function -> `Array` of `Date`, stable.
`CalendarSchedules.DateDifferenceByCalendar(By WorkSchedule, By StartDate, By EndDate, RaiseException = True) Export`
— Function -> `Number`, stable.
`WorkSchedules.DateBySchedule(By WorkSchedule, By DateFrom, By DaysCount, RaiseException = True) Export`
— Function -> `Date` / `Undefined`, stable. Only `CatalogRef.Calendars`.

**Parameters:**
- `WorkSchedule` - calendar/schedule.
- `DateFrom` (`Date`), `DaysCount` (`Number`).
- `DaysArray` (`Array` of `Number`) - offsets for `DatesByCalendar`.
- `CalculateNextDateFromPrevious` (`Boolean`) - `True` = chained shift (each next from the previous one).
- `RaiseException` (`Boolean`) - `True` (default) throws an exception when the calendar is not filled; `False` -> `Undefined`.

**Example:**
```bsl
Calendar = CalendarSchedules.MainProductionCalendar();
If Calendar = Undefined Then
    PlannedDate = CurrentSessionDate() + 5 * 86400;   // fallback: calendar days
Else
    PlannedDate = CalendarSchedules.DateByCalendar(Calendar, CurrentSessionDate(), 5);
EndIf;

// Chain of dates: each one from the previous (approval stages)
DaysArray = New Array; DaysArray.Add(3); DaysArray.Add(5); DaysArray.Add(7);
DatesArray = CalendarSchedules.DatesByCalendar(Calendar, StartDate, DaysArray, True);

// How many working days between dates
DaysLate = CalendarSchedules.DateDifferenceByCalendar(Calendar, ShipmentDate, CurrentSessionDate());
```

**Notes / anti-patterns:**
- ❌ Calculating working days with a `While ... If DayOfWeek < 6` loop - does not account for
  holidays, weekend shifts, or non-working periods by decrees. Only
  `DateByCalendar` / `DateDifferenceByCalendar`.
- `DateByCalendar` accepts both types (calendar and schedule); `DateBySchedule`
  (module `WorkSchedules`) - only `CatalogRef.Calendars`. The result
  is the same - choose based on what you have.
- `DateDifferenceByCalendar` always returns a positive number (the sign
  is normalized internally).

### 6. Nearest working day with non-working periods taken into account

**Task:** for a set of dates, find the nearest working days (forward/backward), taking into
account special non-working periods (presidential decrees).

**Functions:**
`CalendarSchedules.GetNearestWorkingDatesParameters(ProductionCalendar = Undefined) Export`
— Function -> `Structure`, region `ProgrammaticInterface` (stable). Parameter constructor.
`CalendarSchedules.NearestWorkingDates(ProductionCalendar, StartDates, GetParameters = Undefined) Export`
— Function -> `Map` (`Key` - source `Date`, `Value` - nearest working `Date`), stable. Only `CatalogRef.ProductionCalendars`.
`WorkSchedules.NearestDatesIncludedInSchedule(WorkSchedule, StartDates, GetParameters = Undefined) Export`
— Function -> `Map`, stable. Only `CatalogRef.Calendars` - nearest date
  included in the schedule.

**Parameters:**
- `ProductionCalendar` (`CatalogRef.ProductionCalendars`).
- `StartDates` (`Array` of `Date`).
- `GetParameters` (`Structure` from `GetNearestWorkingDatesParameters`): `GetPreceding` (`Boolean` - backward), `TakeNonWorkingPeriodsIntoAccount` (`Boolean`), `NonWorkingPeriods`, `RaiseException`, `GetDatesIfCalendarIsNotFilled`.

**Example:**
```bsl
Parameters = CalendarSchedules.GetNearestWorkingDatesParameters(Calendar);
Parameters.GetPreceding = False;        // forward
Parameters.TakeNonWorkingPeriodsIntoAccount = True;   // take special non-working days into account

Dates = New Array; Dates.Add(DeliveryDate);
Map = CalendarSchedules.NearestWorkingDates(Calendar, Dates, Parameters);
NewDeliveryDate = Map[DeliveryDate];   // if the source date is working, returns it unchanged
```

**Notes / anti-patterns:**
- ❌ `NearestWorkingDates(WorkSchedule, ...)` with `CatalogRef.Calendars` -
  the method will throw an exception: it accepts **only** `ProductionCalendars`. For a
  work schedule - `WorkSchedules.NearestDatesIncludedInSchedule`.
- ⚠️ `CalendarSchedules.DatesOfNearestWorkingDays(Schedule, StartDates, GetPreceding = False, RaiseException = True, IgnoreScheduleUnfilled = False)`
  - obsolete (region `DeprecatedProceduresAndFunctions`), 5 parameters instead of 3. In
  new code - `NearestWorkingDates` / `NearestDatesIncludedInSchedule`.

### 7. Work schedule for a period and filling a calendar in a form

**Task:** get start/end work times by schedules for a period;
fill the `ProductionCalendar` attribute in a form with region (KPP) taken into account.

**Functions:**
`CalendarSchedules.MainProductionCalendar() Export`
— Function -> `CatalogRef.ProductionCalendars` / `Undefined`, stable. Server.
`CalendarSchedules.FillProductionCalendarInForm(Form, AttributePath, KPP = Undefined) Export`
— Procedure, stable. Server. Takes into account the functional option `UseMultipleProductionCalendars`.
`WorkSchedules.WorkSchedulesForPeriod(Schedules, StartDate, EndDate) Export`
— Function -> `ValueTable` (`WorkSchedule, ScheduleDate, StartTime, EndTime`), stable. Server. Requires the `WorkSchedules` subsystem to be connected.
`CalendarSchedules.WorkSchedulesForPeriod(Schedules, StartDate, EndDate) Export`
— Function, stable. Delegates to `WorkSchedules`; throws an exception if the subsystem is absent.

**Parameters:**
- `Form` (`ClientApplicationForm`), `AttributePath` (`String`, e.g. `"Object.ProductionCalendar"`).
- `KPP` (`String`) - for the regional calendar when the multiple calendars option is enabled.
- `Schedules` (`Array` of `CatalogRef.Calendars`), `StartDate` / `EndDate` (`Date`).

**Example:**
```bsl
// Fill the attribute in a form (server, OnCreateOnServer)
CalendarSchedules.FillProductionCalendarInForm(
    ThisForm, "Object.ProductionCalendar", Object.KPP);

// Warehouse work schedule for a week
Schedules = New Array; Schedules.Add(WarehouseSchedule);
ScheduleTable = WorkSchedules.WorkSchedulesForPeriod(
    Schedules, StartDate, EndDate);
// Columns: WorkSchedule, ScheduleDate, StartTime, EndTime
```

**Notes / anti-patterns:**
- ❌ `ThisForm.Object.ProductionCalendar = CalendarSchedules.MainProductionCalendar()`
  without considering KPP - when the multiple calendars option is enabled, for a separate
  subdivision the "main" one will be assigned, not the regional one. Use
  `FillProductionCalendarInForm` with KPP.
- ❌ Store a reference to the calendar in a constant and treat it as the "only" one - with
  the multiple calendars option, there may be no constant; `MainProductionCalendar`
  returns the "first one it finds."
- `CalendarSchedules` and `WorkSchedules` methods are server-side (Server, Thick client,
  External connection); from a thin client - through `&AtServer`. There is no separate
  `CalendarSchedulesClient` module.

## Additional

Other stable methods (region `ProgrammaticInterface`), full signatures - via
`python scripts/bsp_api.py method <Name> --module <Module> --src src/cf`:

- `WorkWithCurrencyRates.NumberFieldTypeDescription(By AllowedFieldSign = Undefined)` -
  description of a money field type (for constructing attributes).
- `WorkWithCurrencyRatesClientLocalization.ShowCurrencyRatesLoad(LoadParameters)` -
  interactive rate loading (stable variant; `LoadParameters.OpeningFromList`).
- `CalendarSchedules.NonWorkingDayPeriods(ProductionCalendar, SelectionPeriod)` -
  non-working day periods of a calendar.
- `CalendarSchedules.CreateTempTablesForWorkSchedulesForPeriod(TempTableManager, Schedules, StartDate, EndDate)` /
  `WorkSchedules.CreateTempTablesForWorkSchedulesForPeriod(...)` - schedule variant in
  a temporary table (for queries).
- `WorkSchedules.DatesBySchedule(By WorkSchedule, By DateFrom, By DaysArray, By CalculateNextDateFromPrevious = False, RaiseException = True)` -
  a chain of dates by schedule (analog of `DatesByCalendar`).

⚠️ Service methods (do not use in new code):
- `WorkWithCurrencyRatesService.LoadCurrencyRates()` - ⚠️ service
  (`ServiceProgrammaticInterface`). Manual rate loading; backward compatibility
  is not guaranteed. Start through the interactive form
  `WorkWithCurrencyRatesClientLocalization.ShowCurrencyRatesLoad`.
- `WorkWithBanks.GetClassifierData(...)` - ⚠️ obsolete, replaced by `BIKInfo`.
- `CalendarSchedules.DatesOfNearestWorkingDays(...)` - ⚠️ obsolete, replaced by
  `NearestWorkingDates` / `NearestDatesIncludedInSchedule`.
- `WorkSchedules.DateDifferenceByCalendar(...)` - ⚠️ service region; use
  `CalendarSchedules.DateDifferenceByCalendar`.

The override hook `CalendarSchedulesOverride.OnProductionCalendarsUpdate`
— application configuration reaction to the update of production calendars
(implemented in the module with the same name, not called directly).

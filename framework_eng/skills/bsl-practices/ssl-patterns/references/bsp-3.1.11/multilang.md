# BSP Multilingual Support

The **Multilingual Support** subsystem is the standard BSP mechanism for storing and
displaying string attribute values of application objects in multiple
languages. There are two ways to store translations: in the object **header**
using duplicate attributes with the `LanguageN` suffix (`Name`, `NameLanguage1`,
`NameLanguage2`) or in the `Representations` **tabular section** (`LanguageCode`
attribute + localized attributes). The approaches are mutually exclusive within
one object.

This covers only the `Multilingual Support` subsystem API. Translating UI strings
via `NStr("ru = '...'; en = '...'")` is a platform mechanism and is not part of
BSP. Printed forms in different languages are `ManagePrintingMultilingual*`
(printing skill). Machine translation is part of the "Text Translation"
subsystem and is not covered here.

## Modules

- `MultilingualSupportServer` - server API: form handlers, query transformation,
  language information.
- `MultilingualSupportClientServer` - shared code (client + server): suffix by
  number, object/reference presentation handlers.
- `MultilingualSupportClient` - client API: `OnOpen` form field handler that opens
  `CommonForm.TranslationInDifferentLanguages`.
- `MultilingualSupportOverride` - `OnDefineSettings(Settings)` hook
  (BSP calls it, application code implements it; not called directly).
- `MultilingualSupportCommon` - cached language information; in application code
  **do not call directly** - use only through the `MultilingualSupportServer` /
  `MultilingualSupportClientServer` wrappers.

The main language code is `GeneralPurpose.MainLanguageCode()` (stable,
`ProgrammaticInterface`; there is also `GeneralPurposeClient.MainLanguageCode`).
The suffix for the main language is an empty string: the attribute is called just
`Name` (without `Language0`). Additional languages are numbered in order of
activation: `Language1`, `Language2`, ... The additional language code (`"en"`,
`"de"`) is a BCP-47-compatible code from the `Languages.LanguageCode` metadata.

## Scenarios

### 1. Object form with multilingual attributes

**Task:** connect a "different languages input" button to the object form and
automatically display the value in the user's current language when reading,
and write the correct language column when saving.

**Functions:**
`MultilingualSupportServer.OnCreateAtServer(Form, Object = Undefined, ObjectName = Undefined) Export`
— connects the open button and handler to form fields with localized
attributes; for list forms, modifies the dynamic list query text.
`MultilingualSupportServer.OnReadAtServer(Form, CurrentObject, ObjectName = Undefined) Export`
— fills form attributes with values in the current language.
`MultilingualSupportServer.BeforeWriteAtServer(CurrentObject) Export`
— parses values in the current language into `LanguageN` attributes or the
  `Representations` tabular section rows.
— all Procedures, `#Region ProgrammaticInterface` (stable). Server.

**Parameters:**
- `Form` (ManagedForm) - object form.
- `Object` (Any) - optional primary form attribute.
- `ObjectName` (String) - name of the primary form attribute: `"Object"`
  (default for object forms), `"Record"` for registers, `"List"` for lists.
  Pass it explicitly if the name is nonstandard.
- `CurrentObject` (Any) - form object/record.

**Example:**
```bsl
&AtServer
Procedure OnCreateAtServer(Cancel, StandardProcessing)
    MultilingualSupportServer.OnCreateAtServer(ThisObject, Object, "Object");
EndProcedure

&AtServer
Procedure OnReadAtServer(CurrentObject)
    MultilingualSupportServer.OnReadAtServer(ThisObject, CurrentObject, "Object");
EndProcedure

&AtServer
Procedure BeforeWriteAtServer(Cancel, CurrentObject, WriteParameters)
    MultilingualSupportServer.BeforeWriteAtServer(CurrentObject);
EndProcedure
```

**Nuances / anti-patterns:**
- ❌ Do not call `MultilingualSupportServer.OnCreateAtServer(ThisObject)` in a
  list form - the list will show values in the main language for all users. The
  method modifies the dynamic list `QueryText`, and it must be called **first**
  in the handler, otherwise subsequent code may overwrite the text.
- Fallback to the main language is built into `OnReadAtServer`: if the value in
  the current language is empty, the main language is substituted. Do not
  implement fallback manually.
- `ObjectName = "Object"` is the standard name of the main attribute. For
  register record forms, pass `"Record"`; for list forms, pass `"List"`.

### 2. Manager module: reference presentation and input by string

**Task:** so that a reference to an item is displayed in the user's current
language, and input by string searches across all language representations of
the object.

**Functions:**
`MultilingualSupportClientServer.HandlePresentationRetrieval(Data, Presentation, StandardProcessing, AttributeName = "Name") Export`
— replaces the reference presentation with the value of the current-language
  attribute. Client + Server.
`MultilingualSupportClientServer.HandlePresentationFieldsRetrieval(Fields, StandardProcessing, AttributeName = "Name") Export`
— adds language attributes to the presentation fields. Client + Server.
`MultilingualSupportServer.HandleSelectionDataRetrieval(SelectionData, ByVal Parameters, StandardProcessing, MetadataObject) Export`
— replaces the standard input-by-string behavior: searches across all language
  representations. Server.
— all Procedures, `#Region ProgrammaticInterface` (stable).

**Parameters:**
- `Data` (Any) - object data from the manager module handler.
- `Presentation` (String) - **output** parameter: the generated presentation.
- `StandardProcessing` (Boolean) - **output** parameter; the method sets it to
  `False` when it replaces the presentation.
- `AttributeName` (String) - name of the localized attribute; default is
  `"Name"`. Pass it explicitly for nonstandard attributes
  (for example, `"PrintTitle"`).
- `Fields` (Array) - **output** parameter: fields used to form the presentation.
- `SelectionData` (SelectionData) - **output** parameter: selection data list.
- `MetadataObject` (Metadata) - object metadata (from `Metadata()`).

**Example:**
```bsl
// Manager module of a catalog
Procedure HandlePresentationRetrieval(Data, Presentation, StandardProcessing) Export
    MultilingualSupportClientServer.HandlePresentationRetrieval(
        Data, Presentation, StandardProcessing, "Name");
EndProcedure

Procedure HandlePresentationFieldsRetrieval(Fields, StandardProcessing) Export
    MultilingualSupportClientServer.HandlePresentationFieldsRetrieval(
        Fields, StandardProcessing, "Name");
EndProcedure

Procedure HandleSelectionDataRetrieval(SelectionData, Parameters, StandardProcessing) Export
    MultilingualSupportServer.HandleSelectionDataRetrieval(
        SelectionData, Parameters, StandardProcessing, Metadata());
EndProcedure
```

**Nuances / anti-patterns:**
- ❌ `HandlePresentationFieldsRetrieval(Fields, StandardProcessing)` without the
  third parameter - `AttributeName` defaults to `"Name"`. If the localized
  attribute has a different name (for example, `"Title"`), the method will
  substitute the wrong field unless you pass it explicitly. Specify
  `AttributeName` for nonstandard attributes.
- Without these three handlers, the reference to an item will be displayed only
  in the main language, and input by string will not find items by translations.
- Fallback to the main language is built into `HandlePresentationRetrieval`: if
  the current language is empty, the main one is returned; if it is empty too,
  standard processing is not disabled.

### 3. Dynamic list with language switching

**Task:** in a list form, the dynamic list must show values in the user's
current language; for a programmatically built query, append the `LanguageN`
suffix to the required field.

**Functions:**
`MultilingualSupportServer.OnCreateAtServer(Form, Object = Undefined, ObjectName = Undefined) Export`
— for a list form without an explicit `Object`, modifies the dynamic list
  `List` `QueryText`. Server.
`MultilingualSupportServer.ChangeQueryFieldForCurrentLanguage(QueryText, FieldName) Export`
— appends the `LanguageN` suffix to the specified field in the query text
  (supports `FieldName AS Alias`). Does nothing if the current language is the
  main one. Server.
— both Procedures, `#Region ProgrammaticInterface` (stable).

**Parameters:**
- `QueryText` (String) - **output** parameter: query text with the modified field.
- `FieldName` (String) - field name in the query text, for example
  `"CatalogItems.Name"`.

**Example:**
```bsl
&AtServer
Procedure OnCreateAtServer(Cancel, StandardProcessing)
    // Automatically modifies the QueryText of the dynamic list "List"
    MultilingualSupportServer.OnCreateAtServer(ThisObject);
EndProcedure

// Manual control (when the query text is built programmatically)
QueryText = "SELECT CatalogItems.Name AS Name, "
    + "CatalogItems.Article AS Article FROM Catalog.Items AS CatalogItems";
MultilingualSupportServer.ChangeQueryFieldForCurrentLanguage(QueryText, "CatalogItems.Name");
// On the first additional language, Name becomes NameLanguage1; on the main language, unchanged
```

**Nuances / anti-patterns:**
- ❌ Hardcoding `"Name" + "Language1"` in a query will break if the language
  order changes (the user enabled `en` first, but `de` comes first in the
  configuration). Always obtain the suffix programmatically through
  `CurrentLanguageSuffix()` or `LanguageSuffix(LanguageCode)`.
- `ChangeQueryFieldForCurrentLanguage` does nothing in the main language - the
  field remains `Name`. This is normal: the main language is stored in the base
  attribute without a suffix.

### 4. Initial population of predefined values

**Task:** during initial population of predefined items, fill the
`AttributeName_LanguageCode` columns from an `NStr`-formatted string for all
languages at once, with fallback to the main language.

**Function:**
`MultilingualSupportServer.FillMultilingualAttribute(Element, AttributeName, SourceString, LanguageCodes = Undefined) Export`
— Procedure, `#Region ProgrammaticInterface` (stable). Server.

**Parameters:**
- `Element` (ValueTableRow) - the row being filled, with
  `AttributeName_LanguageCode` columns.
- `AttributeName` (String) - attribute name, for example `"Name"`.
- `SourceString` (String) - string in `NStr` format, for example
  `"ru = 'Example'; en = 'Example'"`.
- `LanguageCodes` (Array) - language codes for which rows need to be filled;
  `Undefined` means all configuration languages.

**Example:**
```bsl
Procedure OnInitialPopulationOfItems(Items) Export
    LanguageCodes = New Array;
    LanguageCodes.Add("en");
    LanguageCodes.Add("de");

    For Each Element In Items Do
        MultilingualSupportServer.FillMultilingualAttribute(
            Element,
            "Name",
            "ru = 'Demo: example'; en = 'Demo: example'; de = 'Demo: Beispiel'",
            LanguageCodes);
    EndDo;
EndProcedure
```

**Nuances / anti-patterns:**
- ❌ Filling an object attribute directly via `NStr`:
  `Object.NameLanguage1 = NStr("en = 'Example'")` - `NStr` depends on the
  current **session** language, not the language in which the object is being
  edited; different users will see different contents. Use
  `FillMultilingualAttribute` with an explicit list of codes - it is independent
  of the session and fills all columns at once.
- The method itself ensures fallback to the main language for languages missing
  in `SourceString`: when `NStr(SourceString, LanguageCode)` is empty,
  `NStr(SourceString, MainLanguageCode())` is used.
- ❌ Storing translations in a single string attribute with a delimiter
  (`"Example||Example||Beispiel"`) breaks input by string, indexing, and search.
  Use `LanguageN` attributes (header) or the `Representations` tabular section.

### 5. Get the suffix and code of the current language

**Task:** programmatically obtain the current language suffix (`Language1` / `""`)
or the suffix by language code for building an attribute name.

**Functions:**
`MultilingualSupportServer.CurrentLanguageSuffix() Export`
— suffix of the current session language (`""` for the main one, `"Language1"`,
  `"Language2"`). Server.
`MultilingualSupportServer.LanguageSuffix(LanguageCode) Export`
— suffix by language code (`"en"` -> `"Language1"`); `""` if the language is
  not used.
`MultilingualSupportClientServer.LanguageSuffix(LanguageOrdinal = Undefined) Export`
— suffix by ordinal number (`1` -> `"Language1"`); without a parameter, the
  base string `"Language"` is returned. Client + Server.
`MultilingualSupportServer.IsAdditionalLanguageUsed(LanguageOrdinal) Export`
— `Boolean`: whether an additional language with the given number is enabled.
  Server.
`MultilingualSupportServer.CountAdditionalLanguages() Export`
— number of enabled additional languages. Server.
`MultilingualSupportServer.LanguagesInfo() Export`
— structure containing information about the configuration languages. Server.
— all Functions, `#Region ProgrammaticInterface` (stable).

**Parameters:**
- `LanguageCode` (String) - BCP-47-compatible lowercase code (`"en"`).
- `LanguageOrdinal` (Number) - additional language number (`1`, `2`, ...).

**Example:**
```bsl
// Current language suffix
AttributeName = "Name" + MultilingualSupportServer.CurrentLanguageSuffix();
// In the main language -> "Name"; in the first additional language -> "NameLanguage1"

// Suffix by language code
Suffix = MultilingualSupportServer.LanguageSuffix("en");  // "Language1" or ""
If EmptyString(Suffix) Then
    // "en" is not enabled in the configuration - use the main attribute
EndIf;

// Suffix by number (client + server)
Suffix = MultilingualSupportClientServer.LanguageSuffix(1);  // "Language1"

// Check whether the second additional language is enabled
If MultilingualSupportServer.IsAdditionalLanguageUsed(2) Then
    // ...
EndIf;
```

**Nuances / anti-patterns:**
- ❌ Hardcoding `"Language1"` in application code will break if the language
  order changes. Always obtain the suffix programmatically through
  `CurrentLanguageSuffix()` or `LanguageSuffix(LanguageCode)`.
- ❌ Calling `MultilingualSupportCommon.CountAdditionalLanguages()` directly -
  `Common` is for internal BSP use. The wrapper
  `MultilingualSupportServer.CountAdditionalLanguages()` reads from `Common`
  and will not break if the internal contract changes.
- Normalize the language code input to lowercase (`Lower(LanguageCode)`) and do
  not rely on a regional suffix: BSP does not distinguish `"en-US"` and `"en"` -
  both map to the same suffix.

### 6. Read multilingual object attributes programmatically

**Task:** when reading an object programmatically outside a form (in a fill
handler, background job), fill multilingual attributes in the current language
as the form does.

**Function:**
`MultilingualSupportServer.ReadRepresentationsAtServer(Object) Export`
— Procedure, `#Region ProgrammaticInterface` (stable). Server.

**Parameters:**
- `Object` (CatalogObject / DocumentObject) - already obtained object
  (via `GetObject()` or a query with `Allowed`).

**Example:**
```bsl
// In a fill handler/background job
Object = Link.GetObject();
MultilingualSupportServer.ReadRepresentationsAtServer(Object);
// Object.Name now contains the value in the current session language
// (with fallback to the main language if the translation is empty)
```

**Nuances / anti-patterns:**
- Unlike `OnReadAtServer(Form, ...)` (for forms), this method takes an already
  obtained `*Object`, without form context. Use it for programmatic reads
  outside the UI.
- Before writing an object that was modified programmatically, call
  `MultilingualSupportServer.BeforeWriteAtServer(CurrentObject)` - it will parse
  the value in the current language into the required language columns.

### 7. Extending subsystem settings (override hook)

**Task:** the application configuration adjusts multilingual behavior
(for example, disables different-language input for specific roles).

**Hook:**
`MultilingualSupportOverride.OnDefineSettings(Settings) Export`
— Procedure, `#Region ProgrammaticInterface` (override hook). BSP calls this
  method; application code **implements** it in the identically named module of
  the application configuration. Not called directly from application code.

**Parameters:**
- `Settings` (Structure) - subsystem settings; filled/modified in the hook.

**Example:**
```bsl
// MultilingualSupportOverride (application configuration module)
Procedure OnDefineSettings(Settings) Export
    // For example, disable different-language input for the "ReadOnly" role
    If Users.RolesAvailable("ReadOnly") Then
        Settings.DifferentLanguageInputAvailable = False;
    EndIf;
EndProcedure
```

**Nuances / anti-patterns:**
- ❌ Implementing the hook in the main `MultilingualSupportServer` module is
  not allowed. The hook must be in the application configuration's
  `MultilingualSupportOverride` module (the override module is copied, and its
  body is overridden).
- ❌ Calling `MultilingualSupportOverride.OnDefineSettings(...)` from application
  code - this is a hook, and BSP calls it at the appropriate moment.

## Additional

Other stable methods (`ProgrammaticInterface` region), full signatures - via
`python scripts/bsp_api.py method <Name> --module <Module> --src src/cf`:

- `MultilingualSupportServer.AttributeNamesWithLanguageCode(AccountingAttributeNames, LanguageCode = "")`
  - array of attribute names with the language suffix appended.
- `MultilingualSupportServer.LanguagesInfo()` - structure with configuration
  language information (codes, ordinal numbers, main language).
- `MultilingualSupportClient.OnOpen(Form, Object, Element, StandardProcessing)`
  - client `OnOpen` handler for a form field that opens
  `CommonForm.TranslationInDifferentLanguages` (called from the attached form
  handler, not directly).
- `GeneralPurpose.MainLanguageCode()` / `GeneralPurposeClient.MainLanguageCode()`
  - the infobase main language code (for example, `"ru"`), stable. ⚠️ The
  service wrapper `MultilingualSupportServer.MainLanguageCode()` also exists,
  but it is in `ServiceProgrammaticInterface` - prefer the stable
  `GeneralPurpose.MainLanguageCode()`.
- `MultilingualSupportClient.OpenRegionalSettingsForm(NotificationDescription = Undefined, Parameters = Undefined)`
  - ⚠️ `ServiceProgrammaticInterface`: opens the regional settings form
    (administrative scenario, not for application use).

To find the signature/region of any of these methods -
`python scripts/bsp_api.py method <Name> --src src/cf`.

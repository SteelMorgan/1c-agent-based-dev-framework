# Headless launch of external processing

Launching an external processing in batch mode is done through:

```bash
v8-runner launch <thin|thick|ordinary> --execute "<path to .epf>" --c "<sentinel>" --raw-key /DisableUnsafeActionProtection
```

The key platform nuance: `/Execute<epf>` opens the processing form, but does not call the export method of the object module. Processing without a form via `/Execute` will not execute the business logic.

The canonical approach:

1. The processing has a managed form.
2. In `ПриОткрытии`, the form recognizes batch mode by `ПараметрЗапуска()`.
3. The form calls a server method that performs the work.
4. The server method writes an external log or another observable result.
5. The form ends the session through `ЗавершитьРаботуСистемы(Ложь)`.

`--raw-key /DisableUnsafeActionProtection` is needed so that the first opening of the external processing does not hang on the security warning.

Verification: wait for the 1С process to exit or for the log file to appear, then check behavior: data delta, log, registration log. An exit code without a behavioral result does not prove that the logic ran.

An alternative without `/Execute`: from an already connected server session, create the processing via `ВнешниеОбработки.Создать(<path>, Ложь)` and call the export method. This requires a code execution channel on the server; `/Execute` is self-sufficient from the CLI.

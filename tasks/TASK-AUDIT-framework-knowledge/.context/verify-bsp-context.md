# Verify BSP questions — final checkpoint

Ground truth: /workspaces/work/repos/1C Projects/GBIG PAM/src/xml/CommonModules/ (BSP 3.1.9.357)
Bank: tasks/TASK-AUDIT-framework-knowledge/questions/questions-bsp.jsonl (132 q), read-only, not modified.

## Result: DONE (grep fact-check pass complete; report delivered to caller)

~75 questions grep-verified directly against CommonModules sources (module existence, deprecated
region/comment, parameter signatures, positional/param-name checks) — all OK, matched reference_answer.

3 discrepancies found (all version-related, 3.1.9 vs 3.1.11 reference doc, not outright hallucinations):
- Q-BSP-083 (currencies-banks.md): РаботаСБанками.ПолучитьДанныеКлассификатора in 3.1.9 is NOT marked
  deprecated (no "Устарела" comment, sits in plain #Область ПрограммныйИнтерфейс, no
  УстаревшиеПроцедурыИФункции subregion in this module at all). Reference claims deprecated status.
  Verdict: NEEDS-REVIEW / possible НЕТ_В_3.1.9 (deprecation likely added later).
- Q-BSP-097 (esign-mcd.md): ЭлектроннаяПодпись.Зашифровать(Данные, Сертификат) in 3.1.9 has only 2
  params — no third АлгоритмШифрования param claimed by reference_answer. Служебный
  ЭлектроннаяПодписьСлужебный.Зашифровать(Данные, Сертификат, МенеджерКриптографии) confirmed correct.
  Verdict: НЕТ_В_3.1.9 (public Зашифровать signature evolved, param added after 3.1.9).
- Q-BSP-066 (data-exchange.md): ОбменДаннымиПовтИсп — confirmed its #Область ПрограммныйИнтерфейс
  contains ONLY 2 deprecated methods, but module also has a large #Область СлужебныйПрограммныйИнтерфейс
  with active (non-deprecated) internal methods. Claim "модуль содержит только устаревшие методы" is an
  overstatement if read as "whole module"; true if read as "its public/stable API". Minor, borderline OK.

Remaining ~54 questions are runtime/behavioral/UI semantics not practically grep-checkable in the time
budget (marked SEMANTIC in the report) — spot-checked a subset via code reading (e.g. Q-BSP-102 Дата(1,1,1)
default, Q-BSP-105 deprecated comment, Q-BSP-090 ВключитьБизнесЛогику default) which confirmed correct.

No further action needed — final answer already delivered as chat report.

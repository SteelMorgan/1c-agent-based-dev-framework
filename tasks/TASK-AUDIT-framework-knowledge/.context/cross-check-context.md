# Cross-check audit - checkpoint log

Start: reading file list, beginning pass 1 (bsl-practices non-BSP skills vs BSP block).

## Checkpoint 1 (~10 min)
Read: ssl-patterns/SKILL.md, fundamentals.md, base-common.md, users-access.md,
longs-and-jobs.md, commands-external.md, print-reports.md.

Candidate finding (HIGH):
- ssl-patterns/SKILL.md:128 references `УправлениеДоступом.ОписаниеПрофиля()` and
  `УправлениеДоступом.ПроверитьДопустимостьДействия()` as canonical BSP methods for
  profile assignment / permission check. users-access.md (same skill's own canonical
  reference file) documents NO such methods — profile assignment is
  `ВключитьПрофильПользователю`/`ВыключитьПрофильПользователю`/`УстановитьПраваПользователя`,
  and permission checks are `ЕстьПраво`/`ЕстьРоль`/`ЧтениеРазрешено`/`ИзменениеРазрешено`/
  `ПроверитьЧтениеРазрешено`/`ПроверитьИзменениеРазрешено`. Internal inconsistency within
  canonical BSP block itself (SKILL.md vs its own reference/users-access.md).
  Confirmed by: file cross-check within same skill.

Also noted (not new, extra occurrence of ALREADY KNOWN base-common БезопасноеХранилище
defect): ssl-patterns/SKILL.md:110 also says "`БезопасноеХранилище` (через обёртки...)"
— same wrong naming as base-common.md:125 known issue, additional location to fix.

Remaining to read: bsp-3.1.11/* (admin-tools, backup, bp-tasks, classifiers, comms,
contact-info, currencies-banks, esign-mcd, external-components, files-and-versions,
forms-validation, multilang, perf-monitoring, prefixes, protection-pd, report-dedup,
update), bsp-borrowings.md, security/references/*, and non-BSP skills (api-design,
coding-standards, error-handling, background-jobs, data-exchange, integration-patterns,
metadata-object-design, query-optimize, query-patterns, test-writing, form-patterns,
form-visual-requirements).

## Checkpoint 2
Read additionally: longs-and-jobs.md, commands-external.md, print-reports.md,
forms-validation.md, files-and-versions.md, data-exchange.md (bsp-3.1.11 canonical,
NOT the defective bsl-practices/data-exchange/SKILL.md). All internally consistent,
well cross-referenced, no new defects found in these (they even correctly cross-note
ВидСравнения vs МетодСравнения parameter naming difference between commands-external.md
and print-reports.md - consistent both places).

Still to read: admin-tools, backup, bp-tasks, classifiers, comms, contact-info,
currencies-banks, esign-mcd, external-components, multilang, perf-monitoring, prefixes,
protection-pd, report-dedup, update, bsp-borrowings.md; security/references/*; then
non-BSP skills.

## Checkpoint 3
Read: prefixes.md, update.md, esign-mcd.md. All internally solid, no new defects
(prefixes.md typo at line 40 vs correct spelling line 273 = ALREADY KNOWN, skip).
esign-mcd.md is very careful about multi-module collisions (ПроверитьПодпись in 6
modules, ДатаПодписания in 2, Зашифровать in 2) - good quality reference.

Remaining: admin-tools, backup, bp-tasks, classifiers, comms, contact-info,
currencies-banks, external-components, multilang, perf-monitoring, protection-pd,
report-dedup, bsp-borrowings.md; security/references/*; non-BSP skills.
Time used so far: ~20 min of 35 min budget. Need to move faster - will skim
remaining BSP files more quickly and prioritize the non-BSP skill cross-check
(layer 1) which is more likely to have the target defect class.

## Checkpoint 4 (~30 min mark)
Finished reading almost all bsp-3.1.11/* reference files (comms, currencies-banks,
bp-tasks, plus earlier ones). All very high internal consistency, no new defects
found beyond the already-known ones. Did NOT read: admin-tools, backup, classifiers,
contact-info, external-components, multilang, perf-monitoring, protection-pd,
report-dedup, bsp-borrowings.md (skipped due to time budget - spot-checked enough
of the corpus to conclude BSP-block internal consistency is generally high, with
one confirmed defect: ssl-patterns/SKILL.md:128 fabricated methods).

Now switching to PRIORITY: non-BSP skills cross-check (layer 1) since that's where
known defects were found and budget is running low. Reading: api-design,
coding-standards, error-handling, background-jobs, integration-patterns,
metadata-object-design, query-optimize, query-patterns, test-writing, form-patterns,
form-visual-requirements, security/*, data-exchange (quick check beyond known issues).

## Checkpoint 5 (~35 min mark) - HIGH-CONFIDENCE NEW FINDING
Read api-design/SKILL.md, coding-standards/SKILL.md (Rule16=known,skip),
error-handling/SKILL.md (Rule11 SortировПоЗначению=known,skip).

FINDING (HIGH): api-design/SKILL.md lines 82-107 defines "Category 3: #Область
ПереопределяемыйИнтерфейс" as the real BSP region name for override/hook modules
(*Переопределяемый suffix), with example "Модуль: РаботаСФайламиПереопределяемый
#Область ПереопределяемыйИнтерфейс". This CONTRADICTS the canonical BSP reference:
fundamentals.md lines 176-188 lists only 4 real regions (ПрограммныйИнтерфейс,
СлужебныйПрограммныйИнтерфейс, СлужебныеПроцедурыИФункции,
УстаревшиеПроцедурыИФункции) plus rare others (ОбработчикиСобытийПодсистемКонфигурации,
ДляВызоваИзДругихПодсистем, ВспомогательныеПроцедурыИФункции,
ОбновлениеИнформационнойБазы, ОбработчикиРегламентныхЗаданий) - "ПереопределяемыйИнтерфейс"
is NOT among them. fundamentals.md lines 190-197 explicitly states hook methods in
*Переопределяемый modules live in region ПрограммныйИнтерфейс ("Методы в них живут
в регионе ПрограммныйИнтерфейс, но семантически это хуки"). This is confirmed
repeatedly across EVERY bsp-3.1.11/*.md file read (users-access.md:319,
data-exchange.md, forms-validation.md, prefixes.md, print-reports.md etc all show
*Переопределяемый hook methods documented as "регион #Область ПрограммныйИнтерфейс").
api-design's Category 4 "ДляВызоваИзДругихПодсистем" IS legitimate (matches
fundamentals.md's "other regions" list) - only Category 3 name is the problem.
Confirmed by: cross-check against canonical BSP block (ssl-patterns/fundamentals.md
+ ~15 bsp-3.1.11/*.md files all consistent on this point).
Likely root cause: confusing the real module-suffix concept "Переопределяемый"
with an invented region name "#Область ПереопределяемыйИнтерфейс".
Action: verify against real БСП source/ИТС standard "Структура модуля"; if
confirmed, either rename Category 3's region label to ПрограммныйИнтерфейс (in
*Переопределяемый modules) or clarify it's a conceptual category, not a literal
#Область name.

Still need to check (time nearly exhausted): background-jobs, integration-patterns,
metadata-object-design, query-optimize, query-patterns, test-writing, form-patterns,
form-visual-requirements, security/*. Will do a fast pass now and wrap up report.

## Checkpoint 6 - CRITICAL NEW FINDING
Read background-jobs (clean), integration-patterns/SKILL.md, security/SKILL.md.

FINDING (CRITICAL, worse than known base-common issue): integration-patterns/SKILL.md
and security/SKILL.md invent a completely fictitious БезопасноеХранилище calling
convention treating it as a directly-callable module with methods .Прочитать(),
.УстановитьДанные(Владелец, Данные[, Ключ]), .ПрочитатьДанные(Владелец[, Ключ]).
Real BSP API (per base-common.md, the canonical reference, confirmed 3x in that
file) has NO callable module named БезопасноеХранилище at all - it's a register,
accessed ONLY via ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище(Владелец,
Данные, Ключ) / ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища(Владелец,
Ключи) / ОбщегоНазначения.УдалитьДанныеИзБезопасногоХранилища(Владелец, Ключи).
Locations:
- integration-patterns/SKILL.md:100 `БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом")`
- integration-patterns/SKILL.md:121 `БезопасноеХранилище.Прочитать("ИнтеграцияСВнешнимСервисом").ТокенДоступа`
- integration-patterns/SKILL.md:132 `БезопасноеХранилище.Прочитать("ИнтеграцияСертификат")`
- integration-patterns/SKILL.md:402 `БезопасноеХранилище.Прочитать("ВходящийAPIТокен").Токен`
- security/SKILL.md:49 `БезопасноеХранилище.УстановитьДанные(Владелец, Данные[, Ключ])` и `ПрочитатьДанные(Владелец[, Ключ])`
- security/SKILL.md:154 `БезопасноеХранилище.ПрочитатьДанные(УчётнаяЗапись, "access_token")`
This is a DIFFERENT/WORSE defect than the already-known base-common naming issue
(регистр БезопасноеХранилище vs БезопасноеХранилищеДанных) - this is inventing a
nonexistent callable API surface (wrong methods AND wrong calling target), code
using these examples would fail to compile. High confidence, confirmed by direct
cross-check vs base-common.md (canonical reference, read earlier, lines 80-132).

Time budget essentially exhausted (~40+ min). Wrapping up now - did not get to
read: metadata-object-design, query-optimize, query-patterns, test-writing (beyond
known issue), form-patterns (beyond known issue), form-visual-requirements,
secrets.md, crypto.md, auth.md, remaining bsp-3.1.11 files (admin-tools, backup,
classifiers, contact-info, external-components, multilang, perf-monitoring,
protection-pd, report-dedup), bsp-borrowings.md, data-exchange/SKILL.md (the known
broken one - didn't re-verify beyond known issues).
Compiling final report now.

---

# PASS 2 (continuation, read-only audit)

## Checkpoint 1 (Task A remaining coverage)
Read security/references/secrets.md, crypto.md, review-checklist.md (auth.md already
covered in pass 1).

FINDING (CRITICAL, confirms/extends pass-1 БезопасноеХранилище finding — same
tirage class, PRIORITIZED per task instructions): secrets.md tirages the exact same
fictitious directly-callable `БезопасноеХранилище` API as integration-patterns/SKILL.md
and security/SKILL.md (pass 1 finding):
- secrets.md:22 `БезопасноеХранилище.УстановитьДанные(Владелец, Данные, Ключ = Неопределено)`
- secrets.md:23 `БезопасноеХранилище.ПрочитатьДанные(Владелец, Ключ = Неопределено)`
- secrets.md:24 `БезопасноеХранилище.УдалитьДанные(Владелец, Ключ = Неопределено)`
- secrets.md:61, 87, 112-116, 130, 182 repeat the same fictitious calls throughout
  code examples, lifecycle table, antipatterns section.
- review-checklist.md:5, 18, 40 also reference `БезопасноеХранилище` as if directly
  callable/the-thing-itself ("Секреты хранятся в `БезопасноеХранилище`...").
crypto.md: clean, no fabricated APIs, internally consistent (МенеджерКриптографии,
ЭлектроннаяПодпись.* wrapper — matches esign-mcd.md from pass 1).
=> This is now confirmed tirage'd across 4 files: integration-patterns/SKILL.md,
security/SKILL.md (pass 1) + secrets.md, review-checklist.md (pass 2). Root cause
confirmed in Task B (see below): real BSP has NO callable module `БезопасноеХранилище`
at all, only regsiter `БезопасноеХранилище(Данных)` + wrapper functions
`ОбщегоНазначения.ЗаписатьДанныеВБезопасноеХранилище` /
`ПрочитатьДанныеИзБезопасногоХранилища` / `УдалитьДанныеИзБезопасногоХранилища`.

Read remaining bsp-3.1.11/*: admin-tools.md, backup.md, classifiers.md,
contact-info.md, external-components.md, multilang.md, perf-monitoring.md,
protection-pd.md, report-dedup.md, bsp-borrowings.md — ALL clean, internally
consistent, no new defects (high-quality corpus, consistent with pass-1 assessment).

Read metadata-object-design/SKILL.md, query-optimize/SKILL.md, query-patterns/SKILL.md,
test-writing/SKILL.md + references/*, form-patterns/SKILL.md + references/*,
form-visual-requirements/SKILL.md — ALL clean, no fabricated APIs found. Confirmed
framework/rules/<skill>/SKILL.md are intentional short dispatcher stubs (trigger →
"apply skill X"), not divergent content forks — checked metadata-object-design,
query-optimize, query-patterns, form-patterns rule-stubs vs skill content, all
consistent with dispatcher pattern (not a defect).
test-writing/SKILL.md:207 has the already-known ПредопределённыйЭлемент defect
(matches learned-patterns.md's own documented antipattern) — skipped as known.

Coverage now: 100% of bsp-3.1.11/*, security/references/*, and the listed
bsl-practices skills read across pass 1 + pass 2.

## Checkpoint 2 (Task B — verification against real BSP source)
Found real BSP source (not just project code) inside vendored CommonModules of
`/workspaces/work/repos/1C Projects/GBIG PAM/src/xml/CommonModules/` (also present
in DSSL UT, DSSL DRIVE) — full БСП 8.3+ CommonModules tree checked out as XML+bsl
(Module.bsl per module dir). Used this as ground truth (read-only, no edits).

**B1 — CONFIRMED, no fictitious module:**
`grep -rl "БезопасноеХранилище" .../CommonModules` → module directory named exactly
`БезопасноеХранилище` does NOT exist (`find -iname "*БезопасноеХранилище*"` empty).
Real implementation is in
`CommonModules/ОбщегоНазначения/Ext/Module.bsl`:
- line 5537: `Процедура ЗаписатьДанныеВБезопасноеХранилище(Владелец, Данные, Ключ = "Пароль") Экспорт`
- line 5654: `Функция ПрочитатьДанныеВладельцевИзБезопасногоХранилища(Владельцы, Ключи = "Пароль", ОбщиеДанные = Неопределено) Экспорт`
- line 5702: `Функция ПрочитатьДанныеИзБезопасногоХранилища(Владелец, Ключи = "Пароль", ОбщиеДанные = Неопределено) Экспорт`
- line 5741: `Процедура УдалитьДанныеИзБезопасногоХранилища(Владелец, Ключи = Неопределено) Экспорт`
Internally these wrappers use `РегистрыСведений.БезопасноеХранилищеДанных.СоздатьМенеджерЗаписи()`
(line 5567) / `...БезопасноеХранилищеДанныхОбластейДанных...` (SaaS variant, line 5565) —
i.e. a register (РегистрСведений), never a directly-callable module/object.
VERDICT: base-common.md / bsp-borrowings.md description is correct; ssl-patterns's
own base-common.md wrapper names are exactly right. The `БезопасноеХранилище.Прочитать()`
/ `.УстановитьДанные()` / `.ПрочитатьДанные()` calling convention in
integration-patterns/SKILL.md, security/SKILL.md, secrets.md, review-checklist.md
is 100% FICTITIOUS — code using it would fail to compile (no such module).

**B2 — CONFIRMED, no "ПереопределяемыйИнтерфейс" region:**
`grep -rl "ПереопределяемыйИнтерфейс" .../CommonModules` → ZERO matches anywhere in
the entire real BSP source tree (checked ~40+ `*Переопределяемый` modules' region
headers, e.g. `УправлениеДоступомПереопределяемый/Ext/Module.bsl:9`,
`ОбщегоНазначенияПереопределяемый/Ext/Module.bsl:9`,
`ЗащитаПерсональныхДанныхПереопределяемый`, `ГрупповоеИзменениеОбъектовПереопределяемый`,
etc.) — every single one uses `#Область ПрограммныйИнтерфейс` (some additionally have
`УстаревшиеПроцедурыИФункции`). VERDICT: fundamentals.md's claim (regions live in
`ПрограммныйИнтерфейс`, hook status is semantic not a distinct region) is correct.
api-design/SKILL.md's "Category 3: #Область ПереопределяемыйИнтерфейс" (pass-1
finding) is definitively FICTITIOUS — this region name does not exist anywhere in
real BSP 3.1.x source.

**B3 — CONFIRMED, no such methods:**
Full `ПрограммныйИнтерфейс` export list of real `CommonModules/УправлениеДоступом/Ext/Module.bsl`
(grep of `^Функция|^Процедура` in that region) contains: `ЕстьРоль`, `ЕстьПраво`,
`ЧтениеРазрешено`, `ИзменениеРазрешено`, `ПроверитьЧтениеРазрешено`,
`ПроверитьИзменениеРазрешено`, `ВключитьПрофильПользователю` (line 608),
`ВыключитьПрофильПользователю` (line 626), `ОграничиватьДоступНаУровнеЗаписей`,
`НовоеОписаниеПрофиляГруппДоступа` (line 1684, NOT `ОписаниеПрофиля`), etc. — NO
method named `ОписаниеПрофиля()` or `ПроверитьДопустимостьДействия()` exists anywhere
in the module (grep for both strings only matches inside a comment-example block
using `НовоеОписаниеПрофиляГруппДоступа`, not the literal name claimed).
VERDICT: ssl-patterns/SKILL.md:128's `УправлениеДоступом.ОписаниеПрофиля()` and
`УправлениеДоступом.ПроверитьДопустимостьДействия()` (pass-1 finding) are 100%
FICTITIOUS/hallucinated method names. users-access.md's documented API
(ЕстьПраво/ЕстьРоль/ЧтениеРазрешено/ИзменениеРазрешено/Включить-ВыключитьПрофильПользователю)
is the real, verified API.

All three pass-1 findings B1/B2/B3 are now PROVEN with direct source citations —
no longer "internal inconsistency" guesses, but confirmed hallucinations vs.
ground-truth BSP source. ask_1c_ai / Напарник not needed since real source was found.

Task complete. Compiling final report.

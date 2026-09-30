---
name: metadata-object-design
description: "Use when creating or reviewing 1C metadata objects: catalogs, documents, registers, reports, roles, subsystems, scheduled jobs"
alwaysApply: false
metadata:
  borrowed_from: "https://github.com/yellow-hammer/dev-rules"
  borrowed_commit: "fa48a57"
  borrowed_at: "2026-06-30"
  borrowed_table: "docs/specs-and-analisys/yellow-hammer-dev-rules-analysis.md"
---

# 1C Metadata Object Design

This skill complements XML specifications and BSP references. XML specs describe the file format; this checklist describes application-level invariants that must not be missed when creating or reviewing objects in a real 1C configuration.

Before creating an object, inspect current metadata via LSP/`get_metadata_structure` or the source dump. If the object belongs to a vendor configuration, do not modify the vendor object without an explicit decision: isolate new logic in your own modules, subsystems, and extensions.

## General Rules

- [ ] Name, synonym, and presentations follow 1C standards and the project prefix.
- [ ] The new object is placed into a service subsystem; user-facing placement is configured only when required by the technical task/design.
- [ ] Access roles and subsystem roles are defined for the new object if it is exposed in the UI.
- [ ] If the object participates in RLS or has `Организация` as an attribute/dimension, access-control hooks are added.
- [ ] If the object must be covered by data-change prohibition dates, BSP `ДатыЗапретаИзменения` mechanisms are connected.
- [ ] New objects in vendor configurations have a comment with the reason for adding them when the project standard requires it.

## Catalogs

- [ ] `ПредставлениеОбъекта` is singular; `ПредставлениеСписка` is plural when it differs from the synonym.
- [ ] If `Наименование` length is not specified, use the project default `150`.
- [ ] If `Код` has no business meaning, set code length to `0`; otherwise length and format must follow business semantics.
- [ ] Mandatory attributes use `ПроверкаЗаполнения`; do not replace platform fill checking with manual code without a reason.
- [ ] `СвязиПараметровВыбора` and `ПараметрыВыбора` are filled when the value depends on other attributes or is limited by a filter.
- [ ] Prefixing is enabled only for distributed/integration scenarios or when explicitly required by the project.
- [ ] If versioning or BSP attached commands are enabled, use standard BSP hooks instead of custom infrastructure.

## Documents

- [ ] Document name is singular; synonym is mandatory.
- [ ] `ПредставлениеОбъекта` is singular; `ПредставлениеСписка` is plural when it differs from the synonym.
- [ ] Command-interface text is short: preferably up to 30 characters, roughly 38 maximum.
- [ ] `ОперативноеПроведение` is disabled by default unless explicitly required.
- [ ] If the configuration uses an accounting/posting mechanism, the new document follows it. Even a document without movements gets a posting skeleton when this is the project convention.
- [ ] Movement deletion and reposting use the accepted configuration mechanism, not ad hoc manual register cleanup.
- [ ] Prohibition dates, roles, subsystems, and RLS hooks are connected when applicable.

## Registers

- [ ] Mandatory dimensions have `ЗапретНезаполненныхЗначений`; do not enable it where an empty value is valid by the model.
- [ ] Dimension order reflects usage in filters and joins: the most selective/frequent dimensions come first.
- [ ] Registers subordinated to a recorder usually receive only a read role when writes happen through the recorder.
- [ ] Registers with `Организация` or another access key have RLS hooks: restricted object list, `ПриЗаполненииОграниченияДоступа`, defined types for access-key owners.
- [ ] Record sets have data-change prohibition checks when the register participates in closed periods.

## Reports

- [ ] The report is connected to BSP `ВариантыОтчетов` when the configuration uses this subsystem.
- [ ] The report has `ХранилищеВариантов` configured.
- [ ] Standard report commands are disabled when the report is opened through `ВариантыОтчетов`.
- [ ] Do not leave a report variant named `Основной`; the variant name should be business-readable.
- [ ] Database update/version handlers account for the new report so it appears for users after delivery.
- [ ] Role `ПросмотрОтчета<ReportName>` is created.
- [ ] Report queries account for RLS (`РАЗРЕШЕННЫЕ` and project access restrictions).
- [ ] Do not use an external report/data processor to bypass the release cycle when the report should be part of delivery.

## Roles

- [ ] New objects have separate access roles; top-level subsystems have `Подсистема<SubsystemName>` roles.
- [ ] `ИнтерактивноеУдаление` is removed from `ПолныеПрава` for new objects when the project standard forbids interactive deletion.
- [ ] Application roles do not grant client connection rights unless the role is meant for signing in.
- [ ] Role names follow templates: `Чтение<FeatureName>`, `ДобавлениеИзменение<FeatureName>`, `ПросмотрОтчета<ReportName>`, `ИспользованиеОбработки<DataProcessorName>`, `Подсистема<SubsystemName>`.
- [ ] Do not use role editor commands "set all" / "clear all" as a way to configure a new role.
- [ ] Prefer batch role checks (`Пользователи.РолиДоступны(...)`) over scattered point checks when the project infrastructure supports it.

## Subsystems

- [ ] All new objects are placed in a service subsystem.
- [ ] User-facing subsystems are created at the second level; vendor subsystems are not changed without an explicit decision.
- [ ] If a feature adds several related objects, create a functional subsystem inside the service subsystem.
- [ ] A user-facing subsystem has a view role; access to the parent subsystem does not imply access to the child subsystem.
- [ ] Object order in a user-facing subsystem supports user workflow: catalogs above documents, important items highlighted by standard means.

## Scheduled Jobs

- [ ] The scheduled-job method is a thin entrypoint; main logic lives in a relevant common module or manager module.
- [ ] At startup call `ОбщегоНазначения.ПриНачалеВыполненияРегламентногоЗадания(Метаданные.РегламентныеЗадания.<JobName>)` when BSP is available.
- [ ] `Использование` is disabled by default unless the job must start immediately after delivery.
- [ ] The job is placed in a service subsystem and has an administrator-readable schedule/description.

## Related Skills

- `ssl-patterns` - exact BSP modules and hooks.
- `form-patterns` and `form-visual-requirements` - forms and UX.
- `query-patterns` and `query-optimize` - object and report queries.
- `api-design` - exported methods in object/manager/common modules.

---
depends_on:
  - ssl-patterns
  - form-patterns
  - query-patterns
  - api-design
---

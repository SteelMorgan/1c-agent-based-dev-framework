# BSP 3.1.11 Borrowings

Source: `https://github.com/brake71/1c-ssl-skills`
Commit: `85783eececb3a658ea15fc793b095ac370b5339c`
Source version: BSP 3.1.11
Import date: 2026-06-30
Local directory: `references/bsp-3.1.11/`

This file records which materials were taken from the external set, which of our skills they are associated with, and why they are needed by the agent. When updating upstream, first compare this file, then update the corresponding reference files and `metadata` in `SKILL.md`.

## Usage Policy

- Use the external layer as a reference for exact BSP API: real module names, signatures, API regions, hooks, deprecated/service boundaries.
- Do not replace our conceptual skills with it. If we have a general skill about background job design, security, or integrations, it defines the engineering policy, while the BSP reference provides concrete calls.
- If there is a mismatch with the current configuration, the project sources and `get_signature_help` / `scripts/bsp_api.py` take priority.
- To update the version, change not only the files, but also the `bsp_reference_version`, `borrowed_commit`, `borrowed_at` lines in `SKILL.md`.

## Weight of Additional Knowledge

| Weight | Meaning |
|---|---|
| Very high | The model often knows the idea, but cannot reliably reconstruct the exact modules, signatures, regions, and BSP restrictions. |
| High | The general logic is known, but the reference significantly reduces the risk of API hallucinations. |
| Medium | We already have a close skill or the knowledge is common; the reference is useful as a check and source of examples. |
| Low | Use only as reference coverage, do not elevate it into the main path. |

## Borrowings Table

| Upstream reference | Local file | Our related skills | Weight | What we took |
|---|---|---|---|---|
| `fundamentals.md` | `references/bsp-3.1.11/fundamentals.md` | `ssl-patterns`, `api-design`, `coding-standards` | Very high | Module suffixes, stable/service/deprecated, `*Переопределяемый` hooks, subsystem map, and common hallucinations. |
| `base-common.md` | `references/bsp-3.1.11/base-common.md` | `ssl-patterns`, `error-handling`, `security`, `form-patterns` | Medium | Exact scenarios for `ОбщегоНазначения*`, strings, dates, XML/JSON, attributes by reference, and secure storage. |
| `longs-and-jobs.md` | `references/bsp-3.1.11/longs-and-jobs.md` | `background-jobs`, `ssl-patterns`, `vanessa-run-loop` | High | Concrete `ДлительныеОперации*` and `РегламентныеЗадания*` API on top of our idempotency rules. |
| `users-access.md` | `references/bsp-3.1.11/users-access.md` | `security`, `ssl-patterns`, `api-design` | Very high | RLS, access group profiles, external users, permission checks, and service API boundaries. |
| `commands-external.md` | `references/bsp-3.1.11/commands-external.md` | `ssl-patterns`, `form-patterns`, `api-design` | Very high | Plug-in commands, external reports/processors, registration, launch, and settings. |
| `print-reports.md` | `references/bsp-3.1.11/print-reports.md` | `ssl-patterns`, `query-optimize`, `form-patterns` | Very high | Print manager, print form collection, report variants, and programmatic report generation. |
| `forms-validation.md` | `references/bsp-3.1.11/forms-validation.md` | `form-patterns`, `form-visual-requirements`, `ssl-patterns` | High | Prohibition of editing attributes, properties, additional attributes, and change-ban dates. |
| `files-and-versions.md` | `references/bsp-3.1.11/files-and-versions.md` | `ssl-patterns`, `security`, `form-patterns` | High | BSP files, volumes, binary data, object versioning, and file attachment to forms. |
| `data-exchange.md` | `references/bsp-3.1.11/data-exchange.md` | `data-exchange`, `background-jobs`, `ssl-patterns` | High | BSP data exchange API, exchange plans, change registration, SaaS areas, and deprecated replacements. |
| `comms.md` | `references/bsp-3.1.11/comms.md` | `integration-patterns`, `security`, `ssl-patterns` | High | Mail, SMS, message templates, discussions, interactions, and communication storage. |
| `contact-info.md` | `references/bsp-3.1.11/contact-info.md` | `ssl-patterns`, `form-patterns` | High | Contact information, addresses, classifier, string representations, and input forms. |
| `currencies-banks.md` | `references/bsp-3.1.11/currencies-banks.md` | `ssl-patterns`, `query-patterns` | High | Exchange rates, banks, BIK, production calendars, and work schedules. |
| `prefixes.md` | `references/bsp-3.1.11/prefixes.md` | `ssl-patterns`, `coding-standards` | Medium | Number and code prefixes, information base prefix, and non-standard number formats. |
| `update.md` | `references/bsp-3.1.11/update.md` | `ssl-patterns`, `error-handling`, `test-writing` | High | Update handlers, information base version, safe write during update, and update hooks. |
| `esign-mcd.md` | `references/bsp-3.1.11/esign-mcd.md` | `security`, `integration-patterns`, `ssl-patterns` | Very high | E-signatures, MCD, cryptography, DSS, signature verification, and application/service API boundaries. |
| `protection-pd.md` | `references/bsp-3.1.11/protection-pd.md` | `security`, `ssl-patterns`, `form-patterns` | Very high | 152-FZ, consents, personal data destruction, access logging, and lifecycle hooks. |
| `perf-monitoring.md` | `references/bsp-3.1.11/perf-monitoring.md` | `query-optimize`, `db-performance`, `runtime-investigation` | High | Performance evaluation, key operations, business statistics, and monitoring center. |
| `admin-tools.md` | `references/bsp-3.1.11/admin-tools.md` | `runtime-investigation`, `v8-session-manager`, `security` | Medium | User session termination, deletion of marked items, and security profiles. |
| `backup.md` | `references/bsp-3.1.11/backup.md` | `runtime-investigation`, `security` | Medium | Information base backup and administration scenarios through BSP. |
| `bp-tasks.md` | `references/bsp-3.1.11/bp-tasks.md` | `ssl-patterns`, `form-patterns` | High | Business processes, tasks, performers, task state, and task forms. |
| `classifiers.md` | `references/bsp-3.1.11/classifiers.md` | `ssl-patterns`, `integration-patterns` | Medium | Loading and updating regulatory and reference information. |
| `external-components.md` | `references/bsp-3.1.11/external-components.md` | `integration-patterns`, `security`, `ssl-patterns` | High | External components, OData, and safe usage boundaries. |
| `multilang.md` | `references/bsp-3.1.11/multilang.md` | `coding-standards`, `ssl-patterns` | Medium | Multilingual support, `НСтр`, current language, and localization. |
| `report-dedup.md` | `references/bsp-3.1.11/report-dedup.md` | `ssl-patterns`, `query-patterns`, `form-patterns` | High | Duplicate search, batch object modification, and hierarchy structure. |
| `scripts/bsp_api.py` | `scripts/bsp_api.py` | `code-navigation`, `code-verification`, `ssl-patterns` | Very high | Deterministic search for BSP methods and modules in the `src/cf` export with an API region. |

## Update

1. Get the new upstream commit and record its SHA.
2. Compare the list of `references/*.md` files and `scripts/bsp_api.py`.
3. Update only the changed files in `references/bsp-3.1.11/` or create a new version directory if the BSP version changed.
4. Update this table: `Commit`, `Import date`, links, weight, and a short description of the changes.
5. Update `metadata` in `SKILL.md`.
6. Run the structure check and, if there is a BSP export, `python scripts/bsp_api.py modules --src src/cf`.

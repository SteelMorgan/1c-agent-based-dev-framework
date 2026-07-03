---
name: rlm-bsl-search
description: "Rules for using RLM tools for project search and navigation in 1C/BSL"
---

# RLM tools for 1C/BSL

## When to use

Use this skill when working with 1C/BSL and the answer requires project reconnaissance over configuration source files, even if the user did not explicitly ask for "search".

| Situation | First tool |
|----------|-------------------|
| Find the implementation of a procedure, function, handler, subscription, scheduled job | `rlm_start` |
| Understand where a metadata object, attribute, tabular section, form, role, query is used | `rlm_start` |
| Find a similar implementation before changing BSL code | `rlm_start` |
| Understand a business mechanism or call chain in the project | `rlm_start` |
| Investigate XML/MDO/forms/rights/query and their relationships with BSL code | `rlm_start` |
| Assess the blast radius of a change in a 1C configuration | `rlm_start`, then targeted verification via BSL LS |

The main rule: for broad 1C/BSL search, use RLM before `Grep`, `Glob`, broad `Read`, `Bash rg`, `grep`, `find`, and manual recursive file reading.

## Quick decision: RLM or BSL LS

| If you need to... | Use |
|--------------------|-----|
| Find where a mechanism, similar implementation, or business chain lives in the project | RLM first |
| Find all project relationships of an object: BSL, queries, XML/MDO, forms, rights, subscriptions, scheduled jobs | RLM first |
| Understand blast radius before choosing a concrete file/symbol | RLM first |
| Go to definition/references of an already known symbol at a concrete location | BSL LS first |
| Get hover, signature, completion, diagnostics, code actions, rename | BSL LS first |
| Check a changed BSL file after editing | BSL LS diagnostics, then final verification through v8-runner |
| Quickly read one known file/fragment | `Read` / LSP file tool |

Working model: RLM answers “where and what should I search across the project?”, while BSL LS answers “what exactly does this symbol/type/position mean?”. If the task starts from an unclear business need or broad search over 1C sources, start with RLM. If the task is already positional, start with BSL LS.

## When not to use first

| Situation | Tool |
|----------|------------|
| Need diagnostics, hover, signature, completion | BSL LS/LSP tools |
| Need definition/references/rename/formatting/code actions at an already known position | BSL LS/LSP tools |
| Need to read one already known file or a small fragment | `Read` or an LSP file tool |
| The search does not relate to 1C/BSL source files | standard search tools |
| RLM is unavailable, the index is broken, or it clearly does not fit | fallback to `Grep`/`rg`, recording the reason |

## MCP tools

| Tool | When to use | Pitfalls |
|------------|--------------------|-----------------|
| `rlm_start` | Open a project reconnaissance session over 1C/BSL source files | `path` must point to the 1C source root, not an arbitrary repository root |
| `rlm_execute` | Run a batch of search/navigation operations through RLM helpers | Print a compact result, not large file bodies |
| `rlm_help` | Choose a helper/recipe before non-trivial reconnaissance | Call before `rlm_execute` if the `rlm_start` strategy is insufficient |
| `rlm_end` | Close the RLM session | Close it after reconnaissance is complete |
| `rlm_index` | Check index state (`info`) or administer the index | Build/update is usually done by the container, not the agent |
| `rlm_projects` | Work with the RLM project registry | Not needed for a normal session if `path` is known |

## Working cycle

1. Determine that the task requires project 1C/BSL reconnaissance.
2. Call `rlm_start`.
3. In `query`, pass a short working goal for the reconnaissance. This is not a prompt for the internal LLM: `query` helps RLM choose effort, strategy, and business recipe.
4. In `path`, pass the source directory of the 1C configuration, usually `<project-root>/src`, or the exact configuration root where `Configuration.xml` is located. Do not pass the root git repository if it is not the root of the 1C sources.
5. Read the `rlm_start` response: `session_id`, warnings, index status, extension context, strategy, available functions.
6. For a non-trivial task or if in doubt about the helpers, call `rlm_help` before `rlm_execute`.
7. Do reconnaissance through `rlm_execute`: write compact Python, use RLM helpers, and print only useful output.
8. If, after broad reconnaissance, you need precise IDE semantics for a specific symbol/position, switch to BSL LS/LSP tools.
9. Close the session via `rlm_end` when the reconnaissance is finished.

## What to pass in path

`path` should point to the directory where RLM can find the 1C source tree:

```text
<project-root>/src
<project-root>/src/xml
<project-root>/src/cf
```

The exact configuration directory containing `Configuration.xml` is also acceptable.

The MCP proxy can normalize relative, host, and container paths, but this is only a technical path transformation. The semantics do not change: the path must lead to 1C sources, not to an arbitrary project directory.

## RLM and LLM

`rlm_start.query` by itself does not call an LLM. It sets the session goal and affects:

- auto effort;
- strategy;
- business recipe;
- hints for subsequent `rlm_execute`.

Optional `llm_query` and `llm_query_batched` are available only inside `rlm_execute`, if the RLM server is configured with an LLM provider. Without an LLM, RLM remains a deterministic index/search/navigation tool.

## What to do in rlm_execute

Prefer one substantial `rlm_execute` instead of many small `Grep`/`Read` calls.

For complex reconnaissance, split the work into phases:

1. The first `rlm_execute` is discovery only: find and deduplicate candidates (objects, methods,
   registers, forms), output a compact `name/category/path/count` table and the next step.
2. The second `rlm_execute` profiles 3-5 selected production candidates (`get_object_profile`,
   `get_object_full_structure`, `extract_queries`), without callers for all found methods.
3. The third `rlm_execute` is a targeted trace/callers/usages only for methods selected after profiling.

Do not mix discovery + object profiles + callers/call hierarchy in the first batch. That output quickly
becomes noisy, hits `max_output_chars` limits, and loses the tail of the result.

Keep the output compact:

- found methods/objects;
- file paths;
- line numbers;
- brief relationships: callers, usages, forms, rights, query refs;
- the next targeted step, if BSL LS is needed.

Do not print large file bodies unless necessary. First find candidates and only then read exact fragments.
Deduplicate variants of case and language in search terms (`PnL`/`PNL`/`Pnl`, `OKX`/`okx`,
Russian case forms), and also exclude test extensions/modules from production output if the task is not about
tests. If test findings are needed as evidence, print them in a separate section with a limit.

## Index

Usually the agent does not build the index manually. The container starts RLM build/update automatically:

- at startup;
- after file changes via the watcher.

Use `rlm_index` mainly for `info` diagnostics. Apply manual `build`, `update`, `drop` only when you explicitly need to administer the index or fix its state.

## Scenarios

### Find an existing implementation before changing it

1. `rlm_start(query="find an existing implementation of <mechanism>", path="<project-root>/src")`
2. If it is unclear which helper(s) to apply: `rlm_help(category="discovery")`.
3. `rlm_execute`: first `search(...)` / `search_methods(...)`, then targeted `read_file(...)` or a helper for usages/callers.
4. If a specific symbol is found and exact positional verification is needed, switch to BSL LS.
5. `rlm_end`.

### Assess the impact of a metadata object change

1. `rlm_start(query="assess usage of metadata object <Object>", path="<project-root>/src")`
2. `rlm_execute`: find code usages, forms, queries, permissions, subscriptions, scheduled jobs.
3. Summarize the result by usage types and risks.
4. For targeted definition/references on a specific BSL symbol, use BSL LS.
5. `rlm_end`.

### Diagnose the index

1. If RLM responds strangely or slowly, first run `rlm_index(action="info", path="<project-root>/src")`.
2. If the index is stale/missing, check the container logs and watcher settings.
3. Perform manual `build/update/drop` only if it is explicitly needed to restore the index.

## Correct / incorrect

Correct:

```text
Need to understand where the discount is calculated.
1. rlm_start(query="find discount calculation and related calls", path="<project-root>/src")
2. rlm_execute: search("discount"), search_methods("Discount"), get_callers(...)
3. Read/BSL LS only for the found specific files/positions.
```

Why: RLM uses an index and helpers, and does not pull thousands of files into context.

Incorrect:

```text
Grep("Discount", glob="**/*")
Read dozens of found files
Bash rg across all src
```

Why: broad raw search across a large 1C configuration is slower, noisier, and loses metadata, forms, permissions, queries, and call graph links.

## Priorities

RLM first:

- broad static search across 1C/BSL;
- search for similar implementations;
- metadata usages;
- call graph;
- forms, roles, XML/MDO, queries;
- business context and project relationships.

BSL LS first:

- cursor/position based operations;
- diagnostics;
- hover/signature/completion;
- definition/references for an already known position;
- rename/formatting/code actions.

Raw search fallback:

- RLM is unavailable;
- the task is outside 1C/BSL;
- a simple search in one known file is needed;
- RLM does not cover the specific technical case.

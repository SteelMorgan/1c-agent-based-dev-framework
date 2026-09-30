---
name: bsl-performance-measurement
description: "Measure the performance of a reproducible BSL procedure through the 1С debugger and analyze the measurement as an actual call trace. Use it to find the hot path, compare before/after, and verify the multiplicity of server calls when you need a profile of a single controlled run."
---

# Measuring BSL performance through the debugger

Use debugger measurement for a single reproducible and safe run. Treat it simultaneously as a time profile and as proof of which module and which lines actually executed in the selected target.

Do not use this skill to replace an interactive breakpoint stop, tech journal, DBMS plan analysis, or container resource monitoring: measurement localizes the BSL path, but by itself it does not prove the cause in the DBMS, locks, or an external service.

## Record the scenario fingerprint before measurement

Before enabling measure, record the scenario fingerprint:

- the expected entry point: module, procedure, and line, or another unambiguous source locator;
- the exact trigger class and the class of its arguments;
- the start and end time of the run;
- known targetID, session/seance, and measurement key, if they can be captured at the moment of trigger.

The fingerprint is a correlation criterion, not a description of a "similar business operation". Do not replace it with target duration, the number of modules, or the first handler with a business-like name.

## Choose the scope and the control run

Before connecting, fix:

- one business trigger and its input data; the goal is to execute it exactly once;
- the expected target: client, server call, background job, or another session;
- the source code and revision against which moduleData and lineInfo are matched;
- the time limit for the run and the way to stop it safely;
- the comparison criterion: the same trigger, the same data, the same configuration, and identical warm-up for the "before" and "after" variant.

Do not measure a user's productive session and do not profile a broad, nondeterministic scenario. If there is no reproducible trigger, make it minimal first; without it, the profile is uninterpretable.

## Connect debugger and enable measurement

1. Connect (`attach`) to the HTTP debug server and obtain targets.
2. Match the target with the prepared session by type, start time, user, and session number. If necessary, update metadata before starting.
3. Enable measurement mode with the standard RDBG request `setMeasureMode` / `RDBGSetMeasureModeRequest`. Pass `measureModeSeanceID` when available: store this value as the correlation key.
4. Record the start time, targetID, measurement session key, and launch boundary.
5. Only after a successful response, enable exactly one preselected trigger. Do not click again and do not allow parallel background launches of the same path.
6. Wait for the trigger to complete within the predefined timeout, then disable measurement using the same standard mechanism.
7. Receive the asynchronous result, save the original event/payload as evidence before interpreting it.

On platform 8.3.27, the result arrives as an asynchronous event with `xsi:type=DBGUIExtCmdInfoMeasure`; the actual `cmdID` is `measureResultProcessing`. Do not expect a synchronous response with the profile from the enable or disable command.

## Do not lose the asynchronous result

Extract and save the `PerformanceInfoMain` payload. Check at least:

| Field | Use for |
|---|---|
| `targetID` | Match the result to the measured target. |
| `totalDurability` | Get the total measurement duration. |
| `totalIndepServerWorkTime` | Separate independent server work time, if the platform provides it. |
| `performanceFrequency` | Normalize frequency and duration values. |
| `moduleData` | Find the executed modules. |
| `lineInfo` | Parse the lines: `lineNo`, `frequency`, `durability`, `pureDurability`, `serverCallSignal`. |

Verify that `targetID` matches the saved target, and, for multiple simultaneous measurements, that `measureModeSeanceID` or another saved session key also matches. Do not attribute the profile to the desired launch only by close time.

Consider a target eligible only in one of two cases:

1. targetID was captured during trigger and matched the payload;
2. `moduleData`/`lineInfo` contain the expected module, procedure, and line from the scenario fingerprint.

One overall business chain does not replace this evidence. If the fingerprint is absent in the candidate target, that proves that the expected path was not reached in it; do not explain the absence by an "unknown moduleID" or likely competition.

If there are multiple candidates or the expected entry point is not found, set the status to `target_correlation_failed`, save the list of candidates and the fingerprint, perform cleanup, and do not rank hotspots. Never choose a target by duration, by number of modules, or by the first business-looking handler.

The debugger adapter must preserve the raw async event before returning to the calling code. Do not use ordinary `ping` as the only waiting method if it can read and drop the event queue: the next poll will then no longer see the result. Explicitly recognize the event alias `measureResultProcessing`, rather than inventing a different cmdID.

If the typed profiling API is absent, work with the raw protocol only by the officially established XSD/RDBG specification of the corresponding platform. Do not construct types, fields, or XML "by analogy". The adapter must pass `measureModeSeanceID`, if it is provided, and preserve the asynchronous event without loss.

If the event is not found before the control timeout, do not declare a hotspot and do not replace evidence with guesses. Record the lost/unreceived result, exit measurement mode, clear the connection, and check the adapter, the event queue, and the correlation before a new run.

## Read the profile as a call trace

Map `moduleData` to the sources of the exact revision, then map each `lineNo` to the source line. Build a table of lines or subtrees: module, procedure, line, frequency, inclusive, self, server-call signal, and role in the business operation.

| Metric | Interpretation |
|---|---|
| `frequency` | The execution frequency of the line; usually the basis for estimating the number of calls/iterations after normalization. |
| `durability` | Total (inclusive) duration: the line together with nested work. |
| `pureDurability` | Own (self) duration: the line's work without nested calls. |
| `serverCallSignal` | The boundary/signal of a server call; use it to explain client-server multiplicity, not as standalone time. |

Normalize the `frequency`, `durability`, and `pureDurability` counters by `performanceFrequency` if the platform values are given in ticks. State the formula and units in the report; do not compare raw ticks from different measurements as milliseconds.

Treat `durability` as inclusive time, and `pureDurability` as self time. Do not sum the parent's and descendants' inclusive times: the nested work will be counted multiple times.

## Correlate target first, then choose denominator

Follow the order for each measurement: **correlate target → choose business boundary → rank**. Do not move on to business-denominator and hotspot until the target becomes eligible by scenario fingerprint.

## Choose the business-denominator along the nested chain

Do not take the target `totalDurability` as the business-denominator by default. First reconstruct the nested chain of handlers from inclusive lines, frequency, `moduleData`, sources, and neighboring lines: outer transport/polling handler → first business-message handler → its child calls.

For each undisclosed line with moduleID:

1. Map it to `moduleData`, the source revision, and the source line.
2. If there is no direct match, look at neighboring lines, nested inclusive calls, frequency, and handler boundaries.
3. Do not treat an unknown moduleID as proof of the absence of idle/polling: its role is determined by the chain and arithmetic, not by the name.

Apply the following decision:

```text
Is there a first business-message handler with inclusive B?
├─ no → keep target total as the only preliminary denominator and explicitly name the ambiguity.
└─ yes → is target total T approximately equal to B + self of the outer transport/polling wrapper W?
   ├─ yes → classify W as idle/polling; business-denominator = B.
   └─ no → check the remaining time and the chain; use two denominators only while the ambiguity remains unresolved.
```

Show the equation with normalized units: `T ≈ B + W + residual`. If `B` is the first business handler and `residual` is small relative to the error margin/auxiliary lines, do not rank `W` as a business hotspot even if self is high. For business lines, recalculate the share as `normalized metric / B`, not as `/ T`; explicitly label the chosen denominator in each table and in the before/after comparison.

Use `moduleData` and `lineInfo` as the factual trace:

- a line with nonzero frequency proves execution on the measured path;
- a line with high frequency indicates a loop, repeated invocation, or fan-out;
- `serverCallSignal` helps separate overlaps between client and server contexts;
- the absence of a line in the result does not prove it is never called: it did not make it into this target and this single run.

## Find a material cause, not a long line

First exclude from ranking UI waits, polling, sleep, blocking waits, idle time while waiting for an external response, and lines outside the selected business-denominator. The longest wall-time line may be idle rather than a useful optimization point.

Then rank candidates in two different ways:

1. By high `pureDurability`: this is the line's own heavy work or a small subtree.
2. By `frequency × normalized pureDurability`: this is the cumulative effect of a frequent call, even if one iteration is cheap.

Distinguish:

- **high self** — an expensive own operation; investigate its algorithm, data, query, or external boundary;
- **call amplifier** — the line itself is inexpensive, but it multiplies a heavy child call; look for the `N` cycle, the internal multiplicity `K`, and the `N × K` effect;
- **inclusive wrapper** — large inclusive duration with small self; do not optimize the wrapper until an expensive descendant is found;
- **failed resolver / пробный путь** — frequent failed resolutions, checks, or retries; do not call them a business profile without confirming a successful business result.

For each hypothesis, write down the evidence: source line, normalized values, share of the selected denominator, role in the call trace, and excluded waits. Do not conclude "slow database" or "slow HTTP" based on the BSL profile alone: collect the profile artifact for the corresponding level.

## Compare variants and parallel goals

Compare "before" and "after" only with an identical single trigger, data, target, warmup, measurement boundary, and selected business-denominator. Compare not only total, but also:

- the frequency of specific lines and server boundaries;
- self time and accumulated self time;
- the composition of the actual call trace;
- the change in `N × K` for the amplifier;
- the presence of new waits or a disappeared profile.

With multiple concurrent targets, keep a separate record for each target and measurement session. Do not merge their lines and totals until there is an explicit link through targetID, the session key, and the causal chain trigger → server call → result.

## Complete measurement without loose ends

Before outputting or passing on the result:

1. Make sure the measurement mode is turned off even after an error or timeout.
2. Save the raw async event and the normalized summary with targetID, session key, time, trigger, and the original revision.
3. Release the stopped target if breakpoints were used in the same session; remove all breakpoints.
4. Execute `detach`; if the debug state is stuck, perform a standard `force_detach` and recheck the targets.
5. Remove temporary triggers, test data, and launch tools if they were created only for the measurement.

Report the result in the form: “scenario and target → proven trace → normalized metrics and denominator → excluded expectations → cause hypothesis → before/after comparison or the next specialized tool → cleanup confirmation”.

## Checklist against false conclusions

- Do not treat target total as the business denominator without restoring the nested handler chain and the equation `T ≈ B + W + residual`.
- Do not take an unknown moduleID as proof of the absence of idle/polling.
- Do not choose a hotspot only by maximum wall/inclusive time: it may be idle or waiting.
- Do not recursively sum the parent and children inclusive time.
- Do not ignore frequency: modest self at a large `frequency` can dominate.
- Do not miss nested multiplicity `N × K`.
- Do not treat unsuccessful resolver calls as a business profile.
- Do not declare a hotspot if the async event is lost, the target did not match, or the payload is incomplete.
- Do not wait forever: set a hard timeout, turn off measure, and clean up the debug session.

---
depends_on:
  - framework/skills/tool-usage/diagnostics/dap-bsl-code-debug-procedure/SKILL.md
  - framework/skills/tool-usage/diagnostics/runtime-investigation/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---

---
name: infobase-update-rules
description: "1C infobase update rules: dynamic update, incremental build, full rebuild, and safe exclusive mode"
---

# 1C Infobase Update Rules

> Choose the minimally sufficient way to deliver the configuration: dynamic update, a normal incremental build, a full rebuild, or, when necessary, exclusive mode. The specific commands, connection details, and maintenance procedure belong to project supplement.

## 1. Dynamic Update

Dynamic update is preferred for code-only changes: module bodies, algorithms, and form code without changing the metadata structure. It usually takes 1–2 minutes. A duration of up to **5 minutes** is acceptable for the update operation itself; until it completes, observe stdout, logs, the process, and sessions.

Dynamic update is not suitable for schema restructuring: creating, renaming, or deleting attributes, tabular sections, columns, dimensions, resources, and data-bearing objects, as well as type changes. If the platform rejected dynamic update or validation did not confirm application, switch to incremental update or exclusive mode.

After a successful update message, no additional waiting is required: immediately confirm the actual application with a behavioral signal, such as a test, a report, or an observed effect.

## 2. Normal Incremental Update

Incremental update applies only changed objects and is the standard option when dynamic update is not applicable or was not confirmed, but clearing the change-cache is not needed. A duration of up to **10 minutes** is acceptable for the update operation itself; until it completes, observe its state.

When restructuring, perform incremental update only in an exclusive window. After successful completion, immediately confirm the result with a behavioral signal; there is no separate waiting window.

## 3. Full Rebuild or Upload

Use a full rebuild or a full upload as a last resort: when the change-cache is unreliable or corrupted, during recovery after a failure, after a mass edit, when an incremental deploy has been proven incomplete, or when a clean run is required. A duration of **20 minutes or more** is acceptable for the update operation itself; until it finishes, continue monitoring.

A full rebuild is not the default option. If exclusive access is required, first open a monopoly window. After successful completion, do not retain the lock and do not wait for any additional period: perform the verification and remove the restrictions in the finalizer.

## 4. Monopoly Mode

Monopoly mode is mandatory during restructuring and is used when the platform does not allow an update because of activity or locks. It complements the chosen update method, rather than overriding the criteria for choosing an incremental or full build.

Project supplement must define a safe sequence: enable access and scheduled task restrictions, if necessary release connections in a controlled way, apply the changes, and **always** remove the restrictions in the finalizer, including when the update fails. Do not use general examples as ready-made credentials or addresses.

## Update Interruption Prohibition

Never interrupt update just because time has passed. The durations given describe the normal acceptable duration of the operation itself, not a timeout and not a post-success wait. A timeout by itself does not prove a hang and does not give permission to terminate the process.

Interruption is dangerous and can leave the information base in a corrupted or partially updated state. It is permitted to end a specific operation only with the user's direct explicit permission. Without such permission, you may not use `kill`, force Designer/Configurator to terminate, or use any other workaround.

---
depends_on: []
requires:
  - tools

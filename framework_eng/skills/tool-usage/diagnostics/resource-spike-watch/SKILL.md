---
name: resource-spike-watch
description: "Use when you need to capture a CPU or memory spike inside the agent/dev container with process owners and limited log tails."
---

# Resource Spike Watch

## Purpose

Use this skill when you need to understand which process inside the container consumed CPU or memory, especially if the IDE/Cursor/terminal froze only after the spike.

Framework tool:

`/workspaces/work/repos/1C Framework/1c-agent-based-dev-framework/tools/resource-spike-watch/resource-spike-watch.py`

It runs inside the container and reads:

- `/sys/fs/cgroup/cpu.stat` for the container's total CPU load;
- `/sys/fs/cgroup/memory.current`, `memory.events` for memory/OOM;
- `/proc/*` for PIDs, commands, CPU deltas, and RSS;
- limited tails of diagnostic logs.

## Run

Basic run in the current container:

```bash
nohup python3 "/workspaces/work/repos/1C Framework/1c-agent-based-dev-framework/tools/resource-spike-watch/resource-spike-watch.py" \
  --output-dir /tmp/resource-spike-captures \
  >/tmp/resource-spike-watch.log 2>&1 &
```

Default thresholds:

- CPU: more than `4.0` cores over the sampling interval;
- memory: more than `6G`;
- memory growth: more than `768M` over `10` seconds;
- cooldown between full captures: `30` seconds;
- up to `50` capture directories are retained.

For a container with a different memory limit, explicitly specify thresholds:

```bash
python3 ".../resource-spike-watch.py" --cpu-cores 4 --mem-high 6g --mem-growth 768m
```

## Where to find the result

By default, captures are written to:

`/tmp/resource-spike-captures/`

The monitor log itself, if launched through `nohup` as above:

`/tmp/resource-spike-watch.log`

Each triggered alert creates a directory of the form:

`/tmp/resource-spike-captures/2026-07-07T09-31-12Z-cpu-4.82-cores/`

Inside:

- `summary.txt` - the trigger reason, CPU cores, memory.current, thresholds;
- `proc-cpu-delta.tsv` - processes by CPU over the last interval, this is the main file for a CPU spike;
- `proc-memory.tsv` - processes by RSS;
- `recent-samples.csv` - a ring buffer of the latest lightweight samples;
- `cgroup.txt` - `cpu.stat`, `memory.current`, `memory.events`, `memory.peak`;
- `pressure.txt` - `/proc/pressure/{cpu,memory,io}`;
- `recent-files.txt` - recent files in `/tmp` and `/var/log/onec`;
- `logs/` - limited tails of Cursor, Vanessa/MCP, Xvfb/x11vnc, and v8sm logs;

## How to read

1. Start with `summary.txt`.
2. For CPU, look at `proc-cpu-delta.tsv`, not `ps-top.txt`: it shows CPU delta over the interval, not the average since process start.
3. For memory, look at `proc-memory.tsv` and `cgroup.txt`.
4. If there was an OOM, check the growth of `oom` / `oom_kill` counters in `cgroup.txt`.
5. For connection with 1C/VA/MCP, look at `logs/manifest.txt`, then the tails of `/tmp/web-transport.log`, `/tmp/va-mcp-*.log`, `/tmp/mcp-client.log`.

## Limitations

- If a process instantly consumed memory and died between samples, its `/proc` may not make it into the snapshot. Then use `recent-samples.csv`, `memory.events` and log tails.
- The tool does not include the 1C tech journal and does not change the database configuration.
- Log tails are size-limited so the monitor itself does not create significant load.

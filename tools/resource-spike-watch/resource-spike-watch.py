#!/usr/bin/env python3
"""Lightweight in-container CPU/memory spike watcher.

The watcher samples cgroup and /proc counters. On a CPU or memory spike it writes
a bounded diagnostic snapshot with process CPU deltas, memory owners and log tails.
"""

from __future__ import annotations

import argparse
import csv
import os
import shutil
import subprocess
import time
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Deque, Dict, Iterable, List, Optional, Tuple


DEFAULT_LOG_PATTERNS = [
    "/tmp/web-transport.log",
    "/tmp/mcp-client.log",
    "/tmp/mcp-bootstrap.log",
    "/tmp/x11vnc.log",
    "/tmp/xvfb-99.log",
    "/tmp/va-mcp-*.log",
    "/tmp/v8sm/supervisor.log",
    "/home/vscode/.cursor-server/data/logs/*/remoteagent.log",
    "/home/vscode/.cursor-server/data/logs/*/ptyhost.log",
    "/home/vscode/.cursor-server/data/logs/*/exthost*/remoteexthost.log",
    "/home/vscode/.cursor-server/data/logs/*/exthost*/anysphere.cursor-agent-exec/Cursor Agent Exec.log",
]


@dataclass
class ProcSample:
    pid: int
    ppid: int
    utime: int
    stime: int
    rss_kb: int
    vmsize_kb: int
    name: str
    cmd: str

    @property
    def cpu_ticks(self) -> int:
        return self.utime + self.stime


@dataclass
class Sample:
    ts: float
    iso: str
    cpu_usage_usec: int
    mem_bytes: int
    mem_events: Dict[str, int]
    top_cpu_pid: Optional[int]
    top_cpu_cmd: str
    top_rss_pid: Optional[int]
    top_rss_cmd: str


def utc_now() -> Tuple[float, str]:
    ts = time.time()
    iso = datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return ts, iso


def parse_size(value: str) -> int:
    value = value.strip().lower()
    units = {
        "k": 1024,
        "kb": 1024,
        "m": 1024**2,
        "mb": 1024**2,
        "g": 1024**3,
        "gb": 1024**3,
        "t": 1024**4,
        "tb": 1024**4,
    }
    for suffix, factor in sorted(units.items(), key=lambda x: -len(x[0])):
        if value.endswith(suffix):
            return int(float(value[: -len(suffix)]) * factor)
    return int(float(value))


def human_bytes(value: int) -> str:
    amount = float(value)
    for unit in ["B", "KiB", "MiB", "GiB", "TiB"]:
        if abs(amount) < 1024 or unit == "TiB":
            return f"{amount:.1f} {unit}"
        amount /= 1024
    return f"{amount:.1f} TiB"


def find_cgroup_file(name: str) -> Optional[Path]:
    candidates = [
        Path("/sys/fs/cgroup") / name,
        Path("/sys/fs/cgroup/unified") / name,
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    for root, _dirs, files in os.walk("/sys/fs/cgroup"):
        if name in files:
            return Path(root) / name
    return None


def read_kv_file(path: Optional[Path]) -> Dict[str, int]:
    result: Dict[str, int] = {}
    if not path or not path.exists():
        return result
    try:
        for line in path.read_text(errors="replace").splitlines():
            parts = line.split()
            if len(parts) >= 2:
                try:
                    result[parts[0]] = int(parts[1])
                except ValueError:
                    pass
    except OSError:
        pass
    return result


def read_int_file(path: Optional[Path]) -> int:
    if not path or not path.exists():
        return 0
    try:
        return int(path.read_text().strip())
    except (OSError, ValueError):
        return 0


def parse_proc_stat(pid: int) -> Optional[Tuple[int, int, int, int, str]]:
    try:
        text = Path(f"/proc/{pid}/stat").read_text(errors="replace")
    except OSError:
        return None
    rparen = text.rfind(")")
    lparen = text.find("(")
    if lparen < 0 or rparen < lparen:
        return None
    name = text[lparen + 1 : rparen]
    fields = text[rparen + 2 :].split()
    try:
        ppid = int(fields[1])
        utime = int(fields[11])
        stime = int(fields[12])
        rss_pages = int(fields[21])
    except (IndexError, ValueError):
        return None
    return ppid, utime, stime, rss_pages, name


def read_status_sizes(pid: int, page_size_kb: int, rss_pages: int) -> Tuple[int, int]:
    rss_kb = rss_pages * page_size_kb
    vmsize_kb = 0
    try:
        for line in Path(f"/proc/{pid}/status").read_text(errors="replace").splitlines():
            if line.startswith("VmRSS:"):
                rss_kb = int(line.split()[1])
            elif line.startswith("VmSize:"):
                vmsize_kb = int(line.split()[1])
    except (OSError, ValueError, IndexError):
        pass
    return rss_kb, vmsize_kb


def read_cmdline(pid: int, fallback: str) -> str:
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes()
        cmd = raw.replace(b"\0", b" ").decode(errors="replace").strip()
        return cmd or fallback
    except OSError:
        return fallback


def sample_processes() -> Dict[int, ProcSample]:
    page_size_kb = os.sysconf("SC_PAGE_SIZE") // 1024
    result: Dict[int, ProcSample] = {}
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        pid = int(entry.name)
        parsed = parse_proc_stat(pid)
        if not parsed:
            continue
        ppid, utime, stime, rss_pages, name = parsed
        rss_kb, vmsize_kb = read_status_sizes(pid, page_size_kb, rss_pages)
        result[pid] = ProcSample(
            pid=pid,
            ppid=ppid,
            utime=utime,
            stime=stime,
            rss_kb=rss_kb,
            vmsize_kb=vmsize_kb,
            name=name,
            cmd=read_cmdline(pid, name),
        )
    return result


def top_cpu_deltas(
    prev: Dict[int, ProcSample],
    cur: Dict[int, ProcSample],
    elapsed: float,
    limit: int,
) -> List[Tuple[float, ProcSample]]:
    hz = os.sysconf(os.sysconf_names["SC_CLK_TCK"])
    rows: List[Tuple[float, ProcSample]] = []
    if elapsed <= 0:
        return rows
    for pid, sample in cur.items():
        old = prev.get(pid)
        if not old:
            continue
        delta_ticks = sample.cpu_ticks - old.cpu_ticks
        if delta_ticks < 0:
            continue
        cpu_pct = (delta_ticks / hz) / elapsed * 100.0
        rows.append((cpu_pct, sample))
    rows.sort(key=lambda item: item[0], reverse=True)
    return rows[:limit]


def top_memory(cur: Dict[int, ProcSample], limit: int) -> List[ProcSample]:
    rows = list(cur.values())
    rows.sort(key=lambda item: item.rss_kb, reverse=True)
    return rows[:limit]


def run_command(path: Path, command: List[str], timeout: int = 8) -> None:
    try:
        with path.open("w", encoding="utf-8", errors="replace") as out:
            subprocess.run(command, stdout=out, stderr=subprocess.STDOUT, timeout=timeout, check=False)
    except (OSError, subprocess.SubprocessError) as exc:
        path.write_text(f"failed to run {command}: {exc}\n", encoding="utf-8")


def tail_bytes(src: Path, dst: Path, max_bytes: int) -> None:
    try:
        size = src.stat().st_size
        with src.open("rb") as fh:
            if size > max_bytes:
                fh.seek(-max_bytes, os.SEEK_END)
                data = fh.read()
                prefix = f"[tail truncated: source={src} size={size} bytes, kept={max_bytes} bytes]\n".encode()
                data = prefix + data
            else:
                data = fh.read()
        dst.write_bytes(data)
    except OSError as exc:
        dst.write_text(f"failed to tail {src}: {exc}\n", encoding="utf-8")


def collect_log_tails(base: Path, max_bytes: int, max_files: int) -> None:
    logs_dir = base / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)
    paths: List[Path] = []
    for pattern in DEFAULT_LOG_PATTERNS:
        paths.extend(Path("/").glob(pattern.lstrip("/")))
    unique = sorted({p for p in paths if p.is_file()}, key=lambda p: p.stat().st_mtime, reverse=True)
    manifest = logs_dir / "manifest.txt"
    with manifest.open("w", encoding="utf-8") as mf:
        for idx, path in enumerate(unique[:max_files], start=1):
            safe_name = f"{idx:03d}-" + str(path).strip("/").replace("/", "__")
            mf.write(f"{safe_name}\t{path}\n")
            tail_bytes(path, logs_dir / safe_name, max_bytes)


def write_recent_files(path: Path, roots: Iterable[str], newer_than: float, limit: int, max_depth: int) -> None:
    rows: List[Tuple[float, int, str]] = []
    for root in roots:
        root_path = Path(root)
        if not root_path.exists():
            continue
        for current_root, _dirs, files in os.walk(root):
            rel = Path(current_root).relative_to(root_path)
            depth = 0 if str(rel) == "." else len(rel.parts)
            if depth >= max_depth:
                _dirs[:] = []
            for name in files:
                file_path = Path(current_root) / name
                try:
                    stat = file_path.stat()
                except OSError:
                    continue
                if stat.st_mtime >= newer_than:
                    rows.append((stat.st_mtime, stat.st_size, str(file_path)))
    rows.sort(reverse=True)
    with path.open("w", encoding="utf-8") as out:
        for mtime, size, file_path in rows[:limit]:
            iso = datetime.fromtimestamp(mtime, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            out.write(f"{iso}\t{size}\t{file_path}\n")


def write_process_tables(base: Path, prev_proc: Dict[int, ProcSample], cur_proc: Dict[int, ProcSample], elapsed: float) -> None:
    with (base / "proc-cpu-delta.tsv").open("w", encoding="utf-8") as out:
        out.write("cpu_pct\tpid\tppid\trss_kb\tvmsize_kb\tcmd\n")
        for cpu_pct, proc in top_cpu_deltas(prev_proc, cur_proc, elapsed, 80):
            out.write(f"{cpu_pct:.1f}\t{proc.pid}\t{proc.ppid}\t{proc.rss_kb}\t{proc.vmsize_kb}\t{proc.cmd}\n")
    with (base / "proc-memory.tsv").open("w", encoding="utf-8") as out:
        out.write("rss_kb\tvmsize_kb\tpid\tppid\tcmd\n")
        for proc in top_memory(cur_proc, 80):
            out.write(f"{proc.rss_kb}\t{proc.vmsize_kb}\t{proc.pid}\t{proc.ppid}\t{proc.cmd}\n")


def write_samples(path: Path, ring: Deque[Sample]) -> None:
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(
            [
                "iso",
                "cpu_usage_usec",
                "memory_bytes",
                "memory_human",
                "top_cpu_pid",
                "top_cpu_cmd",
                "top_rss_pid",
                "top_rss_cmd",
                "memory_events",
            ]
        )
        for sample in ring:
            writer.writerow(
                [
                    sample.iso,
                    sample.cpu_usage_usec,
                    sample.mem_bytes,
                    human_bytes(sample.mem_bytes),
                    sample.top_cpu_pid or "",
                    sample.top_cpu_cmd,
                    sample.top_rss_pid or "",
                    sample.top_rss_cmd,
                    " ".join(f"{k}={v}" for k, v in sorted(sample.mem_events.items())),
                ]
            )


def write_cgroup_snapshot(base: Path, files: Dict[str, Optional[Path]]) -> None:
    with (base / "cgroup.txt").open("w", encoding="utf-8") as out:
        for label, path in files.items():
            out.write(f"## {label}: {path or 'not found'}\n")
            if path and path.exists():
                try:
                    out.write(path.read_text(errors="replace"))
                except OSError as exc:
                    out.write(f"failed: {exc}\n")
            out.write("\n")


def write_pressure_snapshot(base: Path) -> None:
    with (base / "pressure.txt").open("w", encoding="utf-8") as out:
        for name in ["cpu", "memory", "io"]:
            path = Path("/proc/pressure") / name
            out.write(f"## {path}\n")
            if path.exists():
                try:
                    out.write(path.read_text(errors="replace"))
                except OSError as exc:
                    out.write(f"failed: {exc}\n")
            out.write("\n")


def enforce_retention(output_dir: Path, keep: int) -> None:
    if keep <= 0 or not output_dir.exists():
        return
    dirs = [p for p in output_dir.iterdir() if p.is_dir()]
    dirs.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    for old in dirs[keep:]:
        shutil.rmtree(old, ignore_errors=True)


def capture(
    output_dir: Path,
    reason: str,
    cpu_cores: float,
    mem_growth: int,
    prev_proc: Dict[int, ProcSample],
    cur_proc: Dict[int, ProcSample],
    elapsed: float,
    ring: Deque[Sample],
    files: Dict[str, Optional[Path]],
    args: argparse.Namespace,
) -> Path:
    _ts, iso = utc_now()
    safe_reason = reason.replace(",", "_").replace(" ", "_")
    base = output_dir / f"{iso.replace(':', '-')}-{safe_reason}"
    base.mkdir(parents=True, exist_ok=False)
    mem_current = read_int_file(files["memory.current"])

    with (base / "summary.txt").open("w", encoding="utf-8") as out:
        out.write(f"timestamp: {iso}\n")
        out.write(f"reason: {reason}\n")
        out.write(f"cpu_cores_over_interval: {cpu_cores:.2f}\n")
        out.write(f"memory_current: {mem_current} ({human_bytes(mem_current)})\n")
        out.write(f"memory_growth_window: {mem_growth} ({human_bytes(mem_growth)})\n")
        out.write(f"interval_seconds: {elapsed:.3f}\n")
        out.write(f"threshold_cpu_cores: {args.cpu_cores:.2f}\n")
        out.write(f"threshold_mem_high: {args.mem_high} ({human_bytes(args.mem_high)})\n")
        out.write(f"threshold_mem_growth: {args.mem_growth} ({human_bytes(args.mem_growth)})\n")

    write_process_tables(base, prev_proc, cur_proc, elapsed)
    write_samples(base / "recent-samples.csv", ring)
    write_cgroup_snapshot(base, files)
    write_pressure_snapshot(base)
    write_recent_files(
        base / "recent-files.txt",
        ["/tmp", "/var/log/onec"],
        time.time() - args.recent_seconds,
        args.max_recent_files,
        args.recent_max_depth,
    )
    collect_log_tails(base, args.log_tail_bytes, args.max_log_files)
    run_command(base / "ps-top.txt", ["ps", "-eo", "pid,ppid,pcpu,pmem,rss,vsz,etime,lstart,cmd", "--sort=-pcpu"])
    run_command(base / "threads-top.txt", ["sh", "-lc", "top -b -n1 -H | head -220"])
    return base


def build_sample(
    ts: float,
    iso: str,
    cpu_usage: int,
    mem_bytes: int,
    mem_events: Dict[str, int],
    cpu_rows: List[Tuple[float, ProcSample]],
    cur_proc: Dict[int, ProcSample],
) -> Sample:
    top_cpu = cpu_rows[0][1] if cpu_rows else None
    mem_rows = top_memory(cur_proc, 1)
    top_mem = mem_rows[0] if mem_rows else None
    return Sample(
        ts=ts,
        iso=iso,
        cpu_usage_usec=cpu_usage,
        mem_bytes=mem_bytes,
        mem_events=mem_events,
        top_cpu_pid=top_cpu.pid if top_cpu else None,
        top_cpu_cmd=top_cpu.cmd if top_cpu else "",
        top_rss_pid=top_mem.pid if top_mem else None,
        top_rss_cmd=top_mem.cmd if top_mem else "",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Capture resource spike diagnostics inside a container.")
    parser.add_argument("--interval", type=float, default=2.0, help="Sampling interval in seconds.")
    parser.add_argument("--cpu-cores", type=float, default=4.0, help="Trigger when cgroup CPU usage exceeds this many cores.")
    parser.add_argument("--mem-high", type=parse_size, default=parse_size("6g"), help="Trigger when memory.current exceeds this value.")
    parser.add_argument("--mem-growth", type=parse_size, default=parse_size("768m"), help="Trigger on memory growth over --growth-window.")
    parser.add_argument("--growth-window", type=float, default=10.0, help="Memory growth window in seconds.")
    parser.add_argument("--output-dir", default="/tmp/resource-spike-captures", help="Directory for captures.")
    parser.add_argument("--cooldown", type=float, default=30.0, help="Minimum seconds between full captures.")
    parser.add_argument("--retention", type=int, default=50, help="Keep only this many capture directories.")
    parser.add_argument("--ring-samples", type=int, default=120, help="Recent lightweight samples retained per capture.")
    parser.add_argument("--log-tail-bytes", type=int, default=256 * 1024, help="Bytes kept from each tailed log.")
    parser.add_argument("--max-log-files", type=int, default=40, help="Maximum log files tailed per capture.")
    parser.add_argument("--recent-seconds", type=int, default=900, help="Recent file window for /tmp and /var/log/onec.")
    parser.add_argument("--recent-max-depth", type=int, default=3, help="Maximum directory depth for recent file listing.")
    parser.add_argument("--max-recent-files", type=int, default=300, help="Maximum recent files listed per capture.")
    parser.add_argument("--once", action="store_true", help="Take one baseline snapshot and exit.")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    files = {
        "cpu.stat": find_cgroup_file("cpu.stat"),
        "memory.current": find_cgroup_file("memory.current"),
        "memory.peak": find_cgroup_file("memory.peak"),
        "memory.max": find_cgroup_file("memory.max"),
        "memory.events": find_cgroup_file("memory.events"),
        "memory.events.local": find_cgroup_file("memory.events.local"),
    }

    prev_ts, prev_iso = utc_now()
    prev_cpu = read_kv_file(files["cpu.stat"]).get("usage_usec", 0)
    prev_mem = read_int_file(files["memory.current"])
    prev_proc = sample_processes()
    ring: Deque[Sample] = deque(maxlen=args.ring_samples)
    mem_window: Deque[Tuple[float, int]] = deque()
    last_events = read_kv_file(files["memory.events"])
    last_capture = 0.0

    print(f"resource-spike-watch started at {prev_iso}, output_dir={output_dir}", flush=True)
    print(
        f"thresholds: cpu>{args.cpu_cores} cores, mem>{human_bytes(args.mem_high)}, "
        f"growth>{human_bytes(args.mem_growth)} over {args.growth_window}s",
        flush=True,
    )

    if args.once:
        capture(output_dir, "manual-once", 0.0, 0, prev_proc, prev_proc, 0.0, ring, files, args)
        return 0

    while True:
        time.sleep(args.interval)
        ts, iso = utc_now()
        cpu_usage = read_kv_file(files["cpu.stat"]).get("usage_usec", prev_cpu)
        mem_bytes = read_int_file(files["memory.current"])
        mem_events = read_kv_file(files["memory.events"])
        cur_proc = sample_processes()
        elapsed = max(ts - prev_ts, 0.001)
        cpu_cores = (cpu_usage - prev_cpu) / (elapsed * 1_000_000.0)
        cpu_rows = top_cpu_deltas(prev_proc, cur_proc, elapsed, 5)

        mem_window.append((ts, mem_bytes))
        while mem_window and ts - mem_window[0][0] > args.growth_window:
            mem_window.popleft()
        mem_growth = mem_bytes - mem_window[0][1] if mem_window else 0

        ring.append(build_sample(ts, iso, cpu_usage, mem_bytes, mem_events, cpu_rows, cur_proc))

        reasons: List[str] = []
        if cpu_cores >= args.cpu_cores:
            reasons.append(f"cpu-{cpu_cores:.2f}-cores")
        if mem_bytes >= args.mem_high:
            reasons.append(f"mem-high-{human_bytes(mem_bytes)}")
        if mem_growth >= args.mem_growth:
            reasons.append(f"mem-growth-{human_bytes(mem_growth)}")
        for key in ("oom", "oom_kill"):
            if mem_events.get(key, 0) > last_events.get(key, 0):
                reasons.append(f"memory-event-{key}")

        if reasons and ts - last_capture >= args.cooldown:
            path = capture(
                output_dir,
                ",".join(reasons),
                cpu_cores,
                mem_growth,
                prev_proc,
                cur_proc,
                elapsed,
                ring,
                files,
                args,
            )
            last_capture = ts
            enforce_retention(output_dir, args.retention)
            print(f"{iso} captured {path} reasons={','.join(reasons)}", flush=True)

        prev_ts = ts
        prev_cpu = cpu_usage
        prev_mem = mem_bytes
        prev_proc = cur_proc
        last_events = mem_events


if __name__ == "__main__":
    raise SystemExit(main())

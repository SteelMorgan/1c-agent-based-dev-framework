"""CLI-интеграция watch (CONS-05): read-only инвариант и корректное завершение.

Прогоняется против подготовленного вручную каталога сессии (без адаптеров):
- watch на терминальной сессии завершается с понятным сообщением;
- watch на живой сессии НЕ меняет её состояние (snapshot session.json до/после
  идентичен — включая цикл с SIGINT);
- watch --participant печатает поток только этого участника.
"""
from __future__ import annotations

import json
import signal
import subprocess
import sys
import time
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[2] / "scripts"

SESSION_ID = "cons-20260804-111111-beefcafe"


def _make_session_dir(workdir: Path, *, terminal: bool = False) -> Path:
    sdir = workdir / ".consilium-sessions" / SESSION_ID
    sdir.mkdir(parents=True)
    session = {
        "session_id": SESSION_ID,
        "question": "интеграционный watch",
        "phase": "E" if terminal else "B",
        "round": 2,
        "wave": None if terminal else "response",
        "invocation_count": 9,
        "verdict_done": terminal,
        "participants": [
            {"id": "claude-opus", "family": "claude", "state": "active",
             "review_id": None, "roles": {"A": "architecture"},
             "invocations": 4, "retries": 0},
            {"id": "kimi-k2", "family": "kimi", "state": "active",
             "review_id": None, "roles": {"A": "architecture"},
             "invocations": 5, "retries": 0},
        ],
        "cleanup": {"session_closed": False, "participants_closed": {}},
    }
    (sdir / "session.json").write_text(
        json.dumps(session, ensure_ascii=False, indent=2), encoding="utf-8")
    (sdir / "anon_map.json").write_text(
        json.dumps({"claude-opus": "M1", "kimi-k2": "M2"}), encoding="utf-8")
    records = [
        {"phase": "B", "round": 2, "wave": "response", "author": "claude-opus",
         "type": "response", "content": "ответ claude: agree по M2:E1"},
        {"phase": "B", "round": 2, "wave": "response", "author": "kimi-k2",
         "type": "response", "content": "ответ kimi: disagree по M1:E3"},
    ]
    (sdir / "transcript.jsonl").write_text(
        "".join(json.dumps({"seq": i, "ts": f"2026-08-04T02:00:0{i}Z",
                            "session_id": SESSION_ID, **record}, ensure_ascii=False) + "\n"
                for i, record in enumerate(records, start=1)),
        encoding="utf-8")
    return sdir


def _run_watch(workdir: Path, *args, timeout=60) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPTS_DIR / "consilium.py"), "watch", *args],
        cwd=str(workdir), capture_output=True, text=True, timeout=timeout, check=False,
    )


def test_watch_terminal_session_exits(workdir):
    """watch на терминальной сессии (verdict_done) выходит сразу, exit 0, с причиной."""
    _make_session_dir(workdir, terminal=True)
    result = _run_watch(workdir, SESSION_ID, timeout=30)
    assert result.returncode == 0, result.stderr
    assert "watch завершён" in result.stdout
    assert "вердикт" in result.stdout


def test_watch_terminal_participant_stream_exits(workdir):
    _make_session_dir(workdir, terminal=True)
    result = _run_watch(workdir, SESSION_ID, "--participant", "claude-opus", timeout=30)
    assert result.returncode == 0, result.stderr
    assert "ответ claude" in result.stdout
    assert "ответ kimi" not in result.stdout
    assert "watch завершён" in result.stdout


def test_watch_once_does_not_mutate_session(workdir):
    """--once на живой сессии: session.json до/после идентичен побайтово."""
    sdir = _make_session_dir(workdir)
    before = (sdir / "session.json").read_bytes()
    transcript_before = (sdir / "transcript.jsonl").read_bytes()
    result = _run_watch(workdir, SESSION_ID, "--once")
    assert result.returncode == 0, result.stderr
    assert "[claude-opus]" in result.stdout and "[kimi-k2]" in result.stdout
    assert (sdir / "session.json").read_bytes() == before
    assert (sdir / "transcript.jsonl").read_bytes() == transcript_before


def test_watch_unknown_session_fails(workdir):
    result = _run_watch(workdir, "cons-no-such-session")
    assert result.returncode == 2
    assert "неизвестная сессия" in result.stderr


def test_watch_unknown_participant_fails(workdir):
    _make_session_dir(workdir)
    result = _run_watch(workdir, SESSION_ID, "--participant", "no-such", "--once")
    assert result.returncode == 2
    assert "не найден" in result.stderr


def test_watch_live_loop_sigint_clean_and_readonly(workdir):
    """Живой цикл: 3+ кадра, Ctrl+C (SIGINT) → чистый выход 0, состояние не изменено."""
    sdir = _make_session_dir(workdir)
    before = (sdir / "session.json").read_bytes()
    proc = subprocess.Popen(
        [sys.executable, str(SCRIPTS_DIR / "consilium.py"), "watch", SESSION_ID,
         "--interval", "0.3"],
        cwd=str(workdir), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    time.sleep(1.2)  # несколько кадров опроса
    proc.send_signal(signal.SIGINT)
    stdout, stderr = proc.communicate(timeout=30)
    assert proc.returncode == 0, stderr
    assert "Ctrl+C" in stderr
    assert stdout.count("consilium watch") >= 3  # кадры перерисовывались
    assert (sdir / "session.json").read_bytes() == before


def test_watch_stream_participant_once(workdir):
    """--participant --once: только ходы выбранного участника, с заголовками seq."""
    _make_session_dir(workdir)
    result = _run_watch(workdir, SESSION_ID, "--participant", "kimi-k2", "--once")
    assert result.returncode == 0, result.stderr
    assert "ответ kimi" in result.stdout
    assert "ответ claude" not in result.stdout
    assert "seq=2" in result.stdout

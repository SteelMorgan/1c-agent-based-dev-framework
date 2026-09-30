"""Верификация consilium-watch-herdr.sh без живого herdr (CONS-05, F-004).

Guard-ветки (нет HERDR_ENV, нет herdr в PATH, нет каталога сессии, пустой состав,
невалидные аргументы) → корректные exit-коды и сообщения; happy path и отказ
split — через фейковый herdr-бинарь в PATH, логирующий вызовы в файл.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[2] / "scripts"
HELPER = SCRIPTS_DIR / "consilium-watch-herdr.sh"

SESSION_ID = "cons-20260804-222222-herdtest"


def _make_session(workdir: Path, participant_ids: list[str]) -> None:
    sdir = workdir / ".consilium-sessions" / SESSION_ID
    sdir.mkdir(parents=True)
    session = {
        "session_id": SESSION_ID,
        "participants": [
            {"id": pid, "family": "claude", "state": "active", "review_id": None,
             "roles": {"A": "architecture"}, "invocations": 0, "retries": 0}
            for pid in participant_ids
        ],
    }
    (sdir / "session.json").write_text(
        json.dumps(session, ensure_ascii=False, indent=2), encoding="utf-8")


def _fake_herdr(workdir: Path) -> tuple[Path, Path]:
    """Фейковый herdr: split → JSON с pane_id, run → успех; все вызовы — в лог."""
    bin_dir = workdir / "fake-bin"
    bin_dir.mkdir(exist_ok=True)
    log = workdir / "fake-herdr.log"
    fake = bin_dir / "herdr"
    fake.write_text(
        "#!/usr/bin/env bash\n"
        f"echo \"$@\" >> {log}\n"
        'if [[ "$1" == pane && "$2" == split ]]; then\n'
        "  if [[ \"${FAKE_HERDR_SPLIT_RC:-0}\" != 0 ]]; then exit \"$FAKE_HERDR_SPLIT_RC\"; fi\n"
        '  echo "{\\"result\\":{\\"pane\\":{\\"pane_id\\":\\"w1:p$$\\"}}}"\n'
        "fi\n"
        'if [[ "$1" == pane && "$2" == run ]]; then exit "${FAKE_HERDR_RUN_RC:-0}"; fi\n',
        encoding="utf-8")
    fake.chmod(0o755)
    return bin_dir, log


def _run_helper(workdir: Path, *args, env_extra: dict | None = None,
                strip_path_to: Path | None = None) -> subprocess.CompletedProcess:
    env = os.environ.copy()
    if strip_path_to is not None:
        env["PATH"] = str(strip_path_to)
    if env_extra:
        env.update(env_extra)
    return subprocess.run(
        ["/bin/bash", str(HELPER), *args],
        cwd=str(workdir), env=env, capture_output=True, text=True, timeout=60, check=False,
    )


def test_helper_bash_syntax_ok():
    result = subprocess.run(["bash", "-n", str(HELPER)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr


def test_guard_no_herdr_env(workdir):
    _make_session(workdir, ["claude-opus"])
    env = {"HERDR_ENV": "0"}
    result = _run_helper(workdir, SESSION_ID, env_extra=env)
    assert result.returncode == 1
    assert "HERDR_ENV" in result.stderr


def test_guard_herdr_not_on_path(workdir, tmp_path):
    _make_session(workdir, ["claude-opus"])
    empty_bin = tmp_path / "empty-bin"
    empty_bin.mkdir()
    # python3 нужен guard'ам позже, но до него проверка herdr → PATH без herdr достаточно
    (empty_bin / "python3").symlink_to(sys.executable)
    result = _run_helper(workdir, SESSION_ID,
                         env_extra={"HERDR_ENV": "1"}, strip_path_to=empty_bin)
    assert result.returncode == 1
    assert "herdr не найден" in result.stderr


def test_guard_unknown_session(workdir):
    result = _run_helper(workdir, "cons-no-such", env_extra={"HERDR_ENV": "1"})
    assert result.returncode == 1
    assert "неизвестная сессия" in result.stderr


def test_guard_empty_participants(workdir):
    _make_session(workdir, [])
    result = _run_helper(workdir, SESSION_ID, env_extra={"HERDR_ENV": "1"})
    assert result.returncode == 1
    assert "нет участников" in result.stderr


def test_invalid_interval_rejected(workdir):
    _make_session(workdir, ["claude-opus"])
    result = _run_helper(workdir, SESSION_ID, "--interval", "abc",
                         env_extra={"HERDR_ENV": "1"})
    assert result.returncode == 2
    assert "--interval" in result.stderr
    result = _run_helper(workdir, SESSION_ID, "--bogus", "5",
                         env_extra={"HERDR_ENV": "1"})
    assert result.returncode == 2


def test_happy_path_fake_herdr(workdir):
    """Раскладка: панель статуса + по панели на участника; python -u (F-008),
    экранирование подстановок, фокус не меняется."""
    bin_dir, log = _fake_herdr(workdir)
    _make_session(workdir, ["claude-opus", "kimi-k2"])
    env = {"HERDR_ENV": "1", "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}"}
    result = _run_helper(workdir, SESSION_ID, "--interval", "2.5", env_extra=env)
    assert result.returncode == 0, result.stderr
    calls = log.read_text(encoding="utf-8")
    assert calls.count("pane split") == 3           # статус + 2 участника
    assert calls.count("pane run") == 3
    assert "python3 -u" in calls                     # F-008: unbuffered stdout
    assert f"watch {SESSION_ID} --participant claude-opus" in calls
    assert f"--participant kimi-k2" in calls
    assert "--interval 2.5" in calls
    assert "фокус пользователя не менялся" in result.stdout


def test_split_failure_does_not_crash_midway(workdir):
    """F-007: отказ первого split → понятная ошибка; отказ на середине → warning,
    скрипт доводит остальные панели и сообщает о неполной раскладке."""
    bin_dir, log = _fake_herdr(workdir)
    _make_session(workdir, ["claude-opus"])
    env = {"HERDR_ENV": "1", "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
           "FAKE_HERDR_SPLIT_RC": "1"}
    result = _run_helper(workdir, SESSION_ID, env_extra=env)
    assert result.returncode == 1
    assert "split панели статуса не удался" in result.stderr


def test_participant_pane_cap(workdir):
    """F-007: состав больше потолка панелей → усечение с явным предупреждением."""
    bin_dir, log = _fake_herdr(workdir)
    _make_session(workdir, [f"p{i}" for i in range(10)])
    env = {"HERDR_ENV": "1", "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}"}
    result = _run_helper(workdir, SESSION_ID, env_extra=env)
    assert result.returncode == 0, result.stderr
    assert "ПРЕДУПРЕЖДЕНИЕ" in result.stderr and "потолок панелей" in result.stderr
    calls = log.read_text(encoding="utf-8")
    assert calls.count("pane run") == 8  # статус + 7 участников (MAX_PANES=8)
    assert "p9" not in calls

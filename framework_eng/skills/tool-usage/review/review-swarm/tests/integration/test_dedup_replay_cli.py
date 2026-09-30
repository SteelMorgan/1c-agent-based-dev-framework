"""SI-09 — повторный dedup из состояния DEDUP с реплеем dedup-journal
(RVSW-01, E2E-F1; консилиум E2E-03, вердикт ADOPTED: E1–E6, red-team F-24/F-26).

- SI-09a: dedup --journal-file из TOUR1, затем ПОВТОРНЫЙ dedup --journal-file
  из DEDUP (ранее exit 2 state machine): дельта дописана в сырой журнал,
  результат — проекция ВСЕГО журнала единым fold'ом в порядке файла; чекпоинт
  dedup_complete несёт маркер реплея (записей/применено/отсечено, E4/E5);
- SI-09b: повторный dedup с ТЕМ ЖЕ journal-файлом (дубли записей в сыром
  аудите): дубли отсечены на входе fold'а, неидемпотентный override_auto_confirm
  не рушит реплей (F-07/F-08), проекция идемпотентна;
- SI-09c (негативный, F-26): dedup из TOUR2 и позже — fail-closed (exit 2).
"""
from __future__ import annotations

import json

from swarm_case_helpers import findings_block, verdict_block

EXIT_PROTOCOL = 2


# ---------- Хелперы сценариев ----------

def _session_id(stdout: str) -> str:
    for line in stdout.splitlines():
        if line.startswith("session_id: "):
            return line.split(": ", 1)[1].strip()
    raise AssertionError(f"session_id не найден в выводе convene:\n{stdout}")


def _convene(run_swarm, timeout_sec=60):
    result = run_swarm(
        "convene", "--tier", "swarm",
        "--diff", "changes.diff",
        "--paths", "src/a.py", "src/b.py",
        "--caller", "claude-opus",
        "--timeout-sec", str(timeout_sec),
        "--registry", "adapters.yaml",
    )
    assert result.returncode == 0, result.stderr
    return _session_id(result.stdout)


def _session(workdir, session_id: str) -> dict:
    path = workdir / ".swarm-sessions" / session_id / "session.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _progress(workdir, session_id: str) -> list[dict]:
    path = workdir / ".swarm-sessions" / session_id / "progress.jsonl"
    if not path.exists():
        return []
    return [json.loads(line)
            for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _journal(workdir, session_id: str) -> list[dict]:
    path = workdir / ".swarm-sessions" / session_id / "dedup-journal.jsonl"
    if not path.exists():
        return []
    return [json.loads(line)
            for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _finding(path, start, end, category, severity, claim):
    return {
        "location": {"path": path, "line_start": start, "line_end": end},
        "category": category,
        "severity": severity,
        "in_lens": True,
        "claim": claim,
        "evidence": f"evidence для {claim}",
        "rationale": "обоснование",
    }


def _script_tour1(reply_dir):
    """Пул: F-001 уникальная (src/a.py), F-002/F-003 — пересекающаяся пара
    claude+codex по src/b.py (механически автоподтверждённый кластер)."""
    reply_dir("claude", 1, findings_block([
        _finding("src/a.py", 10, 12, "security", "P3", "CLAIM-ALPHA"),
        _finding("src/b.py", 5, 8, "correctness", "P3", "CLAIM-BETA"),
    ]))
    reply_dir("codex", 1, findings_block([
        _finding("src/b.py", 6, 9, "correctness", "P3", "CLAIM-GAMMA"),
    ]))
    reply_dir("kimi", 1, findings_block([]))


def _journal_file(workdir, name, entries):
    path = workdir / name
    path.write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
    return name


def _entry(action, finding_ids, reason):
    return {"ts": "2026-07-29T23:00:00Z", "session_id": None, "action": action,
            "finding_ids": finding_ids, "reason": reason, "actor": "orchestrator"}


def _journal_markers(workdir, session_id) -> list[dict]:
    """Маркеры реплея из чекпоинтов dedup_complete (E5, плоские счётчики)."""
    markers = []
    for rec in _progress(workdir, session_id):
        counters = rec.get("counters") or {}
        if rec.get("checkpoint") == "dedup_complete" and "dedup_journal_entries" in counters:
            markers.append({
                "entries": counters["dedup_journal_entries"],
                "applied": counters["dedup_journal_applied"],
                "duplicates_cut": counters["dedup_journal_duplicates_cut"],
            })
    return markers


# ---------- SI-09a: повторный dedup из DEDUP — проекция всего журнала ----------

def test_si_09a_repeated_dedup_replays_full_journal(run_swarm, three_family_registry,
                                                    review_tree, reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0

    # Первый dedup из TOUR1: Оркестратор разделяет механически склеенный кластер.
    j1 = _journal_file(workdir, "j1.json", [
        _entry("split", ["F-002"], "разные дефекты, соседние строки")])
    result = run_swarm("dedup", session_id, "--journal-file", j1)
    assert result.returncode == 0, result.stderr
    session = _session(workdir, session_id)
    assert session["state"] == "DEDUP"
    assert set(session["threads"]) == {"F-001", "F-002", "F-003"}

    # Повторный dedup из DEDUP (E1): Оркестратор передумал — одна первопричина.
    j2 = _journal_file(workdir, "j2.json", [
        _entry("merge", ["F-002", "F-003"], "одна первопричина: гонка bounds")])
    result = run_swarm("dedup", session_id, "--journal-file", j2)
    assert result.returncode == 0, result.stderr  # ранее: exit 2 (state machine)

    session = _session(workdir, session_id)
    assert session["state"] == "DEDUP"
    # E2/F-24: проекция из ВСЕГО журнала единым fold'ом в порядке файла —
    # split→merge вернул F-002/F-003 в один автоподтверждённый кластер.
    assert set(session["threads"]) == {"F-001"}
    nonunique = session["dedup"]["groups"]["nonunique_auto_confirmed"]
    assert [{f["finding_id"] for f in cl["findings"]} for cl in nonunique] == [
        {"F-002", "F-003"}]
    # сырой журнал — append-only аудит обеих дельт (E3)
    journal = _journal(workdir, session_id)
    assert [e["action"] for e in journal] == ["split", "merge"]
    # E4/E5: маркер реплея в обоих чекпоинтах dedup_complete
    markers = _journal_markers(workdir, session_id)
    assert markers == [
        {"entries": 1, "applied": 1, "duplicates_cut": 0},
        {"entries": 2, "applied": 2, "duplicates_cut": 0},
    ]
    assert run_swarm("close", session_id).returncode == 0


# ---------- SI-09b: дубли в сыром аудите не рушат реплей (F-07/F-08) ----------

def test_si_09b_duplicate_journal_entries_cut_at_fold_input(
        run_swarm, three_family_registry, review_tree, reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0

    # override неидемпотентен при наивном повторном применении (F-07/F-08):
    # повторный dedup с ТЕМ ЖЕ файлом обязан отсечь дубль на входе fold'а.
    j1 = _journal_file(workdir, "j1.json", [
        _entry("override_auto_confirm", ["F-002", "F-003"],
               "скоррелированное ложное срабатывание")])
    assert run_swarm("dedup", session_id, "--journal-file", j1).returncode == 0
    result = run_swarm("dedup", session_id, "--journal-file", j1)
    assert result.returncode == 0, result.stderr

    session = _session(workdir, session_id)
    # проекция идемпотентна: кластер по-прежнему с маркером, треды те же
    assert set(session["threads"]) == {"F-001", "F-002", "F-003"}
    moved = next(
        cl for cl in session["dedup"]["clusters"]
        if {f["finding_id"] for f in cl["findings"]} == {"F-002", "F-003"})
    assert moved["auto_confirmed"] is False
    assert moved["auto_confirmed_overridden"] is True
    # журнал — сырой аудит: дубль СОХРАНЁН в файле, отсечён только на входе fold'а
    assert len(_journal(workdir, session_id)) == 2
    # E4: применено — ПОСЛЕ отсечения дублей; E5: маркер фиксирует отсечение
    markers = _journal_markers(workdir, session_id)
    assert markers[-1] == {"entries": 2, "applied": 1, "duplicates_cut": 1}
    assert run_swarm("close", session_id).returncode == 0


# ---------- SI-09c: негативный — dedup из TOUR2+ fail-closed (F-26) ----------

def test_si_09c_dedup_from_tour2_fail_closed(run_swarm, three_family_registry,
                                             review_tree, reply_dir, stub_env):
    three_family_registry()
    _script_tour1(reply_dir)
    reply_dir("codex", 2, verdict_block("F-001", "upheld", "src/a.py", 11, "quote-1"))
    reply_dir("kimi", 2, verdict_block("F-001", "upheld", "src/a.py", 12, "quote-2"))
    session_id = _convene(run_swarm)
    workdir = stub_env["cwd"]
    assert run_swarm("attack", session_id).returncode == 0
    assert run_swarm("dedup", session_id).returncode == 0
    assert run_swarm("assess", session_id).returncode == 0
    assert _session(workdir, session_id)["state"] == "TOUR2"

    result = run_swarm("dedup", session_id)
    assert result.returncode == EXIT_PROTOCOL, \
        "dedup из TOUR2+ обязан оставаться fail-closed (red-team F-26)"
    assert run_swarm("close", session_id).returncode == 0

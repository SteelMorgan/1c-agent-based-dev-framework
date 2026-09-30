"""Unit-тесты watch-слоя (CONS-05): рендеринг и чтение на фейковом каталоге сессии.

Фикстуры — руками собранный `.consilium-sessions/<id>/` (session.json,
transcript.jsonl, progress.jsonl, anon_map.json); адаптеры не вызываются.
"""
from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

import consilium_watch as watch

SESSION_ID = "cons-20260804-000000-deadbeef"


def _write_session(sdir: Path, **overrides) -> dict:
    session = {
        "session_id": SESSION_ID,
        "question": "тестовый вопрос",
        "phase": "B",
        "round": 1,
        "wave": "attack",
        "invocation_count": 5,
        "silence_threshold_sec": 120.0,
        "participants": [
            {"id": "claude-opus", "family": "claude", "state": "active",
             "review_id": None, "roles": {"A": "architecture"},
             "invocations": 2, "retries": 0},
            {"id": "codex-gpt", "family": "codex", "state": "killed",
             "review_id": None, "roles": {"A": "security"}, "killed_at_round": 1,
             "invocations": 3, "retries": 1},
        ],
        "cleanup": {"session_closed": False, "participants_closed": {}},
    }
    session.update(overrides)
    (sdir / "session.json").write_text(
        json.dumps(session, ensure_ascii=False, indent=2), encoding="utf-8")
    return session


def _write_transcript(sdir: Path, records: list[dict]) -> None:
    lines = []
    for seq, record in enumerate(records, start=1):
        lines.append(json.dumps({"seq": seq, "ts": f"2026-08-04T00:00:{seq:02d}Z",
                                 "session_id": SESSION_ID, **record},
                                ensure_ascii=False))
    (sdir / "transcript.jsonl").write_text("\n".join(lines) + "\n", encoding="utf-8")


@pytest.fixture()
def fake_session(workdir):
    """Фейковый каталог сессии: 2 участника, 3 записи transcript, 1 чекпоинт."""
    sdir = workdir / ".consilium-sessions" / SESSION_ID
    sdir.mkdir(parents=True)
    _write_session(sdir)
    _write_transcript(sdir, [
        {"phase": "A", "round": 0, "wave": None, "author": "claude-opus",
         "type": "proposal", "content": "модель claude\nдеталь 1\nдеталь 2"},
        {"phase": "B", "round": 1, "wave": "attack", "author": "codex-gpt",
         "type": "attack", "content": "атака на M1:E2"},
        {"phase": "B", "round": 1, "wave": "attack", "author": "moderator",
         "type": "system", "content": "ход codex-gpt отклонён ДО записи (E-4)"},
    ])
    (sdir / "anon_map.json").write_text(
        json.dumps({"claude-opus": "M1", "codex-gpt": "M2"}), encoding="utf-8")
    (sdir / "progress.jsonl").write_text(json.dumps({
        "ts": "2026-08-04T00:00:10Z", "session_id": SESSION_ID, "tool": "consilium",
        "checkpoint": "phase_a_complete", "summary": "фаза A завершена",
        "counters": {"invocations": 2}}, ensure_ascii=False) + "\n", encoding="utf-8")
    return sdir


def test_load_snapshot_reads_all_artifacts(fake_session):
    snapshot = watch.load_snapshot(fake_session)
    assert snapshot["session"]["session_id"] == SESSION_ID
    assert len(snapshot["records"]) == 3
    assert snapshot["checkpoints"][0]["checkpoint"] == "phase_a_complete"
    assert snapshot["anon_map"] == {"claude-opus": "M1", "codex-gpt": "M2"}


def test_terminal_reason_live_session(fake_session):
    assert watch.terminal_reason(fake_session, watch.load_snapshot(fake_session)) is None


def test_terminal_reason_verdict_done(fake_session):
    _write_session(fake_session, verdict_done=True)
    reason = watch.terminal_reason(fake_session, watch.load_snapshot(fake_session))
    assert "вердикт" in reason


def test_terminal_reason_closed_checkpoint(fake_session):
    with (fake_session / "progress.jsonl").open("a", encoding="utf-8") as fh:
        fh.write(json.dumps({"ts": "2026-08-04T01:00:00Z", "session_id": SESSION_ID,
                             "tool": "consilium", "checkpoint": "closed",
                             "summary": "закрыта", "counters": {}},
                            ensure_ascii=False) + "\n")
    reason = watch.terminal_reason(fake_session, watch.load_snapshot(fake_session))
    assert "closed" in reason


def test_terminal_reason_dir_removed(fake_session, workdir):
    import shutil
    shutil.rmtree(fake_session)
    reason = watch.terminal_reason(fake_session, watch.load_snapshot(fake_session))
    assert "удалён" in reason


def test_render_overview_shows_participants(fake_session, workdir):
    text = watch.render_overview(watch.load_snapshot(fake_session), workdir)
    assert "фаза B раунд 1" in text
    assert "[claude-opus] anon=M1" in text and "state=active" in text
    assert "[codex-gpt] anon=M2" in text and "state=killed" in text
    assert "последний чекпоинт: phase_a_complete" in text
    # excerpt последнего хода claude — N последних строк содержимого
    assert "деталь 2" in text


def test_render_overview_without_turns(fake_session, workdir):
    _write_transcript(fake_session, [])
    text = watch.render_overview(watch.load_snapshot(fake_session), workdir)
    assert "ходов ещё не было" in text


def test_participant_events_tail_semantics(fake_session):
    records = watch.load_snapshot(fake_session)["records"]
    events = watch.participant_events(records, "codex-gpt")
    # ход codex-gpt + system-запись модератора о нём; ход claude-opus не попадает
    assert [e["type"] for e in events] == ["attack", "system"]
    # after_seq отсекает уже показанное
    assert watch.participant_events(records, "codex-gpt", after_seq=3) == []


def test_render_turn_full_and_excerpt(fake_session):
    record = watch.load_snapshot(fake_session)["records"][0]
    full = watch.render_turn(record)
    assert "seq=1 proposal" in full and "модель claude" in full
    excerpt = watch.render_turn(record, excerpt_lines=1)
    assert "деталь 2" in excerpt and "модель claude" not in excerpt


# ---------------------------------------------------------------------------
# F-001/F-002: живой прогресс адаптера из runtime.json + liveness между волнами
# ---------------------------------------------------------------------------

def _write_runtime(workdir, review_id, **overrides):
    """runtime.json sandbox'а участника (как пишет адаптер во время инвокации)."""
    review_dir = workdir / ".review-sandboxes" / review_id
    review_dir.mkdir(parents=True, exist_ok=True)
    runtime = {
        "review_id": review_id,
        "state": "running",
        "phase": "generating",
        "elapsed_sec": 42.5,
        "last_heartbeat_at": datetime.now(UTC).isoformat(),
        "last_activity_at": datetime.now(UTC).isoformat(),
        "progress": {"raw_events": 12, "event_types": {"assistant": 5},
                     "tool_calls_total": 3, "last_event_type": "assistant"},
    }
    runtime.update(overrides)
    (review_dir / "runtime.json").write_text(
        json.dumps(runtime, ensure_ascii=False), encoding="utf-8")
    return runtime


def test_overview_shows_adapter_progress_during_wave(fake_session, workdir):
    """F-001: во время волны файлы сессии молчат — прогресс читается из runtime.json."""
    _write_session(fake_session, participants=[
        {"id": "claude-opus", "family": "claude", "state": "active",
         "review_id": "rev-1", "roles": {"A": "architecture"},
         "invocations": 1, "retries": 0},
    ])
    _write_runtime(workdir, "rev-1")
    text = watch.render_overview(watch.load_snapshot(fake_session), workdir)
    assert "адаптер: state=running phase=generating elapsed=42.5" in text
    assert "events=12" in text and "tools=3" in text and "last_event=assistant" in text
    # при идущей инвокации со свежим heartbeat — класс liveness
    assert "liveness=active" in text


def test_liveness_idle_between_waves_not_dead_watcher(fake_session, workdir):
    """F-002: heartbeat тикает только в инвокации; в паузе — «инвокация не идёт»
    + возраст активности, голый dead_watcher не печатается."""
    _write_session(fake_session, participants=[
        {"id": "claude-opus", "family": "claude", "state": "active",
         "review_id": "rev-2", "roles": {"A": "architecture"},
         "invocations": 1, "retries": 0},
    ])
    stale = (datetime.now(UTC) - timedelta(seconds=45)).isoformat()
    _write_runtime(workdir, "rev-2", state="completed", phase="finished",
                   last_heartbeat_at=stale, last_activity_at=stale)
    text = watch.render_overview(watch.load_snapshot(fake_session), workdir)
    assert "инвокация не идёт (state=completed, активность 4" in text  # ~45с назад
    assert "dead_watcher" not in text


# ---------------------------------------------------------------------------
# F-003: инварианты чтения — битый хвост transcript, cleanup.session_closed
# ---------------------------------------------------------------------------

def test_broken_transcript_tail_skipped_then_completed(fake_session):
    """Битая хвостовая строка (гонка с append) заменяется sentinel'ом и
    дочитывается на следующем poll после завершения записи. Sentinel
    (CONS-06 rev F-007) сохраняет позиции: позиционный fallback seq в
    participant_events не смещается между poll'ами."""
    path = fake_session / "transcript.jsonl"
    valid = json.dumps({"seq": 10, "author": "claude-opus", "type": "attack",
                        "content": "ход"}, ensure_ascii=False)
    path.write_text(valid + "\n{\"seq\": 11, \"author\": \"claude-opus\", \"ty",
                    encoding="utf-8")
    records = watch.load_snapshot(fake_session)["records"]
    assert len(records) == 2 and records[0]["seq"] == 10  # битый хвост не роняет чтение
    assert records[1].get("_watch_broken") is True  # sentinel держит позицию
    # следующий poll: запись дописана — дочитывается
    path.write_text(valid + "\n" + json.dumps(
        {"seq": 11, "author": "claude-opus", "type": "attack", "content": "догнал"},
        ensure_ascii=False) + "\n", encoding="utf-8")
    records = watch.load_snapshot(fake_session)["records"]
    assert [r["seq"] for r in records] == [10, 11]


def test_broken_middle_line_does_not_shift_positions(fake_session):
    """CONS-06 rev F-007: битая строка в СЕРЕДИНЕ файла не смещает позиционный
    fallback — запись без seq после неё печатается ровно один раз."""
    path = fake_session / "transcript.jsonl"
    path.write_text(
        json.dumps({"author": "moderator", "type": "system", "content": "без seq 1"}) + "\n"
        + "{битая строка\n"
        + json.dumps({"author": "moderator", "type": "system", "content": "без seq 2"}) + "\n",
        encoding="utf-8")
    records = watch.load_snapshot(fake_session)["records"]
    first = watch.participant_events(records, "moderator", after_seq=0)
    assert [e["content"] for e in first] == ["без seq 1", "без seq 2"]
    cursor = max(e["_watch_seq"] for e in first)
    # Курсор = позиция с учётом sentinel'а; повторный poll не даёт дублей.
    again = watch.participant_events(records, "moderator", after_seq=cursor)
    assert again == []


def test_terminal_reason_session_closed(fake_session):
    """F-003: терминальная ветка cleanup.session_closed."""
    _write_session(fake_session, cleanup={"session_closed": True,
                                          "participants_closed": {}})
    reason = watch.terminal_reason(fake_session, watch.load_snapshot(fake_session))
    assert "session_closed" in reason


# ---------------------------------------------------------------------------
# F-006/F-009: поток участника — точный токен id, записи без seq
# ---------------------------------------------------------------------------

def test_participant_events_no_prefix_collision(fake_session):
    """F-006: 'kimi' не ловит moderator-записи про 'kimi-k2' (границы токена)."""
    _write_transcript(fake_session, [
        {"author": "moderator", "type": "system",
         "content": "участник kimi-k2 → unresponsive (timeout)"},
        {"author": "moderator", "type": "system",
         "content": "ход kimi отклонён ДО записи (E-4)"},
    ])
    records = watch.load_snapshot(fake_session)["records"]
    kimi_events = watch.participant_events(records, "kimi")
    assert [e["content"] for e in kimi_events] == ["ход kimi отклонён ДО записи (E-4)"]
    kimi_k2_events = watch.participant_events(records, "kimi-k2")
    assert len(kimi_k2_events) == 1


def test_participant_events_records_without_seq_printed_once(fake_session):
    """F-009: записи без seq (или seq=0) не отбрасываются навсегда — печатаются
    ровно один раз (эффективный seq = позиция в append-only файле)."""
    path = fake_session / "transcript.jsonl"
    path.write_text(
        json.dumps({"author": "claude-opus", "type": "attack", "content": "без seq"},
                   ensure_ascii=False) + "\n"
        + json.dumps({"seq": 0, "author": "claude-opus", "type": "attack",
                      "content": "seq=0"}, ensure_ascii=False) + "\n",
        encoding="utf-8")
    records = watch.load_snapshot(fake_session)["records"]
    events = watch.participant_events(records, "claude-opus", after_seq=0)
    assert [e["content"] for e in events] == ["без seq", "seq=0"]
    last_seq = max(e["_watch_seq"] for e in events)
    assert watch.participant_events(records, "claude-opus", after_seq=last_seq) == []

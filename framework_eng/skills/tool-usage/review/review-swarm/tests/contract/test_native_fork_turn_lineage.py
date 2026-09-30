"""Пост-форковая линия ходов через НАСТОЯЩИЙ путь swarm → adapter_contract → адаптер.

Граница подделки — исполняемый файл адаптера (`stubs/stub_adapter.py`), то есть
внешний процесс. Наш код не подменяется: `swarm.run_native_fork_shards`,
`ac.fork_participant`, `ac.ask_participant`, `ac.reconcile_participant`,
retry-политика и парсинг stdout исполняются настоящими.

Четыре независимых свойства:
  A — родитель первого пост-форкового хода = фактическая голова ребёнка;
  D — повтор волны после частичного отказа не переисполняет закоммиченный шард;
  E — повтор форка не затирает диагностику первой ошибки;
  F — ошибка ask не маскируется ошибкой reconcile.
"""
from __future__ import annotations

import hashlib
import json

import pytest

import adapter_contract as ac
import swarm

SNAPSHOT_DIGEST = "sha256:" + "c" * 64


def _start_parent(stub_adapter, review_id: str) -> dict:
    result = ac.start_participant(
        stub_adapter["path"], f"parent snapshot {review_id}", [],
        stub_adapter["cwd"], timeout_sec=30, review_id=review_id,
    )
    assert result.ok, result.error
    meta = json.loads(
        (stub_adapter["cwd"] / ac.REVIEW_ROOT / review_id / "review.json")
        .read_text(encoding="utf-8"))
    return meta


def _session(parents: list[dict], session_id: str = "swarm-contract") -> dict:
    participants = [
        {"id": f"stub-{index}", "state": "active", "review_id": meta["review_id"],
         "adapter_session_id": meta["session_id"],
         "provider_checkpoint_id": meta["provider_checkpoint_id"],
         "native_fork": {"supported": True, "automation_safe": True}}
        for index, meta in enumerate(parents, 1)
    ]
    snapshots = {
        f"snap-{p['id']}": {
            "snapshot_id": f"snap-{p['id']}", "participant_id": p["id"],
            "parent_review_id": p["review_id"],
            "parent_session_id": p["adapter_session_id"],
            "provider_checkpoint_id": p["provider_checkpoint_id"],
            "snapshot_digest": SNAPSHOT_DIGEST, "sealed": True,
        }
        for p in participants
    }
    return {
        "session_id": session_id, "participants": participants,
        "execution": {"execution_id": "exec-contract", "root_task_id": "root-contract",
                      "fork_group_id": "fork-contract", "parent_sealed": True,
                      "state": "READY"},
        "parent_snapshots": snapshots, "shards": {}, "tasks": {}, "turns": {},
        "findings": [],
    }


def _materialize(stub_adapter, session: dict):
    sdir = stub_adapter["cwd"] / swarm.session_dir(session["session_id"])
    (sdir / "prompts").mkdir(parents=True, exist_ok=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    return sdir


def _two_task_work(session):
    work = []
    for index, participant in enumerate(session["participants"], 1):
        work.append((participant, f"F-{index}01", f"prompt {index}.1"))
        work.append((participant, f"F-{index}02", f"prompt {index}.2"))
    return work


def _adapters(session, stub_adapter):
    return {p["id"]: stub_adapter["path"] for p in session["participants"]}


# --------------------------------------------------------------------------
# A — родитель первого пост-форкового хода
# --------------------------------------------------------------------------
@pytest.mark.parametrize("turn_semantics", ["coupled", "divergent"])
def test_first_post_fork_turn_parent_is_child_head_not_parent_checkpoint(
    stub_adapter, turn_semantics,
):
    """Первый ход после форка обязан продолжать ГОЛОВУ РЕБЁНКА.

    `coupled` (claude/codex: chk == turn) маскирует дефект; `divergent`
    (kimi: chk `session_…` != turn `local:…`) делает его наблюдаемым.
    """
    stub_adapter["set_semantics"](turn_semantics)
    parent = _start_parent(stub_adapter, "parent-a")
    session = _session([parent], session_id="swarm-property-a")
    sdir = _materialize(stub_adapter, session)
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", _two_task_work(session))
    assert len(shard_ids) == 1

    expected_child_head = (parent["provider_turn_id"] if turn_semantics == "divergent"
                           else parent["provider_checkpoint_id"])

    swarm.run_native_fork_shards(session, stub_adapter["cwd"], _adapters(session, stub_adapter))

    task_ids = session["shards"][shard_ids[0]]["task_ids"]
    assert len(task_ids) == 2, "нужны два хода: первый после форка и продолжение линии"
    turns = {turn["task_id"]: turn for turn in session["turns"].values()}
    assert set(turns) == set(task_ids)
    first, second = (turns[task_id] for task_id in task_ids)
    assert first["parent_turn_id"] == expected_child_head, (
        "первый пост-форковый ход должен продолжать голову ребёнка, "
        "а не provider_checkpoint_id родителя"
    )
    assert second["parent_turn_id"] == first["provider_turn_id"], (
        "второй ход должен продолжать линию, а не начинать её заново"
    )


# --------------------------------------------------------------------------
# D — повтор волны после частичного отказа
# --------------------------------------------------------------------------
def _partial_failure_wave(stub_adapter, session_id: str):
    """Шард 1 успешен, шард 2 падает; возвращает (sdir, ok_shard_id)."""
    parents = [_start_parent(stub_adapter, f"{session_id}-p1"),
               _start_parent(stub_adapter, f"{session_id}-p2")]
    session = _session(parents, session_id=session_id)
    sdir = _materialize(stub_adapter, session)
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", _two_task_work(session))
    assert len(shard_ids) == 2
    ok_shard_id, bad_shard_id = sorted(
        shard_ids, key=lambda sid: session["shards"][sid]["participant_id"])

    # Второй участник валится на первом же ходе; reconcile не доказывает absent.
    bad_operation = session["tasks"][
        session["shards"][bad_shard_id]["task_ids"][0]]["operation_id"]
    stub_adapter["set_control"]({
        "fail_ask_operations": {bad_operation: "provider refused shard-2 turn"},
        "ambiguous_reconcile_operations": {bad_operation: "provider state inspection unavailable"},
    })

    with pytest.raises(RuntimeError, match="native shard failure"):
        swarm.run_native_fork_shards(
            session, stub_adapter["cwd"], _adapters(session, stub_adapter))
    return sdir, session, ok_shard_id


def test_partial_wave_failure_commits_successful_shard_before_raising(stub_adapter):
    """Успевший шард обязан быть durable до подъёма исключения."""
    sdir, _, ok_shard_id = _partial_failure_wave(stub_adapter, "swarm-property-d1")
    on_disk = json.loads((sdir / "session.json").read_text(encoding="utf-8"))
    assert on_disk["shards"][ok_shard_id]["status"] == "COMPLETED", (
        "успешный шард не зафиксирован durable: повтор волны выберет его снова"
    )


def test_wave_retry_does_not_redrive_already_completed_shard(stub_adapter):
    """Повтор волны не гонит провайдерские вызовы по уже завершённому шарду."""
    sdir, session, ok_shard_id = _partial_failure_wave(stub_adapter, "swarm-property-d2")
    on_disk = json.loads((sdir / "session.json").read_text(encoding="utf-8"))
    child_review_id = on_disk["shards"][ok_shard_id]["child_review_id"]

    before = len(stub_adapter["invocations"]())
    with pytest.raises(RuntimeError):
        swarm.run_native_fork_shards(
            on_disk, stub_adapter["cwd"], _adapters(session, stub_adapter))
    replayed = [item["command"] for item in stub_adapter["invocations"]()[before:]
                if child_review_id in item["argv"] and item["command"] in {"fork", "ask"}]
    assert replayed == [], (
        "повторная волна снова погнала провайдерские вызовы по завершённому шарду; "
        f"получено: {replayed}"
    )


# --------------------------------------------------------------------------
# E — повтор форка не затирает первую ошибку
# --------------------------------------------------------------------------
def test_fork_retry_preserves_first_failure_diagnostics(stub_adapter):
    parent = _start_parent(stub_adapter, "parent-e")
    stub_adapter["set_control"]({"fail_fork_children": {
        "child-e": {"message": "provider fork route refused: quota exhausted", "attempts": 1},
    }})
    result = ac.fork_participant(
        stub_adapter["path"], "parent-e", "child-e", stub_adapter["cwd"],
        timeout_sec=30, operation_id="op-e",
        snapshot_digest=SNAPSHOT_DIGEST,
        provider_checkpoint_id=parent["provider_checkpoint_id"],
    )
    assert not result.ok
    assert "quota exhausted" in (result.error or ""), (
        "слепой повтор форка затёр исходную причину вторичной ошибкой песочницы; "
        f"итоговое сообщение: {result.error!r}"
    )


# --------------------------------------------------------------------------
# G — ребёнок, унаследовавший состояние родителя
# --------------------------------------------------------------------------
def test_child_reusing_parent_session_identity_is_rejected(stub_adapter):
    """Класс дефекта из боевого отказа kimi: сервер привязал ребёнка к
    состоянию родителя и проигнорировал переданный каталог. Форк, чей ребёнок
    вернул идентичность родителя, не вправе считаться независимым шардом."""
    parent = _start_parent(stub_adapter, "parent-g")
    session = _session([parent], session_id="swarm-property-g")
    sdir = _materialize(stub_adapter, session)
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", _two_task_work(session))
    child_review_id = "fork-" + hashlib.sha256(
        f"{session['execution']['execution_id']}:{shard_ids[0]}:fork".encode()
    ).hexdigest()[:24]
    stub_adapter["set_control"]({"fork_child_inherits": {
        child_review_id: ["session_id", "workspace_path"]}})

    with pytest.raises(RuntimeError, match="native shard failure"):
        swarm.run_native_fork_shards(
            session, stub_adapter["cwd"], _adapters(session, stub_adapter))

    shard = session["shards"][shard_ids[0]]
    assert shard["status"] == "FAILED", (
        "ребёнок вернул session_id и рабочий каталог родителя, но шард признан "
        f"успешным: {shard.get('child_session_id')!r} == "
        f"{parent['session_id']!r}"
    )
    assert "child session identity" in (shard.get("failure") or ""), (
        f"отказ не называет унаследованную идентичность: {shard.get('failure')!r}"
    )
    assert not session["turns"], (
        "ходы записаны по ребёнку, унаследовавшему идентичность родителя"
    )


# --------------------------------------------------------------------------
# F — ошибка ask не маскируется ошибкой reconcile
# --------------------------------------------------------------------------
def test_ask_failure_is_not_masked_by_reconcile_failure(stub_adapter):
    _start_parent(stub_adapter, "parent-f")
    stub_adapter["set_control"]({
        "fail_ask_operations": {"op-f": "provider refused: context window exceeded"},
        "ambiguous_reconcile_operations": {"op-f": "provider state inspection unavailable"},
    })
    meta = json.loads(
        (stub_adapter["cwd"] / ac.REVIEW_ROOT / "parent-f" / "review.json")
        .read_text(encoding="utf-8"))
    result = ac.ask_participant(
        stub_adapter["path"], "parent-f", "question", stub_adapter["cwd"],
        timeout_sec=30, operation_id="op-f",
        parent_provider_turn_id=meta["provider_turn_id"],
    )
    assert not result.ok
    error = result.error or ""
    assert "provider state inspection unavailable" in error
    assert "context window exceeded" in error, (
        "исходная причина отказа ask потеряна — её подменил текст reconcile; "
        f"итоговое сообщение: {error!r}"
    )

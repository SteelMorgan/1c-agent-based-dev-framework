"""Смешанный состав роя: часть участников — точный форк, часть — прежний путь.

Решение владельца (2026-08-04): kimi участвует БЕЗ точных форков — его шарды
идут прежним путём (`ask_participant` со свежим стартом и переносом контекста в
промпте), а claude и codex продолжают через точный форк. Механизм обязан вести
такой состав, не отказывая всей сессии.

Граница подделки — исполняемый файл адаптера (`review-harness/tests/stubs/
stub_adapter.py`), то есть внешний процесс. Ни `swarm._ensure_native_execution`,
ни `swarm._verdict_wave`, ни `swarm.run_native_fork_shards`, ни функции
`adapter_contract` не подменяются.

Пять независимых свойств:
  M1 — смешанный состав допустим (нет отказа `partially enabled`);
  M2 — изоляция режимов (отказ одной ветки не уносит другую);
  M3 — прежний путь не теряет контекст (находка + фрагмент реально в промпте);
  M4 — нет тихой деградации (объявил, но недоступно → отказ ЛИБО запись degraded);
  M5 — кворум семей и cross-family gate-ревьюер не ослабляются смешением.
"""
from __future__ import annotations

import json
import stat
import textwrap

import pytest

import adapter_contract as ac
import swarm
import swarm_core as core

CHECKED_PATHS = ["services/x/y.py"]

# Ровно то, что отдаёт `stub_adapter.py capabilities` — реестровое объявление
# обязано совпадать с рантаймом байт-в-байт (`_require_native_fork_capability`).
DECLARED_CAPABILITY = {
    "supported": True,
    "route": "stub_native_fork",
    "min_cli_version": "0.0.1",
    "exact_checkpoint": True,
    "automation_safe": True,
}

# Состав: две форкающиеся семьи + kimi прежним путём. Семьи реальные — на них
# держатся кворум разнообразия и cross-family gate (свойство M5).
ROSTER = [
    ("claude-opus", "claude", True),
    ("codex-gpt", "codex", True),
    ("kimi-k2", "kimi", False),
]


# ---------------------------------------------------------------------------
# сборка сессии
# ---------------------------------------------------------------------------

def _checked_set(cwd):
    root = cwd / "review-set"
    (root / "services/x").mkdir(parents=True, exist_ok=True)
    (root / "services/x/y.py").write_text(
        "".join(f"line {i}\n" for i in range(1, 201)), encoding="utf-8")
    return core.checked_set_from_paths(root, CHECKED_PATHS)


def _finding(author_id: str, index: int) -> dict:
    return {
        "finding_id": f"F-{index:03d}",
        "author_id": author_id,
        "location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
        "category": "correctness",
        "severity": "P2",
        "in_lens": True,
        "claim": "деление на ноль при пустом списке",
        "evidence": "services/x/y.py:127 — total / len(items)",
        "rationale": "",
    }


def _start_parents(stub_adapter) -> dict[str, dict]:
    """Тур 1 каждого участника: настоящий `start` через стаб-адаптер."""
    metas = {}
    for participant_id, _family, _forks in ROSTER:
        review_id = f"parent-{participant_id}"
        result = ac.start_participant(
            stub_adapter["path"], f"tour1 {participant_id}", [],
            stub_adapter["cwd"], timeout_sec=30, review_id=review_id)
        assert result.ok, result.error
        metas[participant_id] = json.loads(
            (stub_adapter["cwd"] / ac.REVIEW_ROOT / review_id / "review.json")
            .read_text(encoding="utf-8"))
    return metas


def _entries(stub_adapter, *, adapters: dict[str, str] | None = None,
             declare: dict[str, bool] | None = None) -> dict:
    """Реестр: `native_fork` объявлен ТОЛЬКО у форкающихся участников."""
    adapters = adapters or {}
    declare = declare or {}
    entries = {}
    for participant_id, family, forks in ROSTER:
        entry = {
            "id": participant_id,
            "family": family,
            "enabled": True,
            "gate_legal": True,
            "adapter": adapters.get(participant_id, stub_adapter["path"]),
        }
        if declare.get(participant_id, forks):
            entry["native_fork"] = dict(DECLARED_CAPABILITY)
        entries[participant_id] = entry
    return entries


def _session(stub_adapter, metas: dict[str, dict], *, session_id: str) -> tuple:
    """Сессия после dedup: два открытых треда с РАЗНЫМИ авторами.

    Автор находки не голосует по собственной находке (`_active_voters`,
    `swarm.py`), поэтому при единственной находке её автор не получает в туре 2
    ни одной задачи — форкать у него нечего. Две находки с разными авторами
    дают ход КАЖДОМУ участнику состава и только так позволяют проверить, что
    маршрут выбирается по объявленной возможности, а не по роли в треде:
      F-001 (автор claude) → голосуют codex (форк) и kimi (прежний путь);
      F-002 (автор kimi)   → голосуют claude (форк) и codex (форк).
    """
    cwd = stub_adapter["cwd"]
    checked_set = _checked_set(cwd)
    findings = [_finding("claude-opus", 1), _finding("kimi-k2", 2)]
    participants = [
        {"id": participant_id, "family": family, "state": "active",
         "review_id": metas[participant_id]["review_id"],
         "adapter_session_id": metas[participant_id]["session_id"],
         "provider_checkpoint_id": metas[participant_id]["provider_checkpoint_id"],
         "invocations": 0, "retries": 0}
        for participant_id, family, _forks in ROSTER
    ]
    session = {
        "session_id": session_id,
        "state": "TOUR1",
        "tier": "swarm",
        "orchestrator_id": "claude-opus",
        "caller_family": "claude",
        "timeout_sec": 30,
        "wall_clock": {"started_at": swarm.utc_now(), "budget_sec": 16620},
        "paths": CHECKED_PATHS,
        "checked_set": checked_set,
        "participants": participants,
        "anon_map": {p["id"]: f"M{index}"
                     for index, p in enumerate(participants, 1)},
        "findings": findings,
        "routed_findings": [],
        "rejected": [],
        "threads": {f["finding_id"]: core.start_thread(f) for f in findings},
        "invocation_count": 0,
        # gate-ревьюер другого семейства, чем caller (claude) — это kimi,
        # то есть ровно тот участник, который идёт прежним путём.
        "gate": {"kind": "acceptance", "reviewer_id": "kimi-k2",
                 "verdict": None},
        "shards": {}, "tasks": {}, "turns": {},
    }
    sdir = cwd / swarm.session_dir(session_id)
    (sdir / "prompts").mkdir(parents=True, exist_ok=True)
    (sdir / "review.diff").write_text(
        "--- a/services/x/y.py\n+++ b/services/x/y.py\n", encoding="utf-8")
    swarm.save_session(sdir, session)
    return sdir, session


@pytest.fixture()
def mixed(stub_adapter, monkeypatch):
    """Готовый смешанный расклад: родители тура 1 + сессия + реестр."""
    monkeypatch.chdir(stub_adapter["cwd"])
    metas = _start_parents(stub_adapter)
    return {"metas": metas, "stub": stub_adapter}


def _fork_children_of(stub_adapter, parent_review_id: str) -> list[dict]:
    return [item for item in stub_adapter["invocations"]()
            if item["command"] == "fork" and parent_review_id in item["argv"]]


def _asks_on(stub_adapter, review_id: str) -> list[dict]:
    return [item for item in stub_adapter["invocations"]()
            if item["command"] == "ask" and review_id in item["argv"]]


def _question_of(invocation: dict) -> str:
    argv = invocation["argv"]
    return argv[argv.index("--question") + 1] if "--question" in argv else ""


def _verdict_template(finding_id: str) -> str:
    """Валидный structured-ход тура 2 с плейсхолдером `{turn}` стаба.

    `{turn}` — сквозной номер НОВОГО хода провайдера: он различается у каждого
    голосующего, поэтому evidence заведомо новое и вердикт не отбраковывается
    стоп-правилом новизны (FR-07). Правило разбора и правило unresponsive при
    этом не ослабляются — просто участник наконец отвечает по схеме.
    """
    payload = (
        '{"finding_id": "' + finding_id + '", "verdict": "upheld", '
        '"evidence": {"path": "services/x/y.py", "line": {turn}, '
        '"quote": "line {turn}"}, "rationale": "проверено по коду"}'
    )
    return "Вердикт.\n\n```swarm-verdict\n" + payload + "\n```\n"


def _arm_valid_verdicts(stub_adapter, **control) -> None:
    """Стаб отвечает разбираемым ходом на вопрос по каждой находке."""
    stub_adapter["set_control"]({
        "ask_responses_by_question_marker": {
            finding_id: _verdict_template(finding_id)
            for finding_id in ("F-001", "F-002")},
        **control,
    })


def _adapter_refusing(tmp_path, stub_adapter, command: str):
    """Обёртка над стабом, отказывающая ровно на одной подкоманде."""
    path = tmp_path / f"refuse-{command}-adapter.py"
    path.write_text(textwrap.dedent(f"""\
        #!/usr/bin/env python3
        import subprocess, sys
        if sys.argv[1:2] == [{command!r}]:
            print("provider {command} route refused", file=sys.stderr)
            raise SystemExit(1)
        raise SystemExit(subprocess.run(
            [sys.executable, {str(stub_adapter['path'])!r}, *sys.argv[1:]]).returncode)
        """), encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR)
    return path


# ---------------------------------------------------------------------------
# M1 — смешанный состав допустим
# ---------------------------------------------------------------------------

def test_mixed_roster_is_admissible_and_routes_each_participant_by_capability(mixed):
    """Объявившие идут форком, необъявившие — прежним путём; отказа нет."""
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id="swarm-mixed-m1")
    entries = _entries(stub_adapter)

    swarm._verdict_wave(sdir, session, entries, tour=2)

    forked = [pid for pid, _f, forks in ROSTER if forks]
    legacy = [pid for pid, _f, forks in ROSTER if not forks]
    for participant_id in forked:
        assert _fork_children_of(stub_adapter, f"parent-{participant_id}"), (
            f"участник {participant_id} объявил точный форк, но провайдерского "
            f"вызова fork по его родителю не было"
        )
    for participant_id in legacy:
        assert _asks_on(stub_adapter, f"parent-{participant_id}"), (
            f"участник {participant_id} без объявленной возможности не получил "
            f"хода прежним путём (ask по собственному ревью)"
        )
        assert not _fork_children_of(stub_adapter, f"parent-{participant_id}"), (
            f"участник {participant_id} не объявлял форк, но по его родителю "
            f"всё равно пошёл fork"
        )


# ---------------------------------------------------------------------------
# M2 — изоляция режимов
# ---------------------------------------------------------------------------

def test_legacy_participant_failure_does_not_break_forking_shards(mixed, tmp_path):
    """Отказ участника прежнего пути не отменяет ходы форкающихся."""
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id="swarm-mixed-m2a")
    # Адаптер kimi падает на любом вызове — ветка прежнего пути мертва.
    broken = tmp_path / "broken-adapter.py"
    broken.write_text(textwrap.dedent("""\
        #!/usr/bin/env python3
        import sys
        print("legacy route unavailable", file=sys.stderr)
        raise SystemExit(1)
        """), encoding="utf-8")
    broken.chmod(broken.stat().st_mode | stat.S_IXUSR)
    entries = _entries(stub_adapter, adapters={"kimi-k2": str(broken)})

    swarm._verdict_wave(sdir, session, entries, tour=2)

    for participant_id, _family, forks in ROSTER:
        if not forks:
            continue
        assert _fork_children_of(stub_adapter, f"parent-{participant_id}"), (
            f"падение участника прежнего пути отменило шард форкающегося "
            f"{participant_id}"
        )


def test_forking_shard_failure_does_not_break_legacy_participant(mixed, tmp_path):
    """Отказ шарда форкающегося не отменяет ход участника прежнего пути."""
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id="swarm-mixed-m2b")
    # Адаптер codex подтверждает capability, но отказывает в самом `fork` —
    # боевой отказ маршрута форка у отдельного участника.
    refusing = _adapter_refusing(tmp_path, stub_adapter, "fork")
    entries = _entries(stub_adapter, adapters={"codex-gpt": str(refusing)})

    try:
        swarm._verdict_wave(sdir, session, entries, tour=2)
    except Exception as exc:  # noqa: BLE001 — падение волны само по себе дефект
        pytest.fail(
            "отказ одного форкающегося шарда уронил всю волну смешанного "
            f"состава: {type(exc).__name__}: {exc}")

    assert _asks_on(stub_adapter, "parent-kimi-k2"), (
        "участник прежнего пути не получил хода из-за отказа чужого форка"
    )


# ---------------------------------------------------------------------------
# M3 — прежний путь не теряет контекст
# ---------------------------------------------------------------------------

def test_legacy_participant_prompt_carries_finding_and_code_fragment(mixed):
    """Проверяем ФАКТ передачи: в промпте прежнего пути есть и находка,
    и её фрагмент — иначе участник физически не может дать вердикт."""
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id="swarm-mixed-m3")
    entries = _entries(stub_adapter)

    swarm._verdict_wave(sdir, session, entries, tour=2)

    asks = _asks_on(stub_adapter, "parent-kimi-k2")
    assert asks, "участник прежнего пути не получил ни одного хода"
    question = _question_of(asks[0])
    finding = session["findings"][0]
    assert finding["finding_id"] in question, "в промпте нет идентификатора находки"
    assert finding["claim"] in question, "в промпте нет утверждения находки"
    assert finding["evidence"] in question, (
        "в промпте нет фрагмента кода находки: вердикт по ней невозможен"
    )
    assert finding["location"]["path"] in question, "в промпте нет пути находки"
    assert session["anon_map"][finding["author_id"]] in question, (
        "в промпте прежнего пути потерян anon_id автора — граница анонимности"
    )
    assert finding["author_id"] not in question, (
        "прежний путь раскрыл реальный id автора находки"
    )


# ---------------------------------------------------------------------------
# M4 — нет тихой деградации
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("roster", ["all-declared", "mixed"])
def test_declared_but_unavailable_capability_never_silently_falls_back(
    mixed, tmp_path, roster,
):
    """Объявил возможность, но в бою она недоступна → явный отказ ЛИБО
    явная запись деградации (`session['degraded']`, как у бюджет-гейта).
    Тихий увод на прежний путь — дефект.

    `all-declared` изолирует само свойство от вопроса о допустимости
    смешанного состава; `mixed` проверяет его в целевом раскладе, где отказ
    ПО СОСТАВУ не считается корректным ответом — это дефект свойства M1.
    """
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id=f"swarm-mixed-m4-{roster}")
    # Адаптер codex объявлен в реестре как форкающийся, но его рантайм
    # отвечает `supported: false` — ровно боевой случай kimi.
    downgraded = tmp_path / "downgraded-adapter.py"
    downgraded.write_text(textwrap.dedent(f"""\
        #!/usr/bin/env python3
        import json, subprocess, sys
        if sys.argv[1:2] == ["capabilities"]:
            print(json.dumps({{"native_fork": {{
                "supported": False, "route": "", "min_cli_version": "0.0.1",
                "exact_checkpoint": False, "automation_safe": False}}}}))
            raise SystemExit(0)
        raise SystemExit(subprocess.run(
            [sys.executable, {str(stub_adapter['path'])!r}, *sys.argv[1:]]).returncode)
        """), encoding="utf-8")
    downgraded.chmod(downgraded.stat().st_mode | stat.S_IXUSR)
    entries = _entries(
        stub_adapter, adapters={"codex-gpt": str(downgraded)},
        declare={"kimi-k2": roster == "all-declared"})

    refused = None
    try:
        swarm._verdict_wave(sdir, session, entries, tour=2)
    except Exception as exc:  # noqa: BLE001 — явный отказ допустим
        refused = exc

    if refused is not None:
        assert "partially enabled" not in str(refused), (
            "механизм отказал ПО СОСТАВУ, а не по неподтверждённой возможности "
            f"codex-gpt: {refused}"
        )
        # ПРИМЕЧАНИЕ (наблюдение, не требование этого свойства): текущий отказ
        # рантайм-пробы не называет участника («native fork capability route
        # должна быть непустой строкой»). Диагностируемость — отдельный концерн,
        # здесь не проверяется, чтобы не смешивать свойства.
    else:
        assert session.get("degraded"), (
            "участник объявил точный форк, рантайм его не подтвердил, а "
            "механизм ни отказал, ни записал деградацию — тихий фолбэк"
        )
        assert "codex-gpt" in json.dumps(session["degraded"], ensure_ascii=False), (
            "запись деградации не называет участника, чья возможность "
            f"не подтвердилась: {session['degraded']}"
        )
    assert not _asks_on(stub_adapter, "parent-codex-gpt"), (
        "объявивший точный форк участник тихо уведён на прежний путь "
        "(ask по его родительскому ревью) вместо отказа/записи деградации"
    )


# ---------------------------------------------------------------------------
# M5 — кворум и gate не ослабляются
# ---------------------------------------------------------------------------

def test_mixed_mode_preserves_family_quorum_and_cross_family_gate(mixed):
    """Смешение не вправе вычёркивать участника прежнего пути из состава:
    именно он держит второе семейство и cross-family gate."""
    stub_adapter = mixed["stub"]
    sdir, session = _session(stub_adapter, mixed["metas"],
                             session_id="swarm-mixed-m5")
    entries = _entries(stub_adapter)
    # Все участники отвечают ПО СХЕМЕ — иначе они выбывают в unresponsive по
    # общему правилу разбора, одинаково на обоих маршрутах, и проверка состава
    # перестаёт что-либо говорить о смешанном режиме.
    _arm_valid_verdicts(stub_adapter)

    swarm._verdict_wave(sdir, session, entries, tour=2)

    survivors = {p["id"]: p for p in session["participants"]
                 if p["state"] == "active"}
    assert "kimi-k2" in survivors, (
        "участник без точного форка вычеркнут из состава — смешанный режим "
        "нельзя «решать» отключением прежнего пути"
    )
    families = {p["family"] for p in survivors.values()}
    assert len(survivors) >= 2 and len(families) >= 2, (
        f"кворум разнообразия не пережил смешанный режим: {survivors} / {families}"
    )
    gate_reviewer_id = session["gate"]["reviewer_id"]
    gate_reviewer = survivors.get(gate_reviewer_id)
    assert gate_reviewer is not None, (
        f"gate-ревьюер {gate_reviewer_id} выбыл из состава в смешанном режиме"
    )
    assert gate_reviewer["family"] != session["caller_family"], (
        "gate-ревьюер оказался того же семейства, что и caller"
    )
    # Состав «жив» не по формальному полю state, а по учтённым вотумам: оба
    # маршрута обязаны донести разбираемый ход до ядра.
    def _voters_of(finding_id: str) -> set[str]:
        return {vote["voter_id"]
                for wave in session["threads"][finding_id]["waves"]
                for vote in wave["votes"]}

    assert _voters_of("F-001") == {"codex-gpt", "kimi-k2"}, (
        "вотум участника прежнего пути не дошёл до ядра в смешанной волне: "
        f"{_voters_of('F-001')}"
    )
    assert _voters_of("F-002") == {"claude-opus", "codex-gpt"}, (
        f"вотумы форкающихся участников потеряны: {_voters_of('F-002')}"
    )

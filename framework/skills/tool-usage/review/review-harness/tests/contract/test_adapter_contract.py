"""Contract-слой review-harness (RVSW-01, FR-01/FR-13, AC-13/AC-21; test-plan §4).

Переезд contract-слоя консилиума (CT-01..09 → HC-01..09) на адаптеры
`review-harness/scripts/adapters/` — логика тестов не меняется (ASM-01, FR-17).
Новые assertions библиотеки:
- HC-10 — обязательный sync (FR-01 п.б, FR-09): subcommand есть у всех трёх
  адаптеров; sync_participant() доставляет обновлённый payload в sandbox;
- HC-11 — канонизированные поля активности last_activity_at/last_heartbeat_at
  (FR-13, TD §9.1): обязательны в runtime.json/status, чтение — через
  read_participant_activity(); heartbeat-интервал ≤ 5 c;
- HC-12 — материализация diff файлом внутри sandbox участника (FR-01 п.к, FR-04)
  через materialize_diff().

Assertions internal R-Final (APPROVE_WITH_COMMENTS, RVSW-01):
- HC-13 — gate-промпт адаптеров (P4): REVIEW_PROMPT_PATH резолвится в
  существующий reference исполнителя gate (review-swarm/references/
  review-prompt.md); загруженный промпт НЕ является fallback-заглушкой;
- HC-14 — fallback-copy исключает secrets-материалы (P5): secrets/, .env,
  .secrets* не попадают в sandbox-копию на fallback-пути (без git-фильтра).

Прогон на РЕАЛЬНЫХ адаптерах со stub CLI трёх семейств. Trace: HC-01..HC-14.
"""
from __future__ import annotations

import importlib.util
import json
import os
from datetime import datetime
from pathlib import Path

import pytest

import adapter_contract as ac
from tests.conftest import ADAPTER_PATHS

ALL_FAMILIES = ["claude", "codex", "kimi"]


def _adapter(family: str) -> str:
    path = ADAPTER_PATHS[family]
    assert path.exists(), f"adapter missing: {path}"
    return str(path)


def _start(family, cwd, question="Контрактный вопрос", paths=None, timeout_sec=30, **kw):
    return ac.start_participant(
        _adapter(family), question, paths or [], cwd,
        timeout_sec=timeout_sec, **kw,
    )


def _read_invocations(state_dir: Path, cli: str) -> list[list[str]]:
    log = state_dir / "invocations.jsonl"
    if not log.exists():
        return []
    return [
        json.loads(line)["argv"]
        for line in log.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["cli"] == cli
    ]


# ---------- HC-01: start — парсинг stdout и state-файлы (переезд CT-01) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc01_start_stdout_contract(stub_env, family):
    """HC-01 (CT-01): stdout содержит review_id/session_id/workspace; state-файлы созданы."""
    result = _start(family, stub_env["cwd"])
    assert result.ok, result.error
    assert result.review_id
    assert result.session_id
    review_dir = stub_env["cwd"] / ".review-sandboxes" / result.review_id
    assert (review_dir / "review.json").exists()
    meta = json.loads((review_dir / "review.json").read_text(encoding="utf-8"))
    assert meta["session_id"] == result.session_id
    assert (review_dir / "workspace").is_dir()


# ---------- HC-02: ask — семантика resume (переезд CT-02) ----------

@pytest.mark.parametrize("family,resume_markers", [
    ("claude", ["--resume"]),
    ("codex", ["exec", "resume"]),
    ("kimi", ["-r"]),  # fallback --session допустим (TD 1.2)
])
def test_hc02_resume_semantics(stub_env, family, resume_markers):
    """HC-02 (CT-02): ask продолжает сохранённую сессию адаптера фактическим флагом resume."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    asked = ac.ask_participant(_adapter(family), started.review_id, "Уточнение", stub_env["cwd"], timeout_sec=30)
    assert asked.ok, asked.error
    cli_name = {"claude": "claude", "codex": "codex", "kimi": "kimi"}[family]
    invocations = _read_invocations(stub_env["state_dir"], cli_name)
    assert len(invocations) >= 2
    matching = [call for call in invocations if all(marker in call for marker in resume_markers)]
    assert matching, f"{family}: resume invocation отсутствует в {invocations}"
    resume_call = matching[-1]
    for marker in resume_markers:
        assert marker in resume_call, f"{family}: {marker} not in {resume_call}"
    if family == "kimi":
        assert "--session" not in resume_call or "-r" not in resume_call  # один из двух
        sid_idx = resume_call.index("-r") if "-r" in resume_call else resume_call.index("--session")
        assert resume_call[sid_idx + 1] == started.session_id
    elif family == "claude":
        assert started.session_id in resume_call
    else:
        assert started.session_id in resume_call


# ---------- HC-03: status — наблюдаемость (переезд CT-03) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc03_status_json(stub_env, family):
    """HC-03 (CT-03): status возвращает валидный JSON с runtime/stats."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    status = ac.read_participant_status(_adapter(family), started.review_id, stub_env["cwd"])
    assert status["review_id"] == started.review_id
    assert "runtime" in status
    assert "stats" in status


# ---------- HC-04: таймаут → unresponsive без retry (переезд CT-04) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc04_timeout_maps_unresponsive_no_retry(stub_env, set_stub_mode, family):
    """HC-04 (CT-04): stub молчит → exit 1 + phase=timeout → timeout БЕЗ retry."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    set_stub_mode("timeout")
    result = ac.ask_participant(_adapter(family), started.review_id, "Тихий ход", stub_env["cwd"], timeout_sec=2)
    assert not result.ok
    assert result.kind == "timeout"
    assert result.attempts == 1  # таймаут — без retry
    runtime_path = stub_env["cwd"] / ".review-sandboxes" / started.review_id / "runtime.json"
    runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    assert runtime["phase"] == "timeout"


# ---------- HC-05: ошибка → ровно один retry → unresponsive (переезд CT-05) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc05_error_single_retry(stub_env, set_stub_mode, family):
    """HC-05 (CT-05): ошибка адаптера → ровно один retry; повторная ошибка → error."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    set_stub_mode("error")
    result = ac.ask_participant(_adapter(family), started.review_id, "Ход с ошибкой", stub_env["cwd"], timeout_sec=30)
    assert not result.ok
    assert result.kind == "error"
    assert result.attempts == 2  # ровно один retry


@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc05b_flaky_recovers_on_retry(stub_env, set_stub_mode, family):
    """HC-05b (CT-05b): flaky (error → ok) — retry восстанавливает ход."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    set_stub_mode("flaky")
    result = ac.ask_participant(_adapter(family), started.review_id, "Flaky ход", stub_env["cwd"], timeout_sec=30)
    assert result.ok, result.error
    assert result.attempts == 2


# ---------- HC-06: close — очистка payload с lock tombstone (переезд CT-06) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc06_close_removes_payload_but_preserves_lock_tombstone(stub_env, family):
    """HC-06 (CT-06): close очищает sandbox, но не удаляет stable lock inode."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    review_dir = stub_env["cwd"] / ".review-sandboxes" / started.review_id
    assert review_dir.is_dir()
    assert ac.close_participant(_adapter(family), started.review_id, stub_env["cwd"])
    assert review_dir.is_dir()
    assert [entry.name for entry in review_dir.iterdir()] == ["invocation.lock"]

    started2 = _start(family, stub_env["cwd"])
    assert started2.ok, started2.error
    review_dir2 = stub_env["cwd"] / ".review-sandboxes" / started2.review_id
    assert ac.close_participant(_adapter(family), started2.review_id, stub_env["cwd"], keep_sandbox=True)
    assert review_dir2.is_dir()
    # Повторный close очищает payload, а fixture удалит lock tombstone.
    ac.close_participant(_adapter(family), started2.review_id, stub_env["cwd"])
    assert [entry.name for entry in review_dir2.iterdir()] == ["invocation.lock"]


# ---------- HC-07: focused-paths по умолчанию (переезд CT-07) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc07_focused_paths_no_full_context(stub_env, family):
    """HC-07 (CT-07): start вызывается с явными positional paths, без --full-context."""
    focused = stub_env["cwd"] / "focused.md"
    focused.write_text("фокусный материал", encoding="utf-8")
    result = _start(family, stub_env["cwd"], paths=["focused.md"])
    assert result.ok, result.error
    cli_name = family
    invocations = _read_invocations(stub_env["state_dir"], cli_name)
    assert invocations, "stub CLI не был вызван"
    meta = json.loads(
        (stub_env["cwd"] / ".review-sandboxes" / result.review_id / "review.json").read_text(encoding="utf-8")
    )
    assert meta["full_context"] is False
    assert (stub_env["cwd"] / ".review-sandboxes" / result.review_id / "workspace" / "focused.md").exists()


# ---------- HC-08: kimi — session.resume_hint и последний assistant.content (переезд CT-08) ----------

def test_hc08_kimi_stream_json_parsing(stub_env):
    """HC-08 (CT-08): session_id из session.resume_hint; результат — последний assistant.content."""
    reply = "Первая часть ответа с элементами E1, E2."
    reply_file = stub_env["cwd"] / "reply.md"
    reply_file.write_text(reply, encoding="utf-8")
    os.environ["STUB_REPLY_FILE"] = str(reply_file)
    try:
        result = _start("kimi", stub_env["cwd"])
        assert result.ok, result.error
        assert result.session_id.startswith("session_")
        assert result.text == reply
        # повторный ход — session_id стабилен (resume)
        asked = ac.ask_participant(_adapter("kimi"), result.review_id, "Второй ход", stub_env["cwd"], timeout_sec=30)
        assert asked.ok, asked.error
        assert asked.session_id == result.session_id
    finally:
        os.environ.pop("STUB_REPLY_FILE", None)


# ---------- HC-09: живой smoke (маркер real_cli, опционально; переезд CT-09) ----------

@pytest.mark.real_cli
@pytest.mark.skipif(os.environ.get("CONSILIUM_REAL_CLI") != "1", reason="живой вызов kimi: CONSILIUM_REAL_CLI=1")
def test_hc09_kimi_live_smoke(workdir):
    """HC-09 (CT-09): живой kimi -p round-trip (TD 1.2, ASM-02). Без stub."""
    result = ac.start_participant(
        _adapter("kimi"),
        "Ответь одним словом: работает.",
        [], workdir, timeout_sec=120,
    )
    assert result.ok, result.error
    assert result.session_id
    assert result.text
    ac.close_participant(_adapter("kimi"), result.review_id, workdir)


# ---------- Adapter-side фикс symlink-copy (ратификация D-6, AC-12) ----------

def test_claude_adapter_materializes_dangling_symlink(stub_env, tmp_path):
    """Ратифицированный adapter-side фикс: focused-path через symlink-зеркало
    (.codex/skills/X -> ../../.claude/skills/X) материализуется в sandbox,
    а не отклоняется как unsafe."""
    workdir = stub_env["cwd"]
    real_dir = workdir / "real-mirror" / "skill-x"
    real_dir.mkdir(parents=True)
    (real_dir / "doc.md").write_text("содержимое зеркала", encoding="utf-8")
    link = workdir / "link-mirror"
    link.symlink_to("real-mirror", target_is_directory=True)  # относительная цель, как .codex→.claude

    result = ac.start_participant(
        _adapter("claude"), "Вопрос", ["link-mirror"], workdir, timeout_sec=30,
    )
    assert result.ok, result.error
    copied = (
        workdir / ".review-sandboxes" / result.review_id
        / "workspace" / "real-mirror"
    )
    assert copied.is_dir() and not copied.is_symlink()
    assert (copied / "skill-x" / "doc.md").read_text(encoding="utf-8") == "содержимое зеркала"
    ac.close_participant(_adapter("claude"), result.review_id, workdir)


# ---------- Регресс E2E-F2: typechange git-индекса (symlink → каталог) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_adapter_copies_git_symlink_typechange_directory(stub_env, family):
    """Регресс E2E-F2 (системная невалидность ходов codex в e2e RVSW-01): git
    хранит entry как symlink (mode 120000, зеркало .codex/skills/<skill>), а в
    рабочем дереве на этом пути — реальный каталог (зеркала правил
    .codex/skills/<rule>/SKILL.md). copy sandbox НЕ падает с IsADirectoryError
    (copy2 на каталоге ронял весь start участника → unresponsive): содержимое
    каталога материализуется в workspace."""
    import subprocess

    workdir = stub_env["cwd"]
    ctx = workdir / "ctx"
    ctx.mkdir()
    target = workdir / "real-target"
    target.mkdir()
    (target / "doc.md").write_text("цель symlink'а", encoding="utf-8")
    mirror = ctx / "mirror"
    mirror.symlink_to("../real-target", target_is_directory=True)
    subprocess.run(["git", "init", "-q"], cwd=workdir, check=True)
    subprocess.run(["git", "add", "ctx/mirror"], cwd=workdir, check=True)
    # typechange: в индексе — symlink, на диске — реальный каталог
    mirror.unlink()
    mirror.mkdir()
    (mirror / "SKILL.md").write_text("материализованное зеркало", encoding="utf-8")

    result = _start(family, workdir, paths=["ctx"])
    assert result.ok, result.error
    copied = (
        workdir / ".review-sandboxes" / result.review_id
        / "workspace" / "ctx" / "mirror" / "SKILL.md"
    )
    assert copied.is_file() and not copied.is_symlink()
    assert copied.read_text(encoding="utf-8") == "материализованное зеркало"
    ac.close_participant(_adapter(family), result.review_id, workdir)


# ---------- HC-10: обязательный sync (FR-01 п.б, FR-09) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc10_sync_delivers_payload(stub_env, family):
    """HC-10: subcommand sync существует у всех трёх адаптеров (exit 0, а не
    argparse-отказ) и sync_participant() доставляет обновлённые исходники
    в sandbox участника (канал re-review)."""
    source = stub_env["cwd"] / "focused.md"
    source.write_text("версия 1", encoding="utf-8")
    started = _start(family, stub_env["cwd"], paths=["focused.md"])
    assert started.ok, started.error
    workspace_copy = (
        stub_env["cwd"] / ".review-sandboxes" / started.review_id / "workspace" / "focused.md"
    )
    assert workspace_copy.read_text(encoding="utf-8") == "версия 1"

    source.write_text("версия 2 — обновлённый payload", encoding="utf-8")
    assert ac.sync_participant(_adapter(family), started.review_id, stub_env["cwd"])
    assert workspace_copy.read_text(encoding="utf-8") == "версия 2 — обновлённый payload"


# ---------- HC-11: канонизированные поля активности (FR-13, TD §9.1) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc11_activity_fields_canonical(stub_env, family):
    """HC-11: runtime.json/status содержит last_activity_at и last_heartbeat_at
    (оба обязательны, новый таймстамп не вводится); каноническое чтение — через
    read_participant_activity(); heartbeat-интервал ≤ 5 c; last_activity_at
    обновляется на stream-события stub'а."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error

    status = ac.read_participant_status(_adapter(family), started.review_id, stub_env["cwd"])
    runtime = status["runtime"]
    for field in ac.ACTIVITY_FIELDS:
        assert runtime.get(field), f"{family}: {field} пуст в status.runtime"

    activity = ac.read_participant_activity(stub_env["cwd"], started.review_id)
    for field in ac.ACTIVITY_FIELDS:
        assert activity[field], f"{family}: {field} пуст через read_participant_activity"

    # heartbeat-каденс адаптера 1 c (TD §9.1): последний heartbeat не старше
    # finished_at более чем на 5 c (контрактный потолок heartbeat-интервала).
    heartbeat = datetime.fromisoformat(activity["last_heartbeat_at"])
    finished = datetime.fromisoformat(runtime["finished_at"])
    assert (finished - heartbeat).total_seconds() <= 5

    # last_activity_at — от stream-событий stub'а: не раньше создания runtime.
    activity_at = datetime.fromisoformat(activity["last_activity_at"])
    created = datetime.fromisoformat(runtime["created_at"])
    assert activity_at >= created


def test_hc11b_activity_unknown_review(stub_env):
    """HC-11b: read_participant_activity() на отсутствующем review — None по обоим
    полям (fail-soft чтение, без исключения)."""
    activity = ac.read_participant_activity(stub_env["cwd"], "no-such-review")
    assert activity == {field: None for field in ac.ACTIVITY_FIELDS}


# ---------- HC-12: материализация diff в sandbox (FR-01 п.к, FR-04) ----------

DIFF_SAMPLE = (
    "diff --git a/x.py b/x.py\n"
    "--- a/x.py\n"
    "+++ b/x.py\n"
    "@@ -1 +1 @@\n"
    "-old\n"
    "+new\n"
)


@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc12_materialize_diff(stub_env, family):
    """HC-12: materialize_diff() создаёт diff обычным файлом внутри workspace
    sandbox участника (участник читает diff как файл, а не из промпта)."""
    started = _start(family, stub_env["cwd"])
    assert started.ok, started.error
    target = ac.materialize_diff(stub_env["cwd"], started.review_id, DIFF_SAMPLE)

    meta = json.loads(
        (stub_env["cwd"] / ".review-sandboxes" / started.review_id / "review.json").read_text(encoding="utf-8")
    )
    workspace = Path(meta["workspace_path"])
    assert target == workspace / ac.DIFF_FILENAME
    assert target.is_file() and not target.is_symlink()
    assert target.read_text(encoding="utf-8") == DIFF_SAMPLE


def test_hc12b_materialize_diff_fail_closed(stub_env):
    """HC-12b: materialize_diff() без meta/workspace_path — RuntimeError
    (fail-closed, без молчаливой записи вне sandbox)."""
    with pytest.raises(RuntimeError):
        ac.materialize_diff(stub_env["cwd"], "no-such-review", DIFF_SAMPLE)


def test_hc12c_materialize_diff_rejects_workspace_outside_sandbox(stub_env):
    """HC-12c (R-Final F-05): подмена workspace_path в review.json на путь вне
    `.review-sandboxes/<review_id>/` → RuntimeError, записи по внешнему пути нет."""
    workdir = stub_env["cwd"]
    review_id = "tampered-workspace"
    review_dir = workdir / ".review-sandboxes" / review_id
    review_dir.mkdir(parents=True)
    outside = workdir / "outside-materialize"
    outside.mkdir()
    (review_dir / "review.json").write_text(
        json.dumps({"review_id": review_id, "workspace_path": str(outside.resolve())}),
        encoding="utf-8",
    )
    with pytest.raises(RuntimeError):
        ac.materialize_diff(workdir, review_id, DIFF_SAMPLE)
    assert list(outside.iterdir()) == []


# ---------- HC-13: gate-промпт адаптеров — reference исполнителя gate (R-Final P4) ----------

def _load_adapter_module(family: str):
    """Импорт адаптера как модуля: top-level константы без запуска CLI."""
    path = ADAPTER_PATHS[family]
    spec = importlib.util.spec_from_file_location(f"review_harness_adapter_{family}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("family", ["claude", "codex"])
def test_hc13_review_prompt_resolves_to_gate_reference(family):
    """HC-13 (internal R-Final, P4): REVIEW_PROMPT_PATH адаптера резолвится в
    существующий reference исполнителя gate (review-swarm/references/
    review-prompt.md — носитель gate-семантики, RVSW-01 TD §3.2), а загруженный
    промпт НЕ является встроенной fallback-заглушкой. Переименование/перенос
    reference — молчаливое переключение на fallback; ловится здесь."""
    module = _load_adapter_module(family)
    candidates = module.REVIEW_PROMPT_CANDIDATES
    assert any(path.is_file() for path in candidates), (
        f"{family}: ни один кандидат gate-промпта не существует: "
        f"{[str(p) for p in candidates]}"
    )
    assert module.REVIEW_PROMPT_PATH.is_file(), (
        f"{family}: REVIEW_PROMPT_PATH не существует: {module.REVIEW_PROMPT_PATH}"
    )
    loader = getattr(module, "load_review_prompt", None) or module.load_review_system_prompt
    fallback = (
        getattr(module, "FALLBACK_REVIEW_PROMPT", None)
        or module.FALLBACK_REVIEW_SYSTEM_PROMPT
    ).strip()
    prompt = loader()
    assert prompt != fallback, (
        f"{family}: адаптер загрузил fallback-заглушку вместо gate-reference "
        f"review-swarm/references/review-prompt.md"
    )


# ---------- HC-14: fallback-copy исключает secrets-материалы (R-Final P5) ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_hc14_fallback_copy_excludes_secrets(stub_env, family):
    """HC-14 (internal R-Final, P5): fallback-путь копирования (git admitted-files
    недоступен → rglob без .gitignore-фильтра) исключает secrets-материалы:
    каталог secrets/, файл .env и dot-файлы .secrets* не попадают в sandbox."""
    module = _load_adapter_module(family)
    assert "secrets" in module.DEFAULT_EXCLUDES
    assert ".env" in module.DEFAULT_EXCLUDES

    workdir = stub_env["cwd"]
    src = workdir / "payload"
    (src / "secrets").mkdir(parents=True)
    (src / "secrets" / "token.txt").write_text("s3cr3t", encoding="utf-8")
    (src / ".env").write_text("KEY=1", encoding="utf-8")
    (src / ".secrets.json").write_text("{}", encoding="utf-8")
    (src / "code.py").write_text("print(1)", encoding="utf-8")

    # workdir — не git-репозиторий: git_admitted_files → None → fallback rglob
    dst = workdir / "sandbox" / "payload"
    module.copy_path(src, dst, source_root=workdir)
    assert (dst / "code.py").is_file()
    assert not (dst / "secrets").exists()
    assert not (dst / ".env").exists()
    assert not (dst / ".secrets.json").exists()

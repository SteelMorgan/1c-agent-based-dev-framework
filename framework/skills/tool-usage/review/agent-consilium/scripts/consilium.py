#!/usr/bin/env python3
"""CLI-ядро консилиума: convene/round/status/verdict/close/doctor (CONS-01, TD 4).

Модератор — primary agent (LLM), не этот скрипт: скрипт контролирует формальные
инварианты протокола (стоп-условия, kill-прокси, кворум, анонимизация, дайджест-гейт,
парность cleanup) и отказывает в ходе, нарушающем протокол (fail-closed).

Runtime-данные (относительно cwd = repo root):
  .consilium-sessions/<session_id>/  — состояние и transcript сессии;
  .consilium-track-record/           — durable track record (FR-09), из cleanup исключён;
  .review-sandboxes/<review_id>/     — sandbox'ы участников (адаптеры cross-provider-review).
"""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from datetime import UTC, datetime
from pathlib import Path

# Общий слой review-harness (RVSW-01, TD §14.1): adapter_contract, track_record,
# liveness, progress — единый источник; вставка ДО собственного каталога scripts,
# чтобы `import adapter_contract` резолвился в harness-версию.
HARNESS_DIR = Path(__file__).resolve().parents[2] / "review-harness"
_HARNESS_SCRIPTS = HARNESS_DIR / "scripts"
if str(_HARNESS_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_HARNESS_SCRIPTS))

import adapter_contract as ac
import consilium_core as core
import consilium_watch as watch
import liveness
import progress as progress_tracker  # alias: в cmd_status есть локальная переменная progress
import track_record

SESSIONS_ROOT = Path(".consilium-sessions")
TRACK_ROOT = Path(".consilium-track-record")
DEFAULT_REGISTRY = HARNESS_DIR / "adapters.yaml"
DEFAULT_DOMAINS = HARNESS_DIR / "domains.yaml"

EXIT_PROTOCOL = 2      # fail-closed: нарушение протокола/кворума
EXIT_TERMINATED = 3    # сессия аварийно завершена (кворум/wall-clock)
EXIT_KILL_REJECTED = 4 # kill не по детерминированному кандидату
EXIT_CLEANUP_FAILED = 5  # cleanup fail-closed: участник не закрыт (F-01, FR-10)

STRUCTURED_INSTRUCTION = """
В конце ответа ОБЯЗАТЕЛЬНО приложи fenced-блок структурированного хода:
```consilium-structured
{"elements": ["E1", "E2"], "new_findings": [{"id": "F-01", "text": "..."}],
 "position_changes": [{"element": "E1 | M#:E1", "action": "agree|disagree|refine|withdraw", "refs": [1]}],
 "borrowed": [{"element": "E3", "source_ref": 2}],
 "risk_checklist_responses": [{"item_id": "<id пункта>", "verdict": "hit|clear|na", "note": "<текст>"}]}
```
- elements: стабильные id элементов твоей текущей модели;
- position_changes: по пунктам; чужие элементы адресуются как <anon_id>:<element>;
- borrowed: только с разрешимым source_ref на запись-источник (антинакрутка);
- risk_checklist_responses: ответы на ВСЕ применимые к этому типу хода пункты
  риск-чеклиста твоей роли; verdict строго lowercase hit|clear|na; для hit и na
  note обязателен (непустой после trim, не "-", >=3 символов); для clear note не нужен."""


class CliError(Exception):
    def __init__(self, message: str, code: int = EXIT_PROTOCOL):
        super().__init__(message)
        self.code = code


class TerminateSession(Exception):
    """Аварийное завершение сессии (кворум нарушен / wall-clock)."""


# ---------------------------------------------------------------------------
# Прогресс-чекпоинты (RVSW-01, FR-13, TD §5.6/§9.3) — границы фаз, не поток
# ---------------------------------------------------------------------------

CONSILIUM_CHECKPOINTS = (
    {"convened", "phase_a_complete", "phase_c_complete", "phase_d_complete",
     "verdict_ready", "closed"}
    | {f"round_{n}_complete" for n in range(1, 5)}  # потолок раундов фазы B = 4 (FR-02)
)


def _checkpoint(sdir: Path, session: dict, checkpoint: str, summary: str,
                counters: dict | None = None) -> None:
    """Чекпоинт прогресса на границе фазы/раунда (append-only progress.jsonl).
    Диагностика, не гейт: сбой записи прогресса НЕ останавливает протокол (FR-17)."""
    try:
        progress_tracker.append_checkpoint(
            sdir, tool="consilium", session_id=session["session_id"],
            checkpoint=checkpoint, summary=summary,
            counters=counters or {"invocations": int(session.get("invocation_count", 0))},
            allowed_checkpoints=CONSILIUM_CHECKPOINTS,
        )
    except Exception as exc:  # noqa: BLE001 — прогресс-контроль не рушит протокол
        print(f"ПРЕДУПРЕЖДЕНИЕ: чекпоинт прогресса {checkpoint!r} не записан: {exc}",
              file=sys.stderr)


# ---------------------------------------------------------------------------
# Состояние сессии
# ---------------------------------------------------------------------------

def session_dir(session_id: str) -> Path:
    return SESSIONS_ROOT / session_id


def load_session(sdir: Path) -> dict:
    return json.loads((sdir / "session.json").read_text(encoding="utf-8"))


def save_session(sdir: Path, session: dict) -> None:
    path = sdir / "session.json"
    # F-006 (CONS-06 rev): уникальное tmp-имя — конкурентные писатели не
    # публикуют смешанное содержимое через общий session.json.tmp; replace атомарен.
    tmp = path.with_name(f"session.json.tmp-{os.getpid()}-{uuid.uuid4().hex[:8]}")
    tmp.write_text(json.dumps(session, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)


@contextlib.contextmanager
def _session_lock(sdir: Path):
    """Межпроцессная блокировка session.json (F-002, CONS-06 rev): команда round
    удерживает flock от чтения до финального persist — конкурентная команда
    модератора не перезаписывает состояние старым снимком (lost update)."""
    with (sdir / "session.lock").open("a", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file, fcntl.LOCK_UN)


def make_session_id() -> str:
    return "cons-" + datetime.now(UTC).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]


def registry_map(registry: dict) -> dict:
    return {p["id"]: p for p in registry["participants"]}


def resolve_registry_path(cli_value: str | None, session: dict | None = None) -> str:
    if cli_value:
        return cli_value
    if session and session.get("registry"):
        return session["registry"]
    return str(DEFAULT_REGISTRY)


def load_registry_checked(path: str) -> dict:
    registry = core.load_registry(Path(path))
    errors = core.validate_registry(registry, base_dir=Path.cwd())
    if errors:
        raise CliError("adapters.yaml невалиден (fail-closed):\n" + "\n".join(f"  - {e}" for e in errors))
    return registry


# ---------------------------------------------------------------------------
# doctor (TD 4.6)
# ---------------------------------------------------------------------------

def _probe_member(member: dict, cwd: Path, timeout_sec: int = 60) -> str | None:
    """Живой probe участника (TD 4.6, --probe): 1 реальный invocation адаптера с
    тривиальным вопросом + парный close. Возвращает None при успехе, иначе причину.
    Стоимость: 1 вызов на участника — по умолчанию выключен."""
    adapter = Path(str(member["adapter"]))
    if not adapter.is_absolute():
        adapter = cwd / adapter
    probe_file = cwd / f".doctor-probe-{member['id']}.md"
    try:
        probe_file.write_text("# doctor probe\n", encoding="utf-8")
        result = ac.start_participant(
            str(adapter), "Probe: ответь одним словом «ok».", [probe_file.name],
            cwd, timeout_sec=timeout_sec, model=member.get("model"),
        )
        if result.ok and result.review_id:
            ac.close_participant(str(adapter), result.review_id, cwd)
        if not result.ok:
            return f"probe-вызов не прошёл ({result.kind}): {result.error or ''}".strip()
        return None
    except Exception as exc:  # noqa: BLE001 — probe не должен рушить doctor
        return f"probe-вызов завершился исключением: {exc!r}"
    finally:
        probe_file.unlink(missing_ok=True)


def run_doctor_checks(members: list[dict], probe: bool = False) -> dict:
    """Healthcheck членов реестра: CLI на PATH, адаптер отвечает на --help,
    family kimi — дополнительно `kimi doctor`. Недоступные исключаются с явной записью.
    probe=True — живой пробный вызов адаптера (TD 4.6, 1 invocation на участника)."""
    available: list[dict] = []
    excluded: list[dict] = []
    for member in members:
        reason = None
        failed_check = None
        if not shutil.which(str(member["cli"])):
            reason = f"CLI {member['cli']!r} не найден на PATH"
            failed_check = "cli_on_path"
        else:
            adapter = Path(str(member["adapter"]))
            if not adapter.is_absolute():
                adapter = Path.cwd() / adapter
            help_run = subprocess.run(
                [sys.executable, str(adapter), "--help"],
                capture_output=True, text=True, timeout=60, check=False,
            )
            if help_run.returncode != 0:
                reason = f"адаптер {member['adapter']} не отвечает на --help"
                failed_check = "adapter_help"
            elif member["family"] == "kimi":
                doctor = subprocess.run(
                    ["kimi", "doctor"], capture_output=True, text=True, timeout=120, check=False,
                )
                if doctor.returncode != 0:
                    reason = "kimi doctor: конфигурация CLI невалидна"
                    failed_check = "kimi_doctor"
        if reason is None and probe:
            probe_error = _probe_member(member, Path.cwd())
            if probe_error:
                reason = probe_error
                failed_check = "adapter_probe"
        if reason:
            excluded.append({"id": member["id"], "family": member["family"],
                             "reason": reason, "failed_check": failed_check})
        else:
            available.append(member)
    return {"available": available, "excluded": excluded}


def cmd_doctor(args: argparse.Namespace) -> int:
    registry = load_registry_checked(resolve_registry_path(args.registry))
    domains = load_domains_checked(getattr(args, 'domains', None))  # actionable-отказ (E-1)
    members = registry["participants"]
    report = run_doctor_checks(members, probe=args.probe)
    enabled_available = [m for m in report["available"] if m.get("enabled")]
    quorum = core.quorum_status(enabled_available)
    payload = {
        "members": [
            {"id": m["id"], "family": m["family"], "enabled": bool(m.get("enabled")),
             "check": "ok" if m in report["available"] else "excluded"}
            for m in members
        ],
        "excluded": report["excluded"],
        "quorum": quorum,
        "domains": {"available": [d["id"] for d in domains["domains"]],
                    "default": core.DEFAULT_DOMAIN},
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for member in payload["members"]:
            mark = "ok" if member["check"] == "ok" else "EXCLUDED"
            print(f"  {member['id']:<20} family={member['family']:<10} enabled={member['enabled']} → {mark}")
        for exc in report["excluded"]:
            print(f"  ИСКЛЮЧЁН: {exc['id']} — {exc['reason']} (check: {exc['failed_check']})")
        print(f"кворум: {'ok' if quorum['ok'] else 'НАРУШЕН'}; families: {quorum['families']}")
        if quorum["homogeneity_warning"]:
            print("ПРЕДУПРЕЖДЕНИЕ: однородность family — >1 участника одного семейства в составе")
    if not quorum["ok"]:
        print("doctor: кворум не выполнен (>=2 участников из >=2 family) — convene откажет", file=sys.stderr)
        return EXIT_PROTOCOL
    return 0


# ---------------------------------------------------------------------------
# Track record (FR-09) — слой хранения: harness track_record.py (TD §14.1)
# ---------------------------------------------------------------------------

def _read_track_config(track_dir: Path) -> dict:
    """config.json track record; отсутствует → {} (счётчики читаются с default 0)."""
    return track_record.read_track_config(track_dir)


def _read_observations(track_dir: Path) -> list[dict]:
    """Все observations каталога (observations.jsonl); отсутствует → []."""
    return track_record.read_observations(track_dir)


def _critique_stats(records: list[dict], participant_ids: list[str]) -> dict:
    """upheld/overruled critiques В РОЛИ критика (для observations)."""
    ordered = sorted((r for r in records if r.get("seq") is not None), key=lambda r: r["seq"])
    stats = {pid: {"critiques_upheld": 0, "critiques_overruled": 0} for pid in participant_ids}
    changes_by_author: dict[str, list[tuple[int, str, str]]] = {}
    attacks: list[tuple[int, str, str, str]] = []  # (seq, attacker, target, element)
    for record in ordered:
        author = record.get("author")
        for change in (record.get("structured") or {}).get("position_changes") or []:
            element = change.get("element") or ""
            owner, _, bare = element.partition(":")
            if not bare:
                owner, bare = None, owner
            if owner is None:
                changes_by_author.setdefault(author, []).append((record["seq"], element, change.get("action")))
            elif owner != author:
                attacks.append((record["seq"], author, owner, bare))
    for attack_seq, attacker, target, bare in attacks:
        later = [
            (seq, action) for seq, elem, action in changes_by_author.get(target, [])
            if (elem == bare or elem.endswith(":" + bare)) and seq > attack_seq
        ]
        if not later:
            continue  # атака без ответа — не засчитывается ни upheld, ни overruled
        if attacker not in stats:
            # CONS-06 E3: human-critic вне track-record — guard от KeyError
            # (author атаки может не входить в participant_ids).
            continue
        later.sort()
        if later[0][1] in {"agree", "withdraw"}:
            stats[attacker]["critiques_upheld"] += 1
        else:
            stats[attacker]["critiques_overruled"] += 1
    return stats


def write_track_record(cwd: Path, session: dict, records: list[dict], outcome: str) -> None:
    """Пишет observations (участник × роль × консилиум), регенерирует strengths.json,
    инкрементирует consiliums_completed (TD 3.4). Сборка observation — протокол
    консилиума; хранение — harness track_record (storage_dir=.consilium-track-record)."""
    track_dir = cwd / TRACK_ROOT
    track_dir.mkdir(parents=True, exist_ok=True)
    config = _read_track_config(track_dir)
    consilium_seq = int(config.get("consiliums_completed", 0)) + 1

    participant_ids = [p["id"] for p in session["participants"]]
    metrics = core.compute_model_metrics(
        records, participant_ids, excluded_attack_authors=_human_excluded_authors(session))
    critique_stats = _critique_stats(records, participant_ids)
    moderator_id = session.get("moderator_id") or "primary"

    today = datetime.now(UTC).date().isoformat()
    observations: list[dict] = []
    for participant in session["participants"]:
        pid = participant["id"]
        own_withdrawn = 0
        for record in records:
            if record.get("author") != pid:
                continue
            for change in (record.get("structured") or {}).get("position_changes") or []:
                if change.get("action") == "withdraw":
                    own_withdrawn += 1
        roles = sorted(set((participant.get("roles") or {}).values()))
        default_role = (session.get("domain_snapshot", {}).get("catalog", {})
                        .get("roles", [{"id": "architecture"}])[0]["id"])
        a_role = (participant.get("roles") or {}).get("A")
        for role in roles or [default_role]:
            observation = {
                "consilium_id": session["session_id"],
                "consilium_seq": consilium_seq,
                "date": today,
                "participant_id": pid,
                "family": participant["family"],
                "role": role,
                "moderator_id": moderator_id,
                "moderator_is_participant": moderator_id in participant_ids,
                "findings_accepted": metrics.get(pid, {}).get("borrowed_in", 0),
                "findings_withdrawn": own_withdrawn,
                "critiques_upheld": critique_stats[pid]["critiques_upheld"],
                "critiques_overruled": critique_stats[pid]["critiques_overruled"],
                "model_killed": participant["state"] == "killed",
                "unresponsive_events": 1 if participant["state"] == "unresponsive" else 0,
                "outcome": outcome,
                "phase_d_skipped": bool((session.get("phase_d_skip") or {}).get("skipped", False)),
                "phase_d_saved_invocations": int((session.get("phase_d_skip") or {}).get("saved_invocations", 0)),
                "domain": session.get("domain", "architecture"),
            }
            # F-03: checklist_stats — один раз на участника (на A-роли), без double-count
            if role == a_role or a_role is None:
                observation["checklist_stats"] = core.checklist_stats(records, pid)
            observations.append(observation)

    track_record.append_observations(track_dir, observations)
    # strengths.json — генерируемый кэш/отчёт из observations (seq_field=consilium_seq)
    track_record.regenerate_strengths(track_dir)
    track_record.bump_counter(track_dir, "consiliums_completed")


def _load_strengths_for_assignment(cwd: Path, domain: str | None = None) -> tuple[dict, dict]:
    """Strengths для назначения ролей — ВСЕГДА пересчёт из observations.jsonl (F-04,
    FR-09): источник истины только observations; strengths.json — генерируемый
    кэш/отчёт, на чтении не доверяется (подмена файла не влияет на назначение).
    Домен-фильтр (CONS-03 E-2): observations без поля domain трактуются как architecture."""
    observations = [
        obs for obs in _read_observations(cwd / TRACK_ROOT)
        if domain is None or obs.get("domain", core.DEFAULT_DOMAIN) == domain
    ]
    computed = core.compute_strengths(observations)
    strengths: dict = {}
    n_eff: dict = {}
    for key, entry in computed.items():
        strengths[key] = entry["score"]
        n_eff[key] = entry["n_eff"]
    return strengths, n_eff


def _last_roles(cwd: Path, domain: str | None = None) -> dict:
    """Последняя роль каждого участника по track record (запрет повтора, FR-08);
    домен-фильтр (CONS-03 E-2): кросс-доменная история не влияет."""
    last: dict[str, tuple[int, str]] = {}
    for obs in _read_observations(cwd / TRACK_ROOT):
        if domain and obs.get("domain", core.DEFAULT_DOMAIN) != domain:
            continue
        pid = obs["participant_id"]
        seq = int(obs.get("consilium_seq", 0))
        if pid not in last or seq >= last[pid][0]:
            last[pid] = (seq, obs["role"])
    return {pid: role for pid, (seq, role) in last.items()}


def _role_history(cwd: Path, participant_id: str, domain: str | None = None) -> list[tuple[str, int]]:
    return [
        (obs["role"], int(obs.get("consilium_seq", 0)))
        for obs in _read_observations(cwd / TRACK_ROOT)
        if obs["participant_id"] == participant_id
        and (domain is None or obs.get("domain", core.DEFAULT_DOMAIN) == domain)
    ]


# ---------------------------------------------------------------------------
# Доменные пакеты (CONS-03)
# ---------------------------------------------------------------------------

def load_domains_checked(path: str | None = None) -> dict:
    """Загрузка domains.yaml с actionable-отказом (E-1); --domains переопределяет путь."""
    try:
        return core.load_domains(Path(path) if path else DEFAULT_DOMAINS)
    except core.ProtocolError as error:
        raise CliError(str(error)) from error


def _role_ids(session: dict) -> list[str]:
    return [r["id"] for r in session["domain_snapshot"]["catalog"]["roles"]]


def _role_by_id(session: dict, role_id: str | None) -> dict | None:
    for role in session["domain_snapshot"]["catalog"]["roles"]:
        if role["id"] == role_id:
            return role
    return None


def _role_block(session: dict, role_id: str | None, wave: str) -> str:
    """Блок роли для промпта участника (E-3): title + lens + применимый к волне
    риск-чеклист + evidence_requirements домена."""
    role = _role_by_id(session, role_id)
    if not role:
        return ""
    lines = [f"Твоя роль: {role['title']}. Линза: {role['lens']}"]
    items = core.applicable_checklist_items(role, wave)
    if items:
        lines.append("Риск-чеклист домена (ответь по КАЖДОМУ пункту в "
                     "structured.risk_checklist_responses):")
        lines.extend(f"- [{item['item_id']}] {item['text']}" for item in items)
    evidence = session["domain_snapshot"]["catalog"].get("evidence_requirements") or []
    if evidence:
        lines.append("Evidence: " + "; ".join(str(e) for e in evidence) + ".")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Human-critic режим (CONS-06, E1–E13): session-scoped сущность уровня
# transcript; НЕ participant реестра и НЕ session.participants (E1).
# ---------------------------------------------------------------------------

def _human(session: dict) -> dict:
    return session.get("human_critic") or {}


def _human_active(session: dict) -> bool:
    """Режим включён и не свёрнут (--human-withdraw)."""
    hc = _human(session)
    return bool(hc.get("enabled")) and not hc.get("withdrawn")


def _human_excluded_authors(session: dict) -> set | None:
    """Авторы, исключаемые из kill-метрик и стоп-предикатов (E3/E4)."""
    if _human(session).get("enabled"):
        return {core.HUMAN_CRITIC_ID}
    return None


def _human_awaiting_delta(session: dict, now: datetime) -> float:
    """Текущая (незакрытая) пауза ожидания human-хода, сек."""
    since = _human(session).get("awaiting_since")
    if not since:
        return 0.0
    return max(0.0, (now - datetime.fromisoformat(since)).total_seconds())


def _human_paused_total(session: dict, now: datetime | None = None) -> float:
    """Накопленная пауза wall-clock (E5): persisted paused_sec + текущее ожидание."""
    now = now or datetime.now(UTC)
    wall = session.get("wall_clock") or {}
    return float(wall.get("paused_sec") or 0.0) + _human_awaiting_delta(session, now)


def _human_close_pause(session: dict) -> float:
    """Коммитит текущее ожидание в persisted wall_clock.paused_sec (E5;
    восстановимо из system-записей transcript; started_at НЕ сдвигается)."""
    now = datetime.now(UTC)
    delta = _human_awaiting_delta(session, now)
    if delta > 0:
        wall = session.setdefault("wall_clock", {})
        wall["paused_sec"] = round(float(wall.get("paused_sec") or 0.0) + delta, 3)
    if "human_critic" in session:
        session["human_critic"]["awaiting_since"] = None
    return delta


def _human_system(sdir: Path, session: dict, content: str) -> None:
    """System-запись human-critic события в transcript (E6: каждое решение модератора)."""
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": session.get("phase"),
        "round": session.get("round", 0), "wave": session.get("wave"),
        "author": "moderator", "type": "system", "content": content,
    })


def _precheck_wave_input(sdir: Path, session: dict, args: argparse.Namespace,
                        registry: dict) -> None:
    """F-004/F-009/F-014 (CONS-06 rev): валидации входа волны ДО коммита human-хода —
    тот же digest-lint и context_budget полного промпта, что применит _run_wave.
    Отклонение здесь оставляет transcript и session.json нетронутыми."""
    if not args.digest_file:
        raise CliError("подача human-хода требует --digest-file (LLM-волна следует "
                       "за human-ходом в том же round, E7)")
    text = Path(args.digest_file).read_text(encoding="utf-8")
    real_ids = [p["id"] for p in session["participants"]]
    if _human(session).get("enabled"):
        real_ids.append(core.HUMAN_CRITIC_ID)
    entries = registry_map(registry)
    budget = min(entries[p["id"]].get("context_budget", 120000) for p in session["participants"])
    violations = core.lint_digest(text, real_ids, budget)
    if violations:
        raise CliError("дайджест отклонён (fail-closed, precheck human-хода):\n"
                       + "\n".join(f"  - {v}" for v in violations))
    wave = session["wave"]
    bundle = _prompt_bundle(sdir, session, wave) if wave in ("attack", "response") else ""
    if wave == "redteam":
        turn_participants = [p for p in session["participants"] if p["id"] == session.get("redteamer")]
    else:
        turn_participants = [p for p in session["participants"] if p["state"] == "active"]
    for participant in turn_participants:
        prompt = _build_prompt(session, participant, wave, text, bundle)
        tokens = core.estimate_tokens(prompt)
        budget = int(entries[participant["id"]].get("context_budget", 120000))
        if tokens > budget:
            raise CliError(
                f"полный вход участника {participant['id']} превышает context_budget: "
                f"{tokens} > {budget} токенов (NFR-02, F-014) — human-ход НЕ зафиксирован")


def _apply_human_gate(sdir: Path, session: dict, args: argparse.Namespace,
                      registry: dict) -> str | None:
    """Гейт human-critic режима в точке входа cmd_round (E2/E6/E7, fail-closed).

    Возвращает None — волна исполняется штатно; строку — сессия ждёт
    (сообщение модератору, round не исполняется). CliError — отказ протокола.

    Порядок (E7, антиякорение): на атакующей волне ядро ждёт human-ход ДО
    запуска LLM-волны; поданный --human-turn-file записывается первым и
    попадает в bundle LLM-участников этой волны."""
    turn_file = getattr(args, "human_turn_file", None)
    continue_wait = getattr(args, "human_continue_wait", False)
    skip_wave = getattr(args, "human_skip_wave", False)
    withdraw = getattr(args, "human_withdraw", False)
    keep_raw = getattr(args, "human_keep_raw", False)
    reason_arg = getattr(args, "reason", None)
    # F-012 (CONS-06 rev): действия взаимоисключающие — ровно одно за команду.
    actions = sum(1 for flag in (turn_file, continue_wait, skip_wave, withdraw) if flag)
    if actions > 1:
        raise CliError(
            "human-флаги взаимоисключающие (F-012): подайте ровно одно действие за round — "
            "--human-turn-file | --human-continue-wait | --human-skip-wave | --human-withdraw")
    if keep_raw and not turn_file:
        raise CliError("--human-keep-raw применим только вместе с --human-turn-file (E9)")
    human_flags = bool(actions or keep_raw)
    hc = session.get("human_critic")
    if not hc or not hc.get("enabled"):
        if human_flags:
            raise CliError("human-critic режим не включён при convene (--human-critic) — "
                           "human-флаги round недопустимы (fail-closed)")
        return None

    phase = session.get("phase")
    wave = session.get("wave")
    round_no = int(session.get("round", 0))

    # Решение модератора: свёртывание режима (E6).
    if withdraw:
        reason = (reason_arg or "").strip()
        if not reason:
            raise CliError("--human-withdraw требует --reason \"...\" (E6: явная причина)")
        if hc.get("withdrawn"):
            raise CliError("human-critic режим уже свёрнут ранее")
        _human_close_pause(session)
        hc["withdrawn"] = True
        hc["withdraw_reason"] = reason
        hc["state"] = "withdrawn"
        _human_system(sdir, session,
                      f"human-critic: режим свёрнут модератором (--human-withdraw), "
                      f"причина: {reason}; сессия продолжается без человека (E6)")
        print(f"human-critic режим свёрнут: {reason}")
        return None
    if hc.get("withdrawn"):
        if human_flags:
            raise CliError("human-critic режим свёрнут (--human-withdraw) — "
                           "human-флаги round недопустимы")
        return None

    applicable = core.human_turn_wave_allowed(phase, wave)
    if turn_file and not applicable:
        raise CliError(
            f"human-ход привязан только к атакующим волнам (attack фазы B, redteam фазы D), "
            f"текущая: {phase}/{wave or '—'} (fail-closed, E2)")

    state = hc.get("state") or "idle"

    # Решение модератора: продление ожидания новым ограниченным интервалом (E6).
    if continue_wait:
        if state not in ("awaiting_human", "awaiting_moderator_decision"):
            raise CliError("--human-continue-wait допустим только в ожидании human-хода")
        until = datetime.now(UTC).timestamp() + float(hc.get("wait_cap_sec") or 0)
        hc["wait_extended_until"] = datetime.fromtimestamp(until, UTC).isoformat()
        hc["state"] = "awaiting_human"
        _human_system(sdir, session,
                      f"human-critic: модератор продлил ожидание (--human-continue-wait) "
                      f"до {hc['wait_extended_until']} (новый ограниченный интервал "
                      f"{hc.get('wait_cap_sec')}s, E6)")
        if not turn_file:
            return (f"ожидание human-хода продлено до {hc['wait_extended_until']}; "
                    f"волна {phase}/{wave} не запущена")

    # Решение модератора: одноразовый пропуск текущей волны (E6).
    wave_key = f"{phase}:{round_no}:{wave}"
    if skip_wave:
        if not applicable:
            raise CliError(f"--human-skip-wave допустим только на атакующей волне, "
                           f"текущая: {phase}/{wave or '—'} (E2)")
        if core.wave_has_human_turn(core.read_transcript(sdir), phase, round_no, wave):
            raise CliError("human-ход на эту волну уже зафиксирован — skip неприменим (E2)")
        _human_close_pause(session)
        hc.setdefault("skipped_waves", []).append(wave_key)
        hc["state"] = "idle"
        hc["wait_extended_until"] = None
        _human_system(sdir, session,
                      f"human-critic: модератор пропустил волну {wave_key} "
                      f"(--human-skip-wave, одноразово, E6)")
        print(f"human-ход пропущен для волны {wave_key} (одноразово)")
        return None

    if not applicable:
        return None
    if wave_key in (hc.get("skipped_waves") or []):
        return None  # одноразовый skip уже применён к этой волне

    records = core.read_transcript(sdir)
    if core.wave_has_human_turn(records, phase, round_no, wave):
        if turn_file:
            raise CliError(f"human-ход на волну {wave_key} уже зафиксирован — "
                           f"максимум 1 human-ход на волну (E2)")
        # F-001 (CONS-06 rev): resume-путь после зафиксированного хода тоже
        # закрывает паузу — awaiting_since не должен течь дальше.
        if hc.get("awaiting_since"):
            _human_close_pause(session)
            hc["state"] = "idle"
            hc["wait_extended_until"] = None
        return None

    # Подача human-хода файлом (E2/E9), атомарно связанного с текущей волной.
    if turn_file:
        turn_path = Path(turn_file)
        try:
            payload = json.loads(turn_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as read_error:
            raise CliError(f"human turn-файл не читается как JSON: {read_error}") from read_error
        protected_ids = [p["id"] for p in session["participants"]] + [core.HUMAN_CRITIC_ID]
        try:
            turn = core.validate_human_turn(payload, protected_ids)
        except core.ProtocolError as validation_error:
            raise CliError(str(validation_error)) from validation_error
        # F-004/F-009/F-014 (CONS-06 rev): ВСЕ валидации входа волны до коммита —
        # human-ход не коммитится, если дайджест/промпт будут отклонены.
        _precheck_wave_input(sdir, session, args, registry)
        _human_close_pause(session)
        hc["state"] = "idle"
        hc["wait_extended_until"] = None
        anon_map = json.loads((sdir / "anon_map.json").read_text(encoding="utf-8"))
        try:
            record = core.append_human_turn(sdir, {
                "session_id": session["session_id"], "phase": phase, "round": round_no,
                "wave": wave, "author": core.HUMAN_CRITIC_ID,
                "anon_id": anon_map[core.HUMAN_CRITIC_ID],
                "type": core.HUMAN_TURN_TYPES[wave],
                "refs": turn["refs"],
                "content": turn["content"],
                "structured": turn["structured"],
            }, phase, round_no, wave)
        except core.ProtocolError as atomic_error:
            raise CliError(str(atomic_error)) from atomic_error
        hc.setdefault("turns", []).append(record["seq"])
        _human_system(sdir, session,
                      f"human-critic: ход принят для волны {wave_key} (seq:{record['seq']}); "
                      f"аттестация human_approved проставлена модератором — audit-функция, "
                      f"не удостоверение личности источника (E2)")
        if keep_raw:
            # Raw-сайдкар — forensic-опция, не дефолт (E9).
            raw_dir = sdir / "human"
            raw_dir.mkdir(exist_ok=True)
            shutil.copy2(turn_path, raw_dir / f"turn-{record['seq']}.json")
        print(f"human-ход зафиксирован для волны {wave_key} (seq:{record['seq']}); "
              f"запускается LLM-волна")
        return None  # LLM-волна следует за human-ходом (E7)

    # Ожидание human-хода (E7): LLM-волна НЕ запускается.
    now = datetime.now(UTC)
    if not hc.get("awaiting_since"):
        hc["awaiting_since"] = now.isoformat()
        hc["state"] = "awaiting_human"
        _human_system(sdir, session,
                      f"human-critic: ожидание human-хода для волны {wave_key} "
                      f"(awaiting_human, E7: человек ходит до LLM-волны; кап ожидания "
                      f"{hc.get('wait_cap_sec')}s, E6)")
        return (f"awaiting_human: ожидание human-хода для волны {wave_key}; подайте "
                f"round {session['session_id']} --human-turn-file <path> "
                f"(кап ожидания {hc.get('wait_cap_sec')}s)")

    # Wait-cap (E6): молчаливого продолжения нет — требуется явное решение.
    since = datetime.fromisoformat(hc["awaiting_since"])
    deadline = since.timestamp() + float(hc.get("wait_cap_sec") or 0)
    extended = hc.get("wait_extended_until")
    if extended:
        deadline = max(deadline, datetime.fromisoformat(extended).timestamp())
    if now.timestamp() >= deadline:
        if state != "awaiting_moderator_decision":
            hc["state"] = "awaiting_moderator_decision"
            _human_system(sdir, session,
                          f"human-critic: кап ожидания исчерпан для волны {wave_key} "
                          f"(awaiting_moderator_decision, E6) — требуется явное решение "
                          f"модератора: --human-continue-wait | --human-skip-wave | "
                          f"--human-withdraw --reason | --human-turn-file")
        raise CliError(
            f"сессия в awaiting_moderator_decision (wait-cap ожидания human-хода исчерпан, "
            f"волна {wave_key}): round не исполняется до явного решения модератора — "
            f"--human-continue-wait | --human-skip-wave | --human-withdraw --reason \"...\" "
            f"| --human-turn-file <path> (E6)")
    return (f"awaiting_human: ожидание human-хода для волны {wave_key} "
            f"(ждём {int(_human_awaiting_delta(session, now))}s из капа "
            f"{hc.get('wait_cap_sec')}s); подайте --human-turn-file <path>")


# ---------------------------------------------------------------------------
# convene (FR-06)
# ---------------------------------------------------------------------------

def cmd_convene(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    registry = load_registry_checked(resolve_registry_path(args.registry))
    domains = load_domains_checked(getattr(args, 'domains', None))
    domain_id = args.domain or core.DEFAULT_DOMAIN
    domain = core.domain_by_id(domains, domain_id)
    if domain is None:
        available_ids = [d["id"] for d in domains["domains"]]
        raise CliError(
            f"неизвестный домен: {domain_id!r} (fail-closed). Доступные: {available_ids}"
        )
    # Правило композиции (FR-05): состав ВСЕГДА = все enabled-участники реестра;
    # флага --members не существует (точка, не опция).
    members = [p for p in registry["participants"] if p.get("enabled")]

    report = run_doctor_checks(members, probe=args.probe)
    available = report["available"]
    quorum = core.quorum_status(available)
    if not quorum["ok"]:
        for exc in report["excluded"]:
            print(f"ИСКЛЮЧЁН: {exc['id']} — {exc['reason']} (check: {exc['failed_check']})")
        raise CliError(
            f"кворум не выполнен после исключений: {len(available)} участников, "
            f"families {quorum['families']} (нужно >=2 участников из >=2 family) — convene отказывает"
        )

    track_dir = cwd / TRACK_ROOT
    exploration = core.is_exploration_consilium(
        int(_read_track_config(track_dir).get("consiliums_completed", 0))
    )
    strengths, n_eff = _load_strengths_for_assignment(cwd, domain=domain_id)
    last_roles = _last_roles(cwd, domain=domain_id)
    participant_ids = [p["id"] for p in available]
    catalog_role_ids = [r["id"] for r in domain["roles"]]
    roles = core.assign_roles(participant_ids, strengths=strengths, n_eff=n_eff,
                              exploration=exploration, last_roles=last_roles,
                              role_catalog=catalog_role_ids)

    session_id = make_session_id()
    sdir = cwd / session_dir(session_id)
    (sdir / "digests").mkdir(parents=True)
    (sdir / "bundles").mkdir()
    (sdir / "participants").mkdir()

    anon_ids = list(participant_ids)
    if args.human_critic:
        # E2: presentation-id human-critic — из общего пространства create_anon_map;
        # в session.participants НЕ добавляется (E1).
        anon_ids.append(core.HUMAN_CRITIC_ID)
    anon_map = core.create_anon_map(anon_ids)
    (sdir / "anon_map.json").write_text(
        json.dumps(anon_map, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    paths = list(args.paths or [])
    if not paths:
        # NFR-06: focused-paths обязательны; без явных paths — только файл вопроса
        question_path = sdir / "question.md"
        question_path.write_text(f"# Вопрос консилиума\n\n{args.question}\n", encoding="utf-8")
        paths = [str(session_dir(session_id) / "question.md")]
    paths = _resolve_focused_paths(paths, cwd)  # F-02: symlink-зеркала → реальные пути

    caller_id = args.caller or "primary"
    session = {
        "session_id": session_id,
        "registry": str(Path(args.registry or DEFAULT_REGISTRY).resolve()),
        "question": args.question,
        "created_at": core.utc_now(),
        "moderator_id": caller_id,
        "phase": "A",
        "round": 0,
        "wave": "proposal",
        "paths": paths,
        "timeout_sec": args.timeout_sec,
        "silence_threshold_sec": args.silence_threshold_sec,
        "exploration": exploration,
        "domain": domain_id,
        "domain_snapshot": {
            "domain": domain_id,
            "version": domains.get("version"),
            "content_hash": hashlib.sha256(
                DEFAULT_DOMAINS.read_bytes()).hexdigest()[:16],
            "catalog": domain,
        },
        "participants": [
            {
                "id": p["id"],
                "family": p["family"],
                "review_id": None,
                "state": "active",
                "killed_at_round": None,
                "roles": {"A": roles[p["id"]]},
                "invocations": 0,
                "retries": 0,
                "caller_model": p["id"] == caller_id,
            }
            for p in available
        ],
        "quorum": {**quorum, "excluded": report["excluded"]},
        "stop_state": {"stalemate_counter": 0, "rounds_completed_B": 0,
                       "last_round_had_new_findings": False,
                       "last_round_had_position_changes": False,
                       "stop_reason": None, "freeze_in_round": False},
        "kill_log": [],
        "redteamer": None,
        "wave_counter": 0,
        "invocation_count": 0,
        "invocation_budget": {"default_max": core.INVOCATION_WARN_MAX, "hard_max": core.INVOCATION_HARD_MAX},
        "wall_clock": {
            "started_at": core.utc_now(),
            "budget_sec": core.wall_clock_budget(args.timeout_sec)[0],
            "warn_at_sec": core.wall_clock_budget(args.timeout_sec)[1],
        },
        "cleanup": {"session_closed": False, "participants_closed": {}},
    }
    if args.human_critic:
        session["wall_clock"]["paused_sec"] = 0.0  # E5: пауза ожидания human-хода
        session["human_critic"] = {
            "enabled": True,
            "wait_cap_sec": args.human_wait_cap_sec,
            "state": "idle",
            "awaiting_since": None,
            "wait_extended_until": None,
            "skipped_waves": [],
            "withdrawn": False,
            "withdraw_reason": None,
            "turns": [],
        }
    save_session(sdir, session)
    core.append_record(sdir, {
        "session_id": session_id, "phase": "A", "round": 0, "wave": None,
        "author": "moderator", "type": "system",
        "content": f"convene: состав {[p['id'] for p in available]}; caller={session['moderator_id']}; "
                   f"исключены doctor: {[e['id'] for e in report['excluded']]}; "
                   f"exploration={exploration}; роли фазы A: {roles}"
                   + (f"; human-critic: включён (CONS-06 E1, атакующие волны B/D, "
                      f"presentation-id {anon_map[core.HUMAN_CRITIC_ID]}, "
                      f"wait-cap {args.human_wait_cap_sec}s)" if args.human_critic else ""),
    })

    # Правило композиции (FR-05): caller, совпадающий с участником реестра, —
    # модель вызывающего рядовым членом (caller_model). Caller вне реестра —
    # легитимный модератор без адаптера: warning, convene продолжается.
    caller_in_registry = any(p["id"] == caller_id for p in registry["participants"])
    if not caller_in_registry and caller_id != "primary":
        warning = (f"ПРЕДУПРЕЖДЕНИЕ: caller {caller_id!r} не представлен в реестре — "
                   f"модель вызывающего участвует только как модератор (FR-05)")
        print(warning)
        core.append_record(sdir, {
            "session_id": session_id, "phase": "A", "round": 0, "wave": None,
            "author": "moderator", "type": "system", "content": warning,
        })

    _checkpoint(sdir, session, "convened",
                f"консилиум созван: {len(participant_ids)} участников, "
                f"families {quorum['families']}, домен {domain_id}",
                counters={"invocations": 0, "participants": len(participant_ids),
                          "excluded": len(report["excluded"])})

    print(f"session_id: {session_id}")
    print(f"участники: {', '.join(participant_ids)}")
    for pid in participant_ids:
        print(f"  роль фазы A: {pid} → {roles[pid]}")
    for exc in report["excluded"]:
        print(f"  исключён doctor: {exc['id']} — {exc['reason']}")
    if quorum["homogeneity_warning"]:
        print("ПРЕДУПРЕЖДЕНИЕ: однородность family в составе (flag переносится в вердикт)")
    if exploration:
        print("exploration-консилиум: роли назначены round-robin вразрез статистике (TD 3.4)")
    if args.human_critic:
        print(f"human-critic режим включён (CONS-06): человек ходит в атакующих волнах "
              f"B/D под presentation-id {anon_map[core.HUMAN_CRITIC_ID]}; "
              f"плейбук — references/human-critic-playbook.md")
    return 0


# ---------------------------------------------------------------------------
# Волны (TD 4.1, 7)
# ---------------------------------------------------------------------------

def _require_digest(args: argparse.Namespace, sdir: Path, session: dict) -> str:
    """Дайджест-гейт (NFR-02): волна без дайджеста не запускается; lint — без
    реальных id и в пределах min context_budget (fail-closed)."""
    if not args.digest_file:
        raise CliError("волна требует --digest-file (дайджест модератора, NFR-02): отказ")
    text = Path(args.digest_file).read_text(encoding="utf-8")
    real_ids = [p["id"] for p in session["participants"]]
    if _human(session).get("enabled"):
        # CONS-06 E9: запрещённый словарь расширяется ТОЛЬКО exact protected
        # identifier человека (внутренний id записи), не широкими маркерами.
        real_ids.append(core.HUMAN_CRITIC_ID)
    budget = min(p.get("context_budget", 120000) for p in session["_registry_entries"])
    violations = core.lint_digest(text, real_ids, budget)
    if violations:
        raise CliError("дайджест отклонён (fail-closed):\n" + "\n".join(f"  - {v}" for v in violations))
    session["wave_counter"] = int(session.get("wave_counter", 0)) + 1
    (sdir / "digests" / f"wave-{session['wave_counter']}.md").write_text(text, encoding="utf-8")
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": session["phase"],
        "round": session["round"], "wave": session["wave"],
        "author": "moderator", "type": "digest", "content": text,
    })
    return text


def _wave_bundle(sdir: Path, session: dict, records: list[dict], phase: str) -> str:
    """Рендер bundle только что сыгранной волны. Bundle — выдача УЧАСТНИКАМ,
    поэтому анонимизируется всегда (F-01: фаза A тоже подаётся в фазу B)."""
    wave_records = [
        r for r in records
        if r.get("wave") == session.get("_last_wave") and r.get("round") == session.get("_last_round")
        and r.get("type") in core.PARTICIPANT_TYPES
    ]
    real_ids = [p["id"] for p in session["participants"]]
    anon_map = json.loads((sdir / "anon_map.json").read_text(encoding="utf-8"))
    rendered = core.render_bundle(wave_records, anon_map)
    counter = int(session.get("wave_counter", 0))
    (sdir / "bundles" / f"wave-{counter}.bundle.md").write_text(rendered, encoding="utf-8")
    return rendered


def _prompt_bundle(sdir: Path, session: dict, wave: str) -> str:
    """Bundle, подаваемый в промпт волны attack/response (F-01): рендерится на лету
    из transcript и ВСЕГДА анонимизирован (включая proposals фазы A в раунде 1)."""
    records = core.read_transcript(sdir)
    if wave == "attack":
        if int(session.get("round", 1)) <= 1:
            source = [r for r in records if r.get("phase") == "A" and r.get("type") == "proposal"]
        else:
            source = [
                r for r in records
                if r.get("phase") == "B" and r.get("type") == "response"
                and r.get("round") == session["round"] - 1
            ]
    elif wave == "response":
        source = [
            r for r in records
            if r.get("phase") == "B" and r.get("type") == "attack"
            and r.get("round") == session["round"]
        ]
    else:
        source = []
    real_ids = [p["id"] for p in session["participants"]]
    anon_map = json.loads((sdir / "anon_map.json").read_text(encoding="utf-8"))
    return core.render_bundle(source, anon_map)


def _build_prompt(session: dict, participant: dict, wave: str, digest: str, bundle: str) -> str:
    question = session["question"]
    role = participant["roles"].get("A") or _role_ids(session)[0]
    role_block = _role_block(session, role, wave)
    if wave == "proposal":
        return (
            f"Архитектурный вопрос консилиума:\n{question}\n\n"
            f"{role_block}\n"
            "Фаза A (дивергенция): выдвинь свою версию решения независимо и вслепую — "
            "без оглядки на чужие позиции.\n" + STRUCTURED_INSTRUCTION
        )
    if wave == "attack":
        return (
            f"Дайджест модератора:\n{digest}\n\n"
            f"Анонимизированные позиции (bundle):\n{bundle}\n\n"
            f"Фаза B, раунд {session['round']}. {role_block}\n"
            "Атакуй слабые элементы чужих моделей по существу. Заимствование чужих решений "
            "разрешено и поощряется — оформляй через borrowed с source_ref.\n" + STRUCTURED_INSTRUCTION
        )
    if wave == "response":
        return (
            f"Дайджест модератора:\n{digest}\n\n"
            f"Анонимизированные атаки (bundle):\n{bundle}\n\n"
            f"Фаза B, раунд {session['round']}. {role_block}\n"
            "Ответь на критику своей модели: по каждому пункту agree/disagree/refine/withdraw. "
            "Заимствование разрешено.\n" + STRUCTURED_INSTRUCTION
        )
    if wave == "redteam":
        synthesis = session.get("synthesis_text", "")
        role_d = participant["roles"].get("D") or role
        return (
            f"Дайджест модератора:\n{digest}\n\n"
            f"Синтез модератора (фаза C):\n{synthesis}\n\n"
            f"Фаза D (ред-тим), роль по кросс-ротации. {_role_block(session, role_d, wave)}\n"
            "Атакуй склейку: найди дефекты синтеза, потерянные элементы, внутренние противоречия.\n"
            + STRUCTURED_INSTRUCTION
        )
    if wave == "confirmation":
        synthesis = session.get("synthesis_text", "")
        role_d = participant["roles"].get("D") or role
        return (
            f"Дайджест модератора:\n{digest}\n\n"
            f"Синтез модератора (фаза C):\n{synthesis}\n\n"
            f"Фаза D (подтверждение вклада), роль по кросс-ротации. {_role_block(session, role_d, wave)}\n"
            "Подтверди или оспорь неискажённость твоего вклада в синтез "
            "(position_changes: agree/disagree по элементам синтеза).\n" + STRUCTURED_INSTRUCTION
        )
    if wave == "final_statement":
        return (
            f"Дайджест модератора:\n{digest}\n\n"
            "Твоя модель исключена детерминированным критерием FR-03 (значения прокси-метрик — "
            "в kill-log). Сформулируй финальное заявление: оно ДОСЛОВНО войдёт в minority report.\n"
            + STRUCTURED_INSTRUCTION
        )
    raise CliError(f"неизвестная волна: {wave}")


def _mark_unresponsive(sdir: Path, session: dict, participant: dict, result: ac.InvocationResult) -> None:
    participant["state"] = "unresponsive"
    session["stop_state"]["freeze_in_round"] = True
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": session["phase"],
        "round": session["round"], "wave": session["wave"],
        "author": "moderator", "type": "error",
        "content": f"участник {participant['id']} → unresponsive ({result.kind}); "
                   f"attempts={result.attempts}; {result.error or ''}",
    })


def _check_quorum_or_terminate(session: dict) -> None:
    active = [p for p in session["participants"] if p["state"] == "active"]
    quorum = core.quorum_status(active)
    if not quorum["ok"]:
        raise TerminateSession(
            f"кворум нарушен после исключения unresponsive: активных {len(active)}, "
            f"families {quorum['families']} — сессия завершается без вердикта (FR-11)"
        )


def _run_wave(sdir: Path, session: dict, wave: str, registry: dict,
               digest_text: str | None = None) -> None:
    """Исполняет одну волну: digest-гейт → параллельные вызовы адаптеров →
    записи transcript → unresponsive-политика → проверка кворума.
    digest_text — уже записанный дайджест (kill-путь, FU-01: без повторной записи)."""
    cwd = Path.cwd()
    registry_entries = registry_map(registry)
    session["_registry_entries"] = [
        {**p, "context_budget": registry_entries[p["id"]].get("context_budget", 120000)}
        for p in session["participants"]
    ]

    digest = digest_text or ""
    if wave != "proposal" and digest_text is None:
        digest = _require_digest(_CURRENT_ARGS, sdir, session)

    bundle = ""
    if wave in ("attack", "response"):
        bundle = _prompt_bundle(sdir, session, wave)

    if wave == "proposal":
        turn_participants = [p for p in session["participants"] if p["state"] == "active"]
    elif wave == "redteam":
        turn_participants = [p for p in session["participants"] if p["id"] == session.get("redteamer")]
    elif wave == "final_statement":
        turn_participants = [p for p in session["participants"] if p["id"] == session.get("_kill_target")]
    else:
        turn_participants = [p for p in session["participants"] if p["state"] == "active"]

    tasks = []
    for participant in turn_participants:
        entry = registry_entries[participant["id"]]
        prompt = _build_prompt(session, participant, wave, digest, bundle)
        # NFR-02/F-05: context_budget применяется к ПОЛНОМУ собранному входу
        # (дайджест + bundle + synthesis + инструкции), не только к дайджесту.
        budget = int(entry.get("context_budget", 120000))
        tokens = core.estimate_tokens(prompt)
        if tokens > budget:
            raise CliError(
                f"полный вход участника {participant['id']} превышает context_budget: "
                f"{tokens} > {budget} токенов (NFR-02) — волна отклонена fail-closed"
            )

        if wave == "proposal":
            def task(entry=entry, prompt=prompt):
                return ac.start_participant(
                    str(entry["adapter"]), prompt, session["paths"], cwd,
                    timeout_sec=session["timeout_sec"], model=entry.get("model"),
                )
        else:
            def task(entry=entry, participant=participant, prompt=prompt):
                return ac.ask_participant(
                    str(entry["adapter"]), participant["review_id"], prompt, cwd,
                    timeout_sec=session["timeout_sec"],
                )
        tasks.append(task)

    results = ac.run_wave(tasks, wave_timeout_sec=session["timeout_sec"] + 2 * ac.WAVE_GRACE_SEC)
    kind = {"confirmation": "confirmation", "final_statement": "final_statement"}.get(wave, "participant_turn")
    for participant, result in zip(turn_participants, results):
        participant["invocations"] = int(participant.get("invocations", 0)) + 1
        participant["retries"] = int(participant.get("retries", 0)) + max(result.attempts - 1, 0)
        updated = core.count_invocation(session, kind)
        session["invocation_count"] = updated["invocation_count"]
        if result.ok:
            if wave == "proposal" and result.review_id:
                # review_id нужен контент-retry ДО записи хода (E-4)
                participant["review_id"] = result.review_id
            structured, warning = core.parse_structured_block(result.text or "")
            # CONS-03 E-4: fail-closed валидация structured-хода (отмена TD 7.4 для
            # ходов участников) + чеклист; final_statement чеклиста не требует.
            validation_errors: list[str] = []
            role = None
            if wave != "final_statement":
                if warning:
                    validation_errors.append(f"structured-блок: {warning}")
                role_key = "D" if wave in ("redteam", "confirmation") else "A"
                role = _role_by_id(session, participant["roles"].get(role_key))
                if role:
                    validation_errors += core.validate_checklist_responses(
                        structured.get("risk_checklist_responses", []), role, wave)
            if validation_errors:
                # Ровно ОДИН контент-retry через ask той же сессии адаптера (E-4)
                core.append_record(sdir, {
                    "session_id": session["session_id"], "phase": session["phase"],
                    "round": session["round"], "wave": wave, "author": "moderator",
                    "type": "system",
                    "content": f"ход {participant['id']} отклонён ДО записи (E-4): "
                               f"{validation_errors}; контент-retry через ask",
                })
                participant["invocations"] = int(participant.get("invocations", 0)) + 1
                updated = core.count_invocation(session, kind)
                session["invocation_count"] = updated["invocation_count"]
                retry_prompt = (
                    "Твой структурированный ход отклонён ядром (fail-closed):\n- "
                    + "\n- ".join(validation_errors)
                    + "\n\nПовтори ход с КОРРЕКТНЫМ structured-блоком: все применимые "
                      "пункты risk_checklist покрыты по item_id, verdict строго "
                      "hit|clear|na, для hit/na валидный note."
                )
                entry = registry_entries[participant["id"]]
                retry_result = ac.ask_participant(
                    str(entry["adapter"]), participant["review_id"], retry_prompt, cwd,
                    timeout_sec=session["timeout_sec"],
                )
                if retry_result.ok:
                    retry_structured, retry_warning = core.parse_structured_block(retry_result.text or "")
                    retry_errors: list[str] = []
                    if retry_warning:
                        retry_errors.append(f"structured-блок: {retry_warning}")
                    if role:
                        retry_errors += core.validate_checklist_responses(
                            retry_structured.get("risk_checklist_responses", []), role, wave)
                    if not retry_errors:
                        result, structured, warning = retry_result, retry_structured, None
                    else:
                        _mark_unresponsive(sdir, session, participant, retry_result)
                        continue
                else:
                    _mark_unresponsive(sdir, session, participant, retry_result)
                    continue
            anon_map = json.loads((sdir / "anon_map.json").read_text(encoding="utf-8"))
            record = core.append_record(sdir, {
                "session_id": session["session_id"], "phase": session["phase"],
                "round": session["round"], "wave": wave,
                "author": participant["id"],
                "anon_id": anon_map.get(participant["id"]) if session["phase"] == "B" else None,
                "type": {"proposal": "proposal", "attack": "attack", "response": "response",
                         "redteam": "redteam_attack", "confirmation": "confirmation",
                         "final_statement": "final_statement"}[wave],
                "content": result.text or "",
                "structured": structured,
            })
            valid, invalid = core.resolve_borrowed(record, core.read_transcript(sdir))
            if invalid:
                core.append_record(sdir, {
                    "session_id": session["session_id"], "phase": session["phase"],
                    "round": session["round"], "wave": wave, "author": "moderator",
                    "type": "system",
                    "content": f"borrowed с неразрешимым source_ref отброшены (антинакрутка FR-04): "
                               f"{invalid} (автор {participant['id']})",
                })
            if wave == "proposal":
                participant["review_id"] = result.review_id
                (sdir / "participants" / f"{participant['id']}.json").write_text(
                    json.dumps({"adapter_session_id": result.session_id,
                                "review_id": result.review_id}, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
        else:
            _mark_unresponsive(sdir, session, participant, result)

    _check_quorum_or_terminate(session)
    if core.invocation_warning(session["invocation_count"]):
        print(f"ПРЕДУПРЕЖДЕНИЕ: вызовов адаптеров {session['invocation_count']} > "
              f"{core.INVOCATION_WARN_MAX} (default-диапазон NFR-01)")


def _resolve_focused_paths(paths: list[str], cwd: Path) -> list[str]:
    """Resolve focused-paths через symlink-зеркала (F-02): `.codex/skills/...` и
    другие symlink-компоненты разрешаются в реальные пути ДО передачи адаптерам —
    sandbox-copy адаптеров отклоняет небезопасные symlink'и."""
    resolved: list[str] = []
    for raw in paths:
        path = Path(raw)
        if not path.is_absolute():
            path = cwd / path
        real = path.resolve()
        try:
            resolved.append(str(real.relative_to(cwd.resolve())))
        except ValueError:
            resolved.append(str(real))
    return resolved


def _generate_digest_draft(sdir: Path, session: dict) -> None:
    """OPT-2: экстрактивный черновик дайджеста после волны (артефакт сессии +
    system-запись в transcript; при превышении context_budget — предупреждение
    БЕЗ усечения; черновик не подставляется автоматически — digest-gate сохранён)."""
    records = core.read_transcript(sdir)
    anon_map = json.loads((sdir / "anon_map.json").read_text(encoding="utf-8"))
    draft = core.generate_digest_draft(records, anon_map)
    name = f"draft-{session.get('phase')}-{session.get('_last_wave')}-r{session.get('_last_round')}.md"
    (sdir / "digests" / name).write_text(draft, encoding="utf-8")
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": session["phase"],
        "round": session["round"], "wave": session.get("_last_wave"),
        "author": "moderator", "type": "system",
        "content": f"digest-draft ({name}):\n{draft}",
    })
    budgets = [p.get("context_budget") for p in session.get("_registry_entries", []) if p.get("context_budget")]
    if budgets and core.estimate_tokens(draft) > min(budgets):
        print(f"ПРЕДУПРЕЖДЕНИЕ: черновик дайджеста превышает context_budget "
              f"({core.estimate_tokens(draft)} > {min(budgets)} токенов) — "
              f"сократите вручную при подготовке финального дайджеста (OPT-2, без усечения)")


# ---------------------------------------------------------------------------
# round (TD 4.1–4.4)
# ---------------------------------------------------------------------------

_CURRENT_ARGS: argparse.Namespace = argparse.Namespace(digest_file=None)


def cmd_round(args: argparse.Namespace) -> int:
    global _CURRENT_ARGS
    _CURRENT_ARGS = args
    cwd = Path.cwd()
    sdir = cwd / session_dir(args.session_id)
    if not sdir.exists():
        raise CliError(f"неизвестная сессия: {args.session_id}")
    # F-002 (CONS-06 rev): flock на всю команду — конкурентный round ждёт,
    # а не перезаписывает состояние старым снимком (lost update).
    with _session_lock(sdir):
        return _cmd_round_locked(args, cwd, sdir)


def _cmd_round_locked(args: argparse.Namespace, cwd: Path, sdir: Path) -> int:
    session = load_session(sdir)
    registry = load_registry_checked(resolve_registry_path(args.registry, session))

    wall = session["wall_clock"]
    if core.wall_clock_status(wall["started_at"], budget_sec=wall.get("budget_sec"),
                              warn_at_sec=wall.get("warn_at_sec"),
                              paused_sec=_human_paused_total(session)) == "exceeded":
        return _terminate(cwd, sdir, session, registry,
                          f"wall-clock budget {wall.get('budget_sec')}s превышен (CONS-02 OPT-1)")

    try:
        if args.kill:
            return _handle_kill(cwd, sdir, session, registry, args)
        try:
            human_wait = _apply_human_gate(sdir, session, args, registry)
        except CliError:
            # Переходы состояния гейта (awaiting_human → awaiting_moderator_decision)
            # обязаны пережить отказ — resume после краха модератора (E10).
            save_session(sdir, session)
            raise
        if human_wait is not None:
            save_session(sdir, session)
            print(human_wait)
            return 0
        if session.get("human_critic"):
            # F-002 (CONS-06 rev): persist мутаций гейта (закрытая пауза, turns,
            # решения модератора) ДО блокирующей волны адаптеров.
            save_session(sdir, session)
        phase = session["phase"]
        if phase == "A":
            _run_wave(sdir, session, "proposal", registry)
            session["phase"] = "B"
            session["wave"] = "attack"
            session["round"] = 1
            session["_last_wave"] = "proposal"
            session["_last_round"] = 0
            _wave_bundle(sdir, session, core.read_transcript(sdir), "A")
            _generate_digest_draft(sdir, session)
            _checkpoint(sdir, session, "phase_a_complete",
                        f"фаза A завершена: proposals от {len(session['participants'])} участников; "
                        f"фаза B, раунд 1")
            print("фаза A завершена: proposals зафиксированы; фаза B, раунд 1 (attack)")
        elif phase == "B":
            if core.b_wave_blocked(session["invocation_count"]):
                session["phase"] = "C"
                session["wave"] = None
                session["stop_state"]["stop_reason"] = "invocation_ceiling"
                save_session(sdir, session)
                print(f"жёсткий потолок вызовов ({core.INVOCATION_HARD_MAX}) — "
                      f"фаза B завершена, синтез из текущего состояния (TD 6)")
                return 0
            wave = session["wave"]
            _run_wave(sdir, session, wave, registry)
            session["_last_wave"] = wave
            session["_last_round"] = session["round"]
            _wave_bundle(sdir, session, core.read_transcript(sdir), "B")
            _generate_digest_draft(sdir, session)
            if wave == "attack":
                session["wave"] = "response"
                print(f"раунд {session['round']}: attack завершён; следующая волна — response")
            else:
                _complete_b_round(sdir, session)
        elif phase == "C":
            _handle_synthesis(cwd, sdir, session, args)
        elif phase == "D":
            wave = session["wave"]
            _run_wave(sdir, session, wave, registry)
            session["_last_wave"] = wave
            session["_last_round"] = session["round"]
            _wave_bundle(sdir, session, core.read_transcript(sdir), "D")
            _generate_digest_draft(sdir, session)
            if wave == "redteam":
                session["wave"] = "confirmation"
                print("red-team ход зафиксирован; следующая волна — confirmation авторов")
            else:
                session["phase"] = "E"
                session["wave"] = None
                _checkpoint(sdir, session, "phase_d_complete",
                            "фаза D завершена: red-team и подтверждения зафиксированы; "
                            "переход к вердикту (фаза E)")
                print("фаза D завершена; зафиксируйте итог: verdict <session_id> --decision-file ...")
        elif phase == "E":
            raise CliError("фаза E: используйте verdict для фиксации итога")
        else:
            raise CliError(f"неизвестная фаза сессии: {phase}")
    except TerminateSession as termination:
        save_session(sdir, session)
        return _terminate(cwd, sdir, session, registry, str(termination))

    save_session(sdir, session)
    return 0


def _complete_b_round(sdir: Path, session: dict) -> None:
    """Завершение раунда B: предикаты стоп-условий + kill-рекомендация (TD 4.2/4.4)."""
    records = core.read_transcript(sdir)
    round_no = session["round"]
    excluded = _human_excluded_authors(session)  # CONS-06 E3: human вне nf/pc и kill-метрик
    nf = core.round_has_new_findings(records, round_no, exclude_authors=excluded)
    pc = core.round_has_position_changes(records, round_no, exclude_authors=excluded)
    freeze = bool(session["stop_state"].pop("freeze_in_round", False))
    alive_ids = core.alive_model_ids(records, session["participants"])  # F-05
    reason, counter = core.evaluate_b_stop(
        len(alive_ids), round_no, nf, pc, freeze, session["stop_state"]["stalemate_counter"]
    )
    session["stop_state"].update({
        "stalemate_counter": counter,
        "rounds_completed_B": round_no,
        "last_round_had_new_findings": nf,
        "last_round_had_position_changes": pc,
    })
    _checkpoint(sdir, session, f"round_{round_no}_complete",
                f"раунд {round_no} фазы B завершён: new_findings={nf}, "
                f"position_changes={pc}, стоп-условие: {reason or 'нет'}")
    if reason:
        session["phase"] = "C"
        session["wave"] = None
        session["stop_state"]["stop_reason"] = reason
        print(f"раунд {round_no} завершён: стоп-условие фазы B — {reason} (FR-02); "
              f"переход к синтезу (фаза C)")
        return
    session["round"] = round_no + 1
    session["wave"] = "attack"
    if len(alive_ids) > 2:
        metrics = core.compute_model_metrics(records, alive_ids, excluded_attack_authors=excluded)
        candidate = core.kill_candidate(metrics)
        print(f"раунд {round_no} завершён; живых моделей {len(alive_ids)} > 2 — kill-рекомендация (FR-03):")
        for pid, m in sorted(metrics.items()):
            print(f"  {pid}: survived={m['survived']} borrowed_in={m['borrowed_in']} "
                  f"upheld_against={m['upheld_against']}")
        if candidate:
            print(f"  детерминированный кандидат на kill: {candidate} "
                  f"(применить: round --kill {candidate} --digest-file ...)")
        else:
            print("  kill НЕ производится: полное равенство прокси-метрик (FR-03a)")
    else:
        print(f"раунд {round_no} завершён; следующий раунд {session['round']} (attack)")


def _handle_kill(cwd: Path, sdir: Path, session: dict, registry: dict, args) -> int:
    """Kill по детерминированному кандидату (FR-03); отклонение — fail-closed.

    Атомарность (F-03): сначала выполняются все fallible-операции (финальное
    заявление), затем состояние kill коммитится одним блоком (участник → killed,
    kill_log, kill_decision в transcript, save_session). Сбой волны финального
    заявления не рушит ядро и не оставляет частично применённого состояния.
    Участник без review_id (start не состоялся) kill'ится без обращения к адаптеру.
    """
    if session["phase"] != "B":
        raise CliError("--kill допустим только в фазе B")
    records = core.read_transcript(sdir)
    alive_ids = core.alive_model_ids(records, session["participants"])
    metrics = core.compute_model_metrics(
        records, alive_ids, excluded_attack_authors=_human_excluded_authors(session))
    candidate = core.kill_candidate(metrics)
    if candidate is None:
        print("kill отклонён: полное равенство прокси-метрик — kill не производится (FR-03a)")
        return EXIT_KILL_REJECTED
    if args.kill != candidate:
        print(f"kill отклонён (FR-03a): запрошен {args.kill}, "
              f"детерминированный кандидат — {candidate}")
        for pid, m in sorted(metrics.items()):
            print(f"  {pid}: survived={m['survived']} borrowed_in={m['borrowed_in']} "
                  f"upheld_against={m['upheld_against']}")
        return EXIT_KILL_REJECTED

    digest = _require_digest(args, sdir, session)  # один digest на kill (FU-01)
    target = next(p for p in session["participants"] if p["id"] == candidate)
    # kill фиксируется за ЗАВЕРШЁННЫМ раундом, а не за следующим (FU-02)
    kill_round = int(session["stop_state"].get("rounds_completed_B") or 0) or session["round"]

    # Fallible-часть ДО коммита состояния: финальное заявление (FR-03b), best-effort.
    if target.get("review_id"):
        session["_kill_target"] = candidate
        try:
            _run_wave(sdir, session, "final_statement", registry, digest_text=digest)
        except TerminateSession:
            session.pop("_kill_target", None)
            raise  # потеря кворума — сессионный уровень, не kill
        except Exception as wave_error:  # noqa: BLE001 — kill не должен рушиться
            core.append_record(sdir, {
                "session_id": session["session_id"], "phase": "B", "round": session["round"],
                "wave": session["wave"], "author": "moderator", "type": "system",
                "content": f"final statement волна для {candidate} завершилась сбоем: "
                           f"{wave_error!r}; kill применяется без финального заявления",
            })
        finally:
            session.pop("_kill_target", None)
    else:
        core.append_record(sdir, {
            "session_id": session["session_id"], "phase": "B", "round": session["round"],
            "wave": session["wave"], "author": "moderator", "type": "system",
            "content": f"финальное заявление недоступно: у участника {candidate} нет сессии "
                       f"адаптера (start не состоялся); kill без обращения к адаптеру",
        })

    # Коммит состояния kill — одним блоком (атомарно с точки зрения session.json).
    target["state"] = "killed"
    target["killed_at_round"] = kill_round
    kill_entry = {
        "participant_id": candidate,
        "round": kill_round,
        "metrics": metrics[candidate],
        "all_metrics": metrics,
        "tie_break": "survived asc → borrowed_in asc → upheld_against desc",
    }
    session["kill_log"].append(kill_entry)
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": "B", "round": kill_round,
        "wave": session["wave"], "author": "moderator", "type": "kill_decision",
        "content": json.dumps(kill_entry, ensure_ascii=False),
        "refs": [],
    })
    save_session(sdir, session)
    print(f"kill применён: {candidate} исключён в раунде {kill_entry['round']}; "
          f"состояние согласовано (kill_log ↔ kill_decision ↔ verdict)")
    return 0


def _handle_synthesis(cwd: Path, sdir: Path, session: dict, args) -> None:
    """Фаза C: синтез модератора в поэлементном формате (CONS-02 OPT-3);
    предикат пропуска фазы D (консервативный); иначе — кросс-ротация ролей фазы D."""
    if not args.synthesis_file:
        raise CliError("фаза C требует --synthesis-file (синтез модератора): отказ")
    text = Path(args.synthesis_file).read_text(encoding="utf-8")
    try:
        elements = core.parse_synthesis(text)  # fail-closed: формат/внетекст/диапазоны/секция
    except core.ProtocolError as parse_error:
        raise CliError(f"синтез отклонён (fail-closed): {parse_error}") from parse_error
    records = core.read_transcript(sdir)
    max_seq = records[-1]["seq"] if records else 0
    refs = sorted({ref for element in elements for ref in element["refs"]})
    unresolvable = [n for n in refs if n < 1 or n > max_seq]
    if unresolvable:
        raise CliError(f"синтез: неразрешимые ссылки seq: {unresolvable} (max seq={max_seq})")
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": "C", "round": session["round"],
        "wave": None, "author": "moderator", "type": "synthesis",
        "content": text, "refs": refs,
    })
    session["synthesis_text"] = text

    skip, details = core.should_skip_phase_d(elements, records, session["participants"])
    # CONS-06 E8 + rev F-011: при включённом human-critic режиме skip запрещён,
    # только если человек фактически сделал ≥1 ход в фазе B — НЕЗАВИСИМО от
    # последующего --human-withdraw (иначе withdraw до синтеза обходил бы D).
    skip, details = core.phase_d_skip_with_human(
        skip, details, records, bool(_human(session).get("enabled")))
    if details.get("human_override"):
        _human_system(sdir, session, f"human-critic: {details['reason']}")
    if skip:
        active = [p for p in session["participants"] if p["state"] == "active"]
        session["phase_d_skip"] = {
            "skipped": True,
            "saved_invocations": 1 + len(active),  # redteam + confirmations
            "reason": details,
        }
        session["phase"] = "E"
        session["wave"] = None
        core.append_record(sdir, {
            "session_id": session["session_id"], "phase": "E", "round": session["round"],
            "wave": None, "author": "moderator", "type": "system",
            "content": f"фаза D пропущена (CONS-02 OPT-3): модель-источник {details['source']}; "
                       f"элементов синтеза: {details['elements']}; все элементы трассируются "
                       f"к одной живой активной модели (membership по structured.elements)",
        })
        _checkpoint(sdir, session, "phase_c_complete",
                    f"синтез зафиксирован ({len(elements)} элементов); фаза D пропущена "
                    f"(модель-источник {details['source']}); переход к вердикту")
        print(f"синтез зафиксирован (refs: {refs}); фаза D ПРОПУЩЕНА: единственная "
              f"модель-источник {details['source']} ({details['elements']} элементов); "
              f"переход к вердикту (фаза E)")
        return

    session["phase_d_skip"] = {"skipped": False, "saved_invocations": 0, "reason": details}
    # кросс-ротация ролей фазы D (FR-08)
    for participant in session["participants"]:
        if participant["state"] == "killed":
            continue
        history = _role_history(cwd, participant["id"], domain=session.get("domain"))
        participant["roles"]["D"] = core.assign_phase_d_role(
            participant["id"], list(participant["roles"].values()), history,
            role_catalog=_role_ids(session),
        )
    active = sorted(p["id"] for p in session["participants"] if p["state"] == "active")
    if not active:
        raise TerminateSession("нет активных участников для ред-тима фазы D — кворум нарушен")
    session["redteamer"] = active[0]
    session["phase"] = "D"
    session["wave"] = "redteam"
    _checkpoint(sdir, session, "phase_c_complete",
                f"синтез зафиксирован ({len(elements)} элементов); фаза D: "
                f"red-team назначен — {session['redteamer']}")
    print(f"синтез зафиксирован (refs: {refs}); фаза D: red-team назначен — {session['redteamer']} "
          f"(предикат пропуска не выполнен: {details.get('reason')})")


def _terminate(cwd: Path, sdir: Path, session: dict, registry: dict, reason: str) -> int:
    """Аварийное завершение: причина в transcript, парный close всех участников,
    track record с outcome=terminated (FR-10/FR-11). Cleanup fail-closed (F-01):
    при неуспешном close участника сессия НЕ удаляется, состояние сохраняется для retry."""
    print(f"СЕССИЯ ЗАВЕРШЕНА АВАРИЙНО: {reason}")
    try:
        core.append_record(sdir, {
            "session_id": session["session_id"], "phase": session.get("phase"),
            "round": session.get("round", 0), "wave": session.get("wave"),
            "author": "moderator", "type": "system",
            "content": f"terminated: {reason}",
        })
    except Exception:
        pass
    registry_entries = registry_map(registry)
    failed = _close_all_participants(cwd, session, registry_entries)
    records = core.read_transcript(sdir)
    if not session.get("track_record_written"):
        write_track_record(cwd, session, records, outcome=f"terminated:{reason}")
        session["track_record_written"] = True
    if failed:
        _mark_cleanup_failed(sdir, session, failed)
        print(f"ОШИБКА cleanup: участники {failed} не закрыты; сессия НЕ удалена, "
              f"состояние сохранено для retry: close {session['session_id']}", file=sys.stderr)
        return EXIT_CLEANUP_FAILED
    _checkpoint(sdir, session, "closed", f"сессия аварийно завершена: {reason}")
    shutil.rmtree(sdir, ignore_errors=True)
    print("парный close всех участников выполнен; сессия удалена; track record обновлён")
    return EXIT_TERMINATED


# ---------------------------------------------------------------------------
# status (NFR-01/NFR-04)
# ---------------------------------------------------------------------------

def cmd_status(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    sdir = cwd / session_dir(args.session_id)
    if not sdir.exists():
        raise CliError(f"неизвестная сессия: {args.session_id}")
    session = load_session(sdir)
    registry = load_registry_checked(resolve_registry_path(getattr(args, "registry", None), session))
    registry_entries = registry_map(registry)
    started = session["wall_clock"]["started_at"]
    now = datetime.now(UTC)
    elapsed = (now - datetime.fromisoformat(started)).total_seconds()
    paused_total = _human_paused_total(session, now)  # CONS-06 E5

    # NFR-04/F-08: агрегация adapter observability (heartbeat/phase/progress counters)
    participants_payload = []
    for participant in session["participants"]:
        entry = {"id": participant["id"], "family": participant["family"],
                 "state": participant["state"], "roles": participant["roles"],
                 "invocations": participant["invocations"], "retries": participant["retries"]}
        review_id = participant.get("review_id")
        if review_id:
            try:
                adapter_status = ac.read_participant_status(
                    str(registry_entries[participant["id"]]["adapter"]), review_id, cwd
                )
                runtime = adapter_status.get("runtime") or {}
                progress = runtime.get("progress") or {}
                entry["adapter"] = {
                    "review_id": review_id,
                    "status": adapter_status.get("status"),
                    "phase": runtime.get("phase"),
                    "state": runtime.get("state"),
                    "heartbeat": runtime.get("last_heartbeat_at"),
                    "elapsed_sec": runtime.get("elapsed_sec"),
                    "progress": {
                        "raw_events": progress.get("raw_events", 0),
                        "tool_calls_total": progress.get("tool_calls_total", 0),
                        "last_event_type": progress.get("last_event_type"),
                    },
                }
            except Exception as status_error:  # noqa: BLE001 — observability не рушит status
                entry["adapter"] = {"review_id": review_id, "error": str(status_error)}
            # Liveness-классификация (RVSW-01, FR-13, TD §9): диагностика
            # active/quiet/dead_watcher по каноническим полям активности; НЕ kill.
            try:
                entry["liveness"] = liveness.classify_participant(
                    cwd, review_id,
                    silence_threshold_sec=float(
                        session.get("silence_threshold_sec", liveness.DEFAULT_SILENCE_THRESHOLD_SEC)),
                )
            except Exception as liveness_error:  # noqa: BLE001 — диагностика не рушит status
                entry["liveness"] = {"class": "unknown", "error": str(liveness_error)}
        participants_payload.append(entry)

    payload = {
        "session_id": session["session_id"],
        "phase": session["phase"],
        "round": session["round"],
        "wave": session["wave"],
        "moderator_id": session["moderator_id"],
        "participants": participants_payload,
        "alive_models": core.alive_model_ids(core.read_transcript(sdir), session["participants"]),
        "unresponsive": [p["id"] for p in session["participants"] if p["state"] == "unresponsive"],
        "invocation_count": session["invocation_count"],
        "invocation_warning": core.invocation_warning(session["invocation_count"]),
        "stop_state": session["stop_state"],
        "kill_log": session["kill_log"],
        "quorum": session["quorum"],
        "wall_clock": {"elapsed_sec": round(elapsed, 1),
                       "paused_total_sec": round(paused_total, 1),
                       "real_elapsed_sec": round(elapsed, 1),
                       "active_elapsed_sec": round(max(0.0, elapsed - paused_total), 1),
                       "awaiting_human": bool(_human(session).get("awaiting_since")),
                       "status": core.wall_clock_status(
                           started,
                           budget_sec=session["wall_clock"].get("budget_sec"),
                           warn_at_sec=session["wall_clock"].get("warn_at_sec"),
                           paused_sec=paused_total)},
        "last_checkpoint": progress_tracker.last_checkpoint(sdir),  # TD §9.3: status дублирует
    }
    if _human(session).get("enabled"):
        hc = _human(session)
        payload["human_critic"] = {
            "enabled": True,
            "state": hc.get("state") or "idle",
            "awaiting_since": hc.get("awaiting_since"),
            "wait_cap_sec": hc.get("wait_cap_sec"),
            "wait_extended_until": hc.get("wait_extended_until"),
            "skipped_waves": hc.get("skipped_waves") or [],
            "withdrawn": bool(hc.get("withdrawn")),
            "withdraw_reason": hc.get("withdraw_reason"),
            "turns": hc.get("turns") or [],
        }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


# ---------------------------------------------------------------------------
# watch (CONS-05, вариант A): read-only observability, НЕ часть протокола
# ---------------------------------------------------------------------------

SESSION_ID_RE = re.compile(r"[A-Za-z0-9._-]+")


def _validate_session_id(session_id: str) -> None:
    """F-015 (CONS-06 rev): session_id — один path-компонент (без слешей и ..),
    иначе отказ fail-closed (path traversal через .consilium-sessions/<id>)."""
    if not SESSION_ID_RE.fullmatch(session_id or "") or session_id in (".", ".."):
        raise CliError(f"невалидный session_id: {session_id!r} (fail-closed)")


def cmd_watch(args: argparse.Namespace) -> int:
    """Live-view сессии или поток одного участника. Только чтение файлов:
    watch НЕ пишет в состояние сессии, НЕ вызывает адаптеры и сеть и не
    влияет на state machine (наблюдатель — модератор/человек, не участник)."""
    try:
        # F-008: построчная буферизация stdout — иначе при не-tty (панель herdr,
        # pipe) поток участника приходит большими порциями и tail-семантика ломается.
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, OSError):
        pass
    _validate_session_id(args.session_id)  # F-015 (CONS-06 rev)
    cwd = Path.cwd()
    sdir = cwd / session_dir(args.session_id)
    if not sdir.exists():
        raise CliError(f"неизвестная сессия: {args.session_id}")
    interval = float(args.interval)
    # F-005 (CONS-06 rev): 0 → busy-loop, отрицательное → ValueError в sleep.
    if not 0.1 <= interval <= 3600:
        raise CliError(f"невалидный --interval: {args.interval} (допустимо 0.1–3600 сек)")
    try:
        if args.participant:
            return _watch_participant_stream(sdir, args, interval)
        return _watch_overview(sdir, cwd, args, interval)
    except KeyboardInterrupt:
        print("\nwatch остановлен (Ctrl+C)", file=sys.stderr)
        return 0


def _watch_overview(sdir: Path, cwd: Path, args: argparse.Namespace, interval: float) -> int:
    """Единый live-view: кадр по всем участникам, перерисовка по poll-интервалу."""
    while True:
        snapshot = watch.load_snapshot(sdir)
        watch.clear_screen()
        print(watch.render_overview(snapshot, cwd, excerpt_lines=args.excerpt_lines), flush=True)
        reason = watch.terminal_reason(sdir, snapshot)
        if reason:
            print(f"\nwatch завершён: {reason}", flush=True)
            return 0
        if args.once:
            return 0
        time.sleep(interval)


def _watch_participant_stream(sdir: Path, args: argparse.Namespace, interval: float) -> int:
    """Поток одного участника: печатает новые ходы/события по мере появления
    (tail-семантика поверх transcript.jsonl; сначала выводит уже записанное)."""
    snapshot = watch.load_snapshot(sdir)
    participant_ids = [p.get("id") for p in snapshot["session"].get("participants") or []]
    if args.participant not in participant_ids:
        raise CliError(
            f"участник {args.participant!r} не найден в сессии; состав: {participant_ids}"
        )
    print(f"=== watch участника {args.participant} (сессия {args.session_id}) ===", flush=True)
    last_seq = 0
    while True:
        snapshot = watch.load_snapshot(sdir)
        events = watch.participant_events(snapshot["records"], args.participant,
                                          after_seq=last_seq)
        for event in events:
            last_seq = max(last_seq, int(event.get("_watch_seq") or 0))
            print(watch.render_turn(event), flush=True)
            print(flush=True)
        reason = watch.terminal_reason(sdir, snapshot)
        if reason:
            print(f"watch завершён: {reason}", flush=True)
            return 0
        if args.once:
            return 0
        time.sleep(interval)


# ---------------------------------------------------------------------------
# verdict (FR-01/FR-03/FR-05/FR-06, TD 3.5)
# ---------------------------------------------------------------------------

def _exclusion_section(synthesis_text: str) -> str:
    """Секция обоснования исключения вкладов из синтеза (CONS-02 OPT-3, F-04):
    попадает в transcript (запись synthesis) и в verdict.md."""
    lines = (synthesis_text or "").splitlines()
    start = None
    for index, line in enumerate(lines):
        if core.SYNTHESIS_HEADER_RE.match(line) and core.SYNTHESIS_EXCLUSION_MARK in line.lower():
            start = index
            break
    if start is None:
        return ""
    body: list[str] = []
    for line in lines[start:]:
        if body and core.SYNTHESIS_HEADER_RE.match(line) and core.SYNTHESIS_EXCLUSION_MARK not in line.lower():
            break
        body.append(line)
    return "\n".join(body).strip()


def cmd_verdict(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    sdir = cwd / session_dir(args.session_id)
    if not sdir.exists():
        raise CliError(f"неизвестная сессия: {args.session_id}")
    session = load_session(sdir)
    if session["phase"] != "E":
        raise CliError(f"verdict доступен только в фазе E (текущая: {session['phase']})")
    if not args.decision_file:
        raise CliError("verdict требует --decision-file (решение модератора): отказ")
    decision = Path(args.decision_file).read_text(encoding="utf-8")
    records = core.read_transcript(sdir)
    max_seq = records[-1]["seq"] if records else 0
    refs = [int(n) for n in re.findall(r"seq:(\d+)", decision)]
    if not refs:
        raise CliError("решение без traceability-ссылок seq:<n> — отказ (TD 3.5)")
    unresolvable = [n for n in refs if n < 1 or n > max_seq]
    if unresolvable:
        raise CliError(f"решение: неразрешимые ссылки seq: {unresolvable}")

    # minority report: ДОСЛОВНО из transcript (FR-05) — редактирование модератором
    # конструктивно невозможно: тексты берутся только из записей transcript.
    minority_parts: list[str] = []
    for record in records:
        if record["type"] == "final_statement":
            minority_parts.append(
                f"### Финальное заявление исключённой модели ({record['author']}, дословно)\n\n"
                f"{record['content']}"
            )
    for record in records:
        if record["type"] != "confirmation":
            continue
        dissenting = any(
            change.get("action") == "disagree"
            for change in (record.get("structured") or {}).get("position_changes") or []
        )
        if dissenting:
            minority_parts.append(
                f"### Позиция несогласного участника ({record['author']}, дословно)\n\n"
                f"{record['content']}"
            )
    minority = "\n\n".join(minority_parts) or "(несогласных нет)"

    kill_log_lines = ["| участник | раунд | survived | borrowed_in | upheld_against | tie-break |",
                      "| --- | --- | --- | --- | --- | --- |"]
    for entry in session["kill_log"]:
        m = entry["metrics"]
        kill_log_lines.append(
            f"| {entry['participant_id']} | {entry['round']} | {m['survived']} | "
            f"{m['borrowed_in']} | {m['upheld_against']} | {entry['tie_break']} |"
        )
    if not session["kill_log"]:
        kill_log_lines.append("| — | — | — | — | — | kill не производился |")

    unresponsive = [p["id"] for p in session["participants"] if p["state"] == "unresponsive"]
    retries = {p["id"]: p["retries"] for p in session["participants"] if p["retries"]}
    incidents = []
    if unresponsive:
        incidents.append(f"- unresponsive-участники (модели заморожены, FR-11): {', '.join(unresponsive)}")
    if retries:
        incidents.append(f"- retry вызовов адаптеров: {retries}")
    if not incidents:
        incidents.append("- инцидентов нет")

    quorum = session["quorum"]
    composition = [f"- кворум при convene: {'выполнен' if quorum['ok'] else 'НАРУШЕН'}; "
                   f"families: {quorum['families']}"]
    if quorum.get("homogeneity_warning"):
        composition.append("- ПРЕДУПРЕЖДЕНИЕ: однородность family в составе сверх минимального кворума")
    if quorum.get("excluded"):
        composition.append(f"- исключены doctor: {[e['id'] for e in quorum['excluded']]}")
    if unresponsive:
        composition.append("- состав деградировал в ходе сессии (unresponsive)")
    if _human(session).get("enabled"):
        hc = _human(session)
        human_line = (f"- human-critic: режим включён; ходов: {len(hc.get('turns') or [])} "
                      f"(только атакующие волны B/D); в family-разнообразие НЕ засчитывается "
                      f"(CONS-06 E11)")
        if hc.get("withdrawn"):
            human_line += f"; режим свёрнут модератором (--human-withdraw): {hc.get('withdraw_reason')}"
        composition.append(human_line)

    cost = (f"фактические вызовы адаптеров: {session['invocation_count']} "
            f"(расчётный диапазон 16–25 default, потолок {core.INVOCATION_HARD_MAX}; NFR-01/TD 6)")
    escalation = "ESCALATE_TO_HUMAN" if args.escalate else "нет"
    stop_reason = session["stop_state"].get("stop_reason") or "—"
    phase_d_skip = session.get("phase_d_skip") or {}
    if phase_d_skip.get("skipped"):
        reason = phase_d_skip.get("reason") or {}
        phase_d_section = (
            f"фаза D пропущена (CONS-02 OPT-3): синтез трассируется к единственной "
            f"модели-источнику {reason.get('source')} ({reason.get('elements')} элементов, "
            f"membership-проверка пройдена); сэкономлено вызовов: "
            f"{phase_d_skip.get('saved_invocations', 0)}"
        )
    else:
        phase_d_section = "фаза D выполнена (ред-тим синтеза)"
    exclusion_section = _exclusion_section(session.get("synthesis_text", "")) or \
        "(секция обоснования исключения вкладов отсутствует)"

    verdict_text = f"""# Вердикт консилиума {session['session_id']}

Вопрос: {session['question']}

## Решение

{decision}

## Kill-log (FR-03c)

{chr(10).join(kill_log_lines)}

## Minority report (FR-03b/FR-05, дословно)

{minority}

## Флаг состава (FR-06)

{chr(10).join(composition)}

## Инциденты (FR-11)

{chr(10).join(incidents)}

## Стоимость (NFR-01)

{cost}

## Завершение фазы B

стоп-условие: {stop_reason}

## Фаза D

{phase_d_section}

## Обоснование исключения вкладов (CONS-02 OPT-3)

{exclusion_section}

## Эскалация

{escalation}

---
Advisory-only: вердикт не является acceptance/finalization gate (FR-13).
"""
    (sdir / "verdict.md").write_text(verdict_text, encoding="utf-8")
    core.append_record(sdir, {
        "session_id": session["session_id"], "phase": "E", "round": session["round"],
        "wave": None, "author": "moderator", "type": "verdict",
        "content": decision, "refs": refs,
    })
    session["verdict_done"] = True
    save_session(sdir, session)
    _checkpoint(sdir, session, "verdict_ready",
                f"вердикт зафиксирован (refs: {refs}); kill-log: "
                f"{len(session['kill_log'])} записей; эскалация: {escalation}")
    print(f"verdict зафиксирован: {sdir / 'verdict.md'}")
    print(f"вызовов адаптеров: {session['invocation_count']}; minority report: "
          f"{'есть' if minority_parts else 'пуст'}; эскалация: {escalation}")
    print("следующий шаг: close <session_id> (парность cleanup, FR-10)")
    return 0


# ---------------------------------------------------------------------------
# close (FR-10)
# ---------------------------------------------------------------------------

def _close_participant_idempotent(adapter: str, review_id: str, cwd: Path,
                                  keep_sandbox: bool = False) -> tuple[str, bool]:
    """Идемпотентный close участника (FU-03): отсутствующий sandbox — уже закрыт
    (НЕ failure, ложного CLOSE FAILED нет); успешный close — closed; иначе failed."""
    sandbox = cwd / ac.REVIEW_ROOT / review_id
    if not sandbox.exists():
        return "closed", True
    if ac.close_participant(adapter, review_id, cwd, keep_sandbox=keep_sandbox):
        return ("kept --keep-sandbox" if keep_sandbox else "closed"), True
    return "failed", False


def _close_all_participants(cwd: Path, session: dict, registry_entries: dict,
                            keep_sandbox: bool = False) -> list[str]:
    """Закрывает всех участников с идемпотентностью; печатает cleanup status;
    возвращает список id участников, которых закрыть не удалось."""
    failed: list[str] = []
    statuses = session["cleanup"].setdefault("participants_closed", {})
    for participant in session["participants"]:
        review_id = participant.get("review_id")
        if not review_id:
            statuses[participant["id"]] = "not started"
            print(f"  {participant['id']}: not started")
            continue
        status, ok = _close_participant_idempotent(
            str(registry_entries[participant["id"]]["adapter"]), review_id, cwd, keep_sandbox)
        statuses[participant["id"]] = status
        if not ok:
            failed.append(participant["id"])
            print(f"  {participant['id']} ({review_id}): CLOSE FAILED")
        else:
            print(f"  {participant['id']} ({review_id}): {status}")
    return failed


def _mark_cleanup_failed(sdir: Path, session: dict, failed: list[str]) -> None:
    """Фиксация cleanup_status=failed с данными для retry (F-01): сессия НЕ
    помечается closed и НЕ удаляется."""
    session["cleanup"]["session_closed"] = False
    session["cleanup"]["status"] = "failed"
    session["cleanup"]["failed_participants"] = failed
    session["cleanup"]["retry"] = f"close {session['session_id']}"
    save_session(sdir, session)


def cmd_close(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    sdir = cwd / session_dir(args.session_id)
    if not sdir.exists():
        raise CliError(f"неизвестная сессия: {args.session_id}")
    session = load_session(sdir)
    registry = load_registry_checked(resolve_registry_path(args.registry, session))
    registry_entries = registry_map(registry)

    print("cleanup status:")
    failed = _close_all_participants(cwd, session, registry_entries, keep_sandbox=args.keep)
    if failed:
        _mark_cleanup_failed(sdir, session, failed)
        print(f"ОШИБКА cleanup (F-01, fail-closed): участники {failed} не закрыты; "
              f"сессия НЕ удалена и НЕ помечена closed, состояние сохранено для retry: "
              f"close {args.session_id}", file=sys.stderr)
        return EXIT_CLEANUP_FAILED

    records = core.read_transcript(sdir)
    outcome = "verdict" if session.get("verdict_done") else "closed_without_verdict"
    if not session.get("track_record_written"):
        write_track_record(cwd, session, records, outcome=outcome)
        session["track_record_written"] = True
    session["cleanup"]["session_closed"] = True
    session["cleanup"]["status"] = "ok"
    _checkpoint(sdir, session, "closed",
                f"сессия закрыта ({outcome}); cleanup парный, track record обновлён")
    if args.keep:
        save_session(sdir, session)
        print(f"сессия сохранена (--keep): {sdir}")
    else:
        shutil.rmtree(sdir, ignore_errors=True)
        print(f"  сессия {args.session_id}: closed (каталог удалён)")
    print(f"  track record: {cwd / TRACK_ROOT} (durable, из checkpoint исключён)")
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Ядро архитектурного консилиума (CONS-01).")
    sub = parser.add_subparsers(dest="cmd", required=True)

    def add_registry(p):
        p.add_argument("--registry", default=None,
                       help="Путь к adapters.yaml (default: из сессии, иначе реестр skill'а).")

    p_convene = sub.add_parser("convene", help="Создать сессию консилиума.")
    p_convene.add_argument("--question", required=True, help="Архитектурный вопрос.")
    p_convene.add_argument("--caller",
                           help="id вызывающего primary agent (default: primary); фиксируется "
                                "как moderator_id в session/track record (правило композиции FR-05).")
    p_convene.add_argument("--domain", default=None,
                           help="Доменный пакет консилиума (CONS-03, default: architecture).")
    p_convene.add_argument("--domains", default=None,
                           help="Путь к domains.yaml (default: реестр skill'а).")
    p_convene.add_argument("--paths", nargs="*", help="Focused-paths материалов (NFR-06).")
    p_convene.add_argument("--timeout-sec", type=int, default=ac.DEFAULT_TIMEOUT_SEC,
                           help="Per-invocation timeout адаптеров (CONS-02 OPT-1, default 900).")
    p_convene.add_argument("--silence-threshold-sec", type=float,
                           default=liveness.DEFAULT_SILENCE_THRESHOLD_SEC,
                           help="Порог тишины liveness-классификации (RVSW-01, TD §9.2, default 120).")
    p_convene.add_argument("--probe", action="store_true", help="doctor с живым probe-вызовом.")
    p_convene.add_argument("--human-critic", action="store_true",
                           help="Human-critic режим (CONS-06, default off): человек ходит "
                                "в атакующих волнах фаз B/D через turn-файл модератора.")
    p_convene.add_argument("--human-wait-cap-sec", type=int,
                           default=core.DEFAULT_HUMAN_WAIT_CAP_SEC,
                           help=f"Кап ожидания human-хода (E6, default "
                                f"{core.DEFAULT_HUMAN_WAIT_CAP_SEC}s); по достижении — "
                                f"awaiting_moderator_decision.")
    add_registry(p_convene)
    p_convene.set_defaults(func=cmd_convene)

    p_round = sub.add_parser("round", help="Провести очередную волну/ход текущей фазы.")
    p_round.add_argument("session_id")
    p_round.add_argument("--digest-file", help="Дайджест модератора (обязателен для всех волн, кроме первой).")
    p_round.add_argument("--kill", help="Применить kill к participant_id (только детерминированный кандидат).")
    p_round.add_argument("--synthesis-file", help="Синтез модератора (фаза C).")
    p_round.add_argument("--human-turn-file",
                         help="Human-ход на pending атакующую волну (E2): JSON "
                              "{content, structured, human_approved: true}.")
    p_round.add_argument("--human-keep-raw", action="store_true",
                         help="Сохранить raw human turn-файл в сессии (forensic-опция E9, не дефолт).")
    p_round.add_argument("--human-continue-wait", action="store_true",
                         help="Решение модератора: продлить ожидание human-хода новым "
                              "ограниченным интервалом (E6).")
    p_round.add_argument("--human-skip-wave", action="store_true",
                         help="Решение модератора: одноразово пропустить human-ход текущей волны (E6).")
    p_round.add_argument("--human-withdraw", action="store_true",
                         help="Решение модератора: свернуть human-critic режим, сессия "
                              "продолжается без человека (E6). Требует --reason.")
    p_round.add_argument("--reason", help="Причина для --human-withdraw (обязательна).")
    add_registry(p_round)
    p_round.set_defaults(func=cmd_round)

    p_status = sub.add_parser("status", help="Состояние сессии (JSON).")
    p_status.add_argument("session_id")
    p_status.set_defaults(func=cmd_status)

    p_watch = sub.add_parser("watch", help="Live-view сессии / поток участника (CONS-05, read-only).")
    p_watch.add_argument("session_id")
    p_watch.add_argument("--participant", help="Поток одного участника (отдельная панель).")
    p_watch.add_argument("--interval", type=float, default=3.0,
                         help="Poll-интервал обновления, сек (default 3).")
    p_watch.add_argument("--excerpt-lines", type=int, default=6,
                         help="Строк excerpt'а последнего хода в live-view (default 6).")
    p_watch.add_argument("--once", action="store_true",
                         help="Один кадр/снимок без цикла опроса (диагностика, тесты).")
    p_watch.set_defaults(func=cmd_watch)

    p_verdict = sub.add_parser("verdict", help="Зафиксировать итог фазы E.")
    p_verdict.add_argument("session_id")
    p_verdict.add_argument("--decision-file", required=True, help="Решение модератора (с seq-ссылками).")
    p_verdict.add_argument("--escalate", action="store_true",
                           help="Material-разногласие: пометить ESCALATE_TO_HUMAN.")
    p_verdict.set_defaults(func=cmd_verdict)

    p_close = sub.add_parser("close", help="Закрыть сессию и удалить рабочие данные (FR-10).")
    p_close.add_argument("session_id")
    p_close.add_argument("--keep", action="store_true", help="Сохранить каталог сессии (forensic).")
    add_registry(p_close)
    p_close.set_defaults(func=cmd_close)

    p_doctor = sub.add_parser("doctor", help="Pre-flight healthcheck реестра.")
    p_doctor.add_argument("--json", action="store_true", help="Machine-readable вывод.")
    p_doctor.add_argument("--probe", action="store_true", help="Живой probe-вызов адаптеров.")
    p_doctor.add_argument("--domains", default=None,
                          help="Путь к domains.yaml (default: реестр skill'а).")
    add_registry(p_doctor)
    p_doctor.set_defaults(func=cmd_doctor)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except CliError as error:
        print(f"Error: {error}", file=sys.stderr)
        return error.code
    except core.ProtocolError as error:
        print(f"ProtocolError: {error}", file=sys.stderr)
        return EXIT_PROTOCOL
    except Exception as error:  # noqa: BLE001 — fail-closed с диагностикой
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

"""Watch-слой консилиума (CONS-05, вариант A): read-only наблюдение за сессией.

Исполнение остаётся headless; watch только ЧИТАЕТ артефакты сессии и sandbox'ей:

  .consilium-sessions/<id>/session.json      — фаза/раунд/волна, участники, счётчики;
  .consilium-sessions/<id>/transcript.jsonl  — ходы (tail-семантика для --participant);
  .consilium-sessions/<id>/progress.jsonl    — чекпоинты границ фаз/раундов;
  .consilium-sessions/<id>/anon_map.json     — anon_id участников фазы B;
  .review-sandboxes/<review_id>/runtime.json — liveness-поля активности адаптера
                                               (читается напрямую, без вызова адаптера).

Инварианты слоя: никаких записей в состояние сессии, никаких сетевых вызовов и
subprocess'ов адаптеров — только чтение файлов. Watch — observability, НЕ гейт и
НЕ вход для человеческих ходов: наблюдатель не влияет на протокол и state machine.
"""
from __future__ import annotations

import json
import re
import sys
from datetime import UTC, datetime
from pathlib import Path

import adapter_contract as ac
import liveness

# Чекпоинты progress.jsonl, после которых сессия считается завершённой (CONSILIUM_CHECKPOINTS).
TERMINAL_CHECKPOINTS = ("verdict_ready", "closed")

# Типы записей transcript — ходы/решения участников (для excerpt в overview и stream).
PARTICIPANT_TURN_TYPES = (
    "proposal", "attack", "response", "redteam_attack", "confirmation", "final_statement",
)


def _read_json(path: Path, default):
    """Толерантное чтение JSON: отсутствующий/битый файл → default (watch не рушится)."""
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def _read_jsonl(path: Path) -> list[dict]:
    """Append-only jsonl; битые строки заменяются sentinel'ом, а не удаляются
    (F-007, CONS-06 rev): позиции остальных записей не смещаются между poll'ами,
    позиционный fallback seq в participant_events остаётся стабильным —
    повтора/потери события нет. Недописанный хвост прочитается на следующем poll."""
    records: list[dict] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return records
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            records.append({"_watch_broken": True})  # placeholder, не событие
    return records


def load_snapshot(sdir: Path) -> dict:
    """Снимок состояния сессии из файлов (единственный источник чтения watch)."""
    return {
        "session": _read_json(sdir / "session.json", {}),
        "records": _read_jsonl(sdir / "transcript.jsonl"),
        "checkpoints": _read_jsonl(sdir / "progress.jsonl"),
        "anon_map": _read_json(sdir / "anon_map.json", {}),
    }


def terminal_reason(sdir: Path, snapshot: dict) -> str | None:
    """Терминальное состояние сессии → человекочитаемая причина; иначе None.

    Признаки (в порядке приоритета): каталог удалён (close без --keep),
    cleanup.session_closed, verdict_done, последний чекпоинт ∈ TERMINAL_CHECKPOINTS.
    """
    if not sdir.exists():
        return "каталог сессии удалён (close выполнен)"
    session = snapshot["session"]
    cleanup = session.get("cleanup") or {}
    if cleanup.get("session_closed"):
        return "сессия закрыта (cleanup.session_closed)"
    if session.get("verdict_done"):
        return "вердикт зафиксирован (verdict_done); ожидается close"
    checkpoints = snapshot["checkpoints"]
    if checkpoints and checkpoints[-1].get("checkpoint") in TERMINAL_CHECKPOINTS:
        return f"терминальный чекпоинт: {checkpoints[-1]['checkpoint']}"
    return None


# F-013 (CONS-06 rev): контент transcript/адаптеров — недоверенный; перед печатью
# в терминал вырезаем ANSI/OSC-последовательности и управляющие символы.
_ANSI_CSI_RE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
_ANSI_OSC_RE = re.compile(r"\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)")
_ANSI_ESC_RE = re.compile(r"\x1b[@-_]")
_CONTROL_RE = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")


def sanitize_terminal(text: str) -> str:
    """Санитизация недоверенного текста для терминала (F-013): ANSI CSI/OSC/ESC
    и прочие control-символы удаляются; переносы строк и табуляция сохраняются."""
    cleaned = _ANSI_OSC_RE.sub("", text or "")
    cleaned = _ANSI_CSI_RE.sub("", cleaned)
    cleaned = _ANSI_ESC_RE.sub("", cleaned)
    return _CONTROL_RE.sub("", cleaned)


def _excerpt(text: str, lines: int) -> str:
    """N последних непустых строк хода (последнее = самое свежее в structured-ходе)."""
    body = [line for line in (text or "").splitlines() if line.strip()]
    return "\n".join(body[-lines:]) if body else "(пустой ход)"


def _last_turn(records: list[dict], participant_id: str) -> dict | None:
    for record in reversed(records):
        if record.get("author") == participant_id and record.get("type") in PARTICIPANT_TURN_TYPES:
            return record
    return None


def adapter_runtime(cwd: Path, review_id: str | None) -> dict | None:
    """runtime.json sandbox'а участника (файл, не subprocess — F-001): state/phase/
    elapsed_sec, прогресс-счётчики (raw_events/tool_calls_total/last_event_type).
    Отсутствующий/битый файл или отсутствие review_id → None."""
    if not review_id:
        return None
    runtime = _read_json(Path(cwd) / ac.REVIEW_ROOT / review_id / "runtime.json", None)
    return runtime if isinstance(runtime, dict) else None


def _activity_age_sec(runtime: dict, now: datetime) -> float | None:
    """Возраст last_activity_at в секундах; битый/отсутствующий таймстамп → None."""
    value = runtime.get("last_activity_at")
    if not isinstance(value, str):
        return None
    try:
        return (now - datetime.fromisoformat(value)).total_seconds()
    except ValueError:
        return None


def _liveness_view(cwd: Path, review_id: str | None, silence_threshold_sec: float,
                   runtime: dict | None, now: datetime) -> str:
    """Отображение живости (F-002): класс liveness показывается ТОЛЬКО при идущей
    инвокации (runtime.state == running — heartbeat тикает только в ней); иначе —
    явное «инвокация не идёт» + возраст последней активности. Голый dead_watcher
    в исправной паузе между волнами не печатается."""
    if not review_id:
        return "not_started"
    if runtime is None:
        return "unknown"
    if runtime.get("state") == "running":
        try:
            activity = ac.read_participant_activity(cwd, review_id)
            return liveness.classify_liveness(
                activity, silence_threshold_sec=silence_threshold_sec)["class"]
        except Exception:  # noqa: BLE001 — диагностика не рушит watch
            return "unknown"
    age = _activity_age_sec(runtime, now)
    age_text = f"{age:.0f}с назад" if age is not None else "нет данных"
    return f"инвокация не идёт (state={runtime.get('state') or '?'}, активность {age_text})"


def participant_rows(snapshot: dict, cwd: Path, now: datetime | None = None) -> list[dict]:
    """Строки live-view по участникам: фаза/раунд общие, активность и последний ход —
    персональные. Реальные id показываются намеренно: наблюдатель — модератор."""
    session = snapshot["session"]
    records = snapshot["records"]
    anon_map = snapshot["anon_map"]
    now = now or datetime.now(UTC)
    silence = float(session.get("silence_threshold_sec") or liveness.DEFAULT_SILENCE_THRESHOLD_SEC)
    rows: list[dict] = []
    for participant in session.get("participants") or []:
        pid = participant.get("id", "?")
        turn = _last_turn(records, pid)
        review_id = participant.get("review_id")
        runtime = adapter_runtime(cwd, review_id)
        rows.append({
            "id": pid,
            "anon_id": anon_map.get(pid),
            "family": participant.get("family", "?"),
            "state": participant.get("state", "?"),
            "roles": participant.get("roles") or {},
            "invocations": int(participant.get("invocations") or 0),
            "retries": int(participant.get("retries") or 0),
            "review_id": review_id,
            "runtime": runtime,
            "liveness": _liveness_view(cwd, review_id, silence, runtime, now),
            "last_turn": turn,
        })
    return rows


def render_overview(snapshot: dict, cwd: Path, excerpt_lines: int = 6,
                    now: datetime | None = None) -> str:
    """Единый live-view сессии: заголовок фазы + блок на участника."""
    session = snapshot["session"]
    now = now or datetime.now(UTC)
    sid = session.get("session_id", "?")
    lines = [
        f"=== consilium watch: {sid} — {now.strftime('%Y-%m-%d %H:%M:%S')} UTC ===",
        f"фаза {session.get('phase', '?')} раунд {session.get('round', '?')} "
        f"волна {session.get('wave') or '—'} | вызовов адаптеров: "
        f"{session.get('invocation_count', 0)} | вопрос: {session.get('question', '')}",
    ]
    checkpoints = snapshot["checkpoints"]
    if checkpoints:
        last = checkpoints[-1]
        lines.append(f"последний чекпоинт: {last.get('checkpoint')} — {last.get('summary')}")
    human_line = human_critic_view(session, now=now)
    if human_line:
        lines.append(human_line)
    lines.append("")
    for row in participant_rows(snapshot, cwd, now=now):
        roles = "/".join(f"{wave}:{role}" for wave, role in sorted(row["roles"].items()))
        header = (f"[{row['id']}] anon={row['anon_id'] or '—'} family={row['family']} "
                  f"state={row['state']} liveness={row['liveness']} "
                  f"invocations={row['invocations']} retries={row['retries']} роли={roles}")
        lines.append(header)
        runtime = row["runtime"]
        if runtime:
            # Живой прогресс инвокации (F-001): файлы сессии пишутся только ПОСЛЕ
            # блокирующей волны, поэтому во время волны это единственный живой сигнал.
            progress = runtime.get("progress") or {}
            lines.append(
                f"  адаптер: state={runtime.get('state') or '?'} "
                f"phase={runtime.get('phase') or '?'} "
                f"elapsed={runtime.get('elapsed_sec') or 0}с "
                f"events={progress.get('raw_events', 0)} "
                f"tools={progress.get('tool_calls_total', 0)} "
                f"last_event={progress.get('last_event_type') or '—'}"
            )
        turn = row["last_turn"]
        if turn:
            lines.append(f"  последний ход: seq={turn.get('seq')} type={turn.get('type')} "
                         f"фаза {turn.get('phase')} раунд {turn.get('round')} "
                         f"волна {turn.get('wave') or '—'} ts={turn.get('ts', '?')}")
            for excerpt_line in _excerpt(turn.get("content", ""), excerpt_lines).splitlines():
                lines.append(f"  | {excerpt_line}")
        else:
            lines.append("  последний ход: — (ходов ещё не было)")
        lines.append("")
    return sanitize_terminal("\n".join(lines).rstrip())


def human_critic_view(session: dict, now: datetime | None = None) -> str | None:
    """Строка состояния human-critic режима для overview (CONS-06 E10):
    awaiting_human с длительностью ожидания и капом / awaiting_moderator_decision /
    withdrawn; режим выключен → None."""
    hc = session.get("human_critic")
    if not hc or not hc.get("enabled"):
        return None
    now = now or datetime.now(UTC)
    wall = session.get("wall_clock") or {}
    paused = float(wall.get("paused_sec") or 0.0)
    turns = len(hc.get("turns") or [])
    if hc.get("withdrawn"):
        return (f"human-critic: режим свёрнут (--human-withdraw): "
                f"{hc.get('withdraw_reason') or '—'}; ходов: {turns}")
    state = hc.get("state") or "idle"
    since = hc.get("awaiting_since")
    waiting = 0.0
    if since:
        try:
            waiting = max(0.0, (now - datetime.fromisoformat(since)).total_seconds())
        except ValueError:
            waiting = 0.0
    base = (f"human-critic: state={state} | ходов: {turns} | "
            f"пауза wall-clock: {paused + waiting:.0f}с | кап ожидания: {hc.get('wait_cap_sec')}с")
    if state == "awaiting_human":
        return base + f" | ждём human-ход {waiting:.0f}с"
    if state == "awaiting_moderator_decision":
        return base + " | ТРЕБУЕТСЯ РЕШЕНИЕ МОДЕРАТОРА (continue-wait/skip-wave/withdraw/turn-file)"
    return base


def render_turn(record: dict, excerpt_lines: int = 0) -> str:
    """Блок хода для потока участника (stream-режим); excerpt_lines=0 — ход целиком."""
    header = (f"--- seq={record.get('seq')} {record.get('type')} "
              f"фаза {record.get('phase')} раунд {record.get('round')} "
              f"волна {record.get('wave') or '—'} ts={record.get('ts', '?')}")
    content = record.get("content") or ""
    if excerpt_lines > 0:
        content = _excerpt(content, excerpt_lines)
    return sanitize_terminal(header + "\n" + content.rstrip())


def _mentions_participant(content: str, participant_id: str) -> bool:
    """Точное совпадение токена id (F-006): границы — не буква/цифра/подчёрк/дефис,
    иначе 'kimi' ловил бы записи про 'kimi-k2' (префиксная коллизия)."""
    return re.search(rf"(?<![\w-]){re.escape(participant_id)}(?![\w-])", content) is not None


def participant_events(records: list[dict], participant_id: str, after_seq: int = 0) -> list[dict]:
    """Новые события участника: его ходы + system/error-записи модератора о нём
    (отклонения хода, unresponsive) — поток одной панели (tail-семантика).

    Позиционный fallback (F-009): записи без валидного seq (None/0) получают
    эффективный seq = позиции в append-only файле — печатаются ровно один раз,
    а не отбрасываются навсегда при after_seq=0.
    """
    events: list[dict] = []
    for position, record in enumerate(records, start=1):
        seq = record.get("seq")
        effective_seq = seq if isinstance(seq, int) and not isinstance(seq, bool) and seq > 0 \
            else position
        if effective_seq <= after_seq:
            continue
        event = {**record, "_watch_seq": effective_seq}
        if record.get("author") == participant_id:
            events.append(event)
        elif (record.get("author") == "moderator"
              and _mentions_participant(record.get("content") or "", participant_id)):
            events.append(event)
    return events


def clear_screen() -> None:
    """Перерисовка кадра: ANSI-очистка только в TTY; в pipe — разделитель кадров."""
    if sys.stdout.isatty():
        sys.stdout.write("\033[2J\033[H")
    else:
        sys.stdout.write("\n" + "─" * 72 + "\n")

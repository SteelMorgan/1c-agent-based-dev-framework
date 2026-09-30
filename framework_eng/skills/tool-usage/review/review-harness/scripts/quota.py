"""Квотный выбор адаптера по usage-introspection CLI (RVSW-01, FR-14, AC-14; TD §10).

Источник квот — свежие файлы сессий CLI (локальная книга учёта запрещена,
FR-14). Доказанный introspection (TBD-01, probe 2026-07-29): только codex —
rollout-файлы `~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl`, последняя запись
с непустым `payload.rate_limits` (TD §10.1). Claude/kimi — `unavailable`.

Алгоритм (TD §10.4):
1. Floor-фильтр ПЕРЕД взвешиванием: enabled, id != id вызывающего,
   для gate-ролей — gate_legal. Пусто → fail-closed QuotaSelectionError (NFR-01).
2. Один кандидат → он; режим — weighted, если его introspection proven и
   данные свежие, иначе blind с записью причины.
3. Несколько кандидатов: weighted (argmax remaining_percent, детерминированный,
   запрет повтора последнего выбора) ТОЛЬКО если introspection proven и данные
   свежие у ВСЕХ кандидатов (решение 2026-07-29); иначе — blind-ротация
   (равномерная, запрет повтора) с quota_fallback_reason в observations.
4. Временная недоступность данных (файл не читается → причина `no data`;
   запись старше 24 ч по mtime rollout-файла → причина `stale`, F-07) →
   blind; выбор не блокируется.

Модуль — чистые предикаты + thin IO; он не знает о протоколах инструментов
(правило границы FR-01).
"""
from __future__ import annotations

import json
import time
from pathlib import Path

STALE_LIMIT_SEC = 24 * 3600  # staleness > 24 ч → данные недоступны (TD §10.1)

MODE_WEIGHTED = "weighted"
MODE_BLIND = "blind"

DEFAULT_CODEX_SESSIONS_DIR = Path.home() / ".codex" / "sessions"


class QuotaSelectionError(Exception):
    """Fail-closed отказ выбора: пустой пул после floor-фильтра (NFR-01).
    Self-review/нарушение capability floor запрещены."""


# ---------------------------------------------------------------------------
# Thin IO: чтение codex-квоты из rollout-файлов (TD §10.1)
# ---------------------------------------------------------------------------

def read_codex_quota(sessions_dir: Path, *, now: float | None = None) -> dict | None:
    """Квота codex: последний по mtime rollout-файл → последняя НЕПУСТАЯ
    запись payload.rate_limits. remaining = 100 − used_percent.

    → {"remaining_percent", "window_minutes", "resets_at", "plan_type",
       "recorded_at", "stale_sec", "rollout_path"}; None — каталог/файлы/записи
    отсутствуют или не читаются (временная недоступность → blind с причиной
    `no data`, TD §10.4 п.4). Свежесть данных — по mtime ЗАПИСИ rollout-файла
    (`recorded_at`), а не по resets_at (момент сброса окна): окно может быть
    давно сброшено при свежей записи и наоборот (R-Final F-07).
    `stale_sec` = now − recorded_at; оценивает вызывающая сторона.
    """
    now = time.time() if now is None else float(now)
    sessions_dir = Path(sessions_dir)
    if not sessions_dir.is_dir():
        return None
    try:
        rollouts = sorted(sessions_dir.rglob("rollout-*.jsonl"),
                          key=lambda p: (p.stat().st_mtime, p.name))
    except OSError:
        return None
    if not rollouts:
        return None
    newest = rollouts[-1]
    try:
        recorded_at = newest.stat().st_mtime
        lines = newest.read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    for line in reversed(lines):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            continue
        payload = record.get("payload") if isinstance(record, dict) else None
        if not isinstance(payload, dict):
            continue
        rate_limits = payload.get("rate_limits")
        if not isinstance(rate_limits, dict):
            continue
        primary = rate_limits.get("primary")
        if not isinstance(primary, dict) or primary.get("used_percent") is None:
            continue
        resets_at = float(primary.get("resets_at") or 0)
        return {
            "remaining_percent": 100.0 - float(primary["used_percent"]),
            "window_minutes": primary.get("window_minutes"),
            "resets_at": resets_at,
            "plan_type": rate_limits.get("plan_type"),
            "recorded_at": recorded_at,
            "stale_sec": max(0.0, now - recorded_at),
            "rollout_path": str(newest),
        }
    return None


def read_participant_quota(participant: dict, *, codex_sessions_dir: Path | None = None,
                           now: float | None = None) -> dict | None:
    """Диспетчер по способу introspection реестра v2 (TD §4): `codex-rollout`
    читается из rollout-файлов; `none` (claude/kimi, TD §10.2–10.3) — данных нет."""
    if participant.get("quota_introspection") == "codex-rollout":
        return read_codex_quota(codex_sessions_dir or DEFAULT_CODEX_SESSIONS_DIR, now=now)
    return None


# ---------------------------------------------------------------------------
# Чистый выбор (TD §10.4)
# ---------------------------------------------------------------------------

def _floor(participants: list[dict], *, caller_family: str, gate: bool) -> list[dict]:
    """Capability/cross-family floor ПЕРЕД взвешиванием (FR-14, решение
    владельца 2026-08-03): enabled, family участника != family вызывающего
    (не только другой literal id того же family); для gate-ролей — только
    gate_legal."""
    return [
        p for p in participants
        if p.get("enabled")
        and p.get("family") != caller_family
        and (not gate or p.get("gate_legal"))
    ]


def _fresh_quota(participant: dict, quota: dict, now: float) -> dict | None:
    """Свежие данные proven-кандидата; None — introspection не доказан,
    данные не читаются или протухли (запись старше STALE_LIMIT_SEC по mtime
    rollout-файла — поле recorded_at, R-Final F-07)."""
    if participant.get("quota_status") != "proven":
        return None
    record = (quota or {}).get(participant["id"])
    if record is None:
        return None
    if now - float(record.get("recorded_at") or 0) > STALE_LIMIT_SEC:
        return None
    return record


def select_adapter(participants: list[dict], *, caller_id: str, caller_family: str,
                   gate: bool = False, last_choice: str | None = None,
                   quota: dict | None = None, now: float | None = None) -> dict:
    """Выбор адаптера: floor → weighted (all-proven, свежие данные) | blind.

    caller_family — family вызывающего (решение владельца 2026-08-03): floor
    фильтрует по нему (participant.family != caller_family), а не по literal
    caller_id того же family. caller_id сохраняется только для диагностики /
    сообщений об отказе, а не как критерий фильтра.

    quota — {participant_id: record | None} (см. read_codex_quota); proven-
    кандидат без записи считается временно недоступным (причина `no data`),
    с протухшей записью — `stale` (F-07).
    → {"adapter_id", "quota_mode", "quota_fallback_reason", "candidates",
       "remaining_percent"}. Пустой пул → QuotaSelectionError (не подмена).
    """
    now = time.time() if now is None else float(now)
    candidates = _floor(participants, caller_family=caller_family, gate=gate)
    if not candidates:
        raise QuotaSelectionError(
            f"пустой пул после cross-family floor-фильтра "
            f"(caller_id={caller_id!r}, caller_family={caller_family!r}, gate={gate}); "
            f"кандидат того же family, что вызывающий, не eligible; "
            f"self-review запрещён (NFR-01)"
        )
    candidate_ids = [p["id"] for p in candidates]
    fresh = {p["id"]: _fresh_quota(p, quota, now) for p in candidates}

    # Причина blind-режима для observations (FR-14б): unavailable CLI из
    # реестра + proven-кандидаты с недоступными/протухшими данными.
    # F-07: протухшая запись (`stale`) и отсутствие данных (`no data`) —
    # разные причины, не смешиваются.
    unavailable = [p["cli"] for p in candidates if p.get("quota_status") != "proven"]
    stale = [p["id"] for p in candidates
             if p.get("quota_status") == "proven"
             and (quota or {}).get(p["id"]) is not None
             and fresh[p["id"]] is None]
    no_data = [p["id"] for p in candidates
               if p.get("quota_status") == "proven"
               and (quota or {}).get(p["id"]) is None]
    reason_parts = []
    if unavailable:
        reason_parts.append("introspection unavailable: " + ", ".join(unavailable))
    if stale:
        reason_parts.append("stale: " + ", ".join(stale))
    if no_data:
        reason_parts.append("no data: " + ", ".join(no_data))
    fallback_reason = "; ".join(reason_parts) or None

    all_fresh = all(fresh[pid] is not None for pid in candidate_ids)
    if all_fresh:
        # Weighted: детерминированный argmax remaining_percent (равенство —
        # порядок реестра), запрет повтора последнего выбора.
        ranked = sorted(candidates, key=lambda p: -fresh[p["id"]]["remaining_percent"])
        choice = ranked[0]
        if choice["id"] == last_choice and len(ranked) > 1:
            choice = ranked[1]
        mode = MODE_WEIGHTED
        fallback_reason = None
    else:
        # Blind: равномерная ротация по порядку реестра, запрет повтора.
        index = 0
        if last_choice in candidate_ids:
            index = (candidate_ids.index(last_choice) + 1) % len(candidate_ids)
        choice = candidates[index]
        mode = MODE_BLIND
    return {
        "adapter_id": choice["id"],
        "quota_mode": mode,
        "quota_fallback_reason": fallback_reason,
        "candidates": candidate_ids,
        "remaining_percent": {
            pid: fresh[pid]["remaining_percent"] for pid in candidate_ids if fresh[pid] is not None
        },
    }

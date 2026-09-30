"""Liveness-классификация участников (RVSW-01, FR-13, AC-13; TD §9).

Чистая классификация по каноническим полям активности адаптера
(`last_activity_at` — «CLI эмитит события», `last_heartbeat_at` —
«процесс-адаптер жив и следит за CLI»; оба уже ведутся всеми адаптерами,
новый таймстамп не вводится — TD §9.1).

Heartbeat — ДИАГНОСТИЧЕСКИЙ сигнал, не kill (RISK-05): модуль только
классифицирует; вмешательство — решение вызывающего Оркестратора.
Модуль не знает о протоколах инструментов (правило границы FR-01);
poller с периодом 15 с живёт на стороне инструмента (TD §9.2).

Классы (TD §9.2, порог тишины — параметр сессии, TBD-04):
- heartbeat старше HEARTBEAT_DEAD_THRESHOLD_SEC (10 с) → dead_watcher
  (жёсткий сигнал: watcher не жив; приоритет над тишиной активности);
- активность свежее silence_threshold_sec (120 с по умолчанию) → active;
- активность старше порога → quiet.
Границы: heartbeat «> 10» (ровно 10 — жив), активность «≤ 120» (ровно 120 — active).
Отсутствующие поля — fail-safe: heartbeat → dead_watcher, активность → quiet.
"""
from __future__ import annotations

import time
from datetime import datetime
from pathlib import Path

CLASS_ACTIVE = "active"                # «модель думает»: события идут
CLASS_QUIET = "quiet"                  # диагностический маркер тишины (НЕ kill, RISK-05)
CLASS_DEAD_WATCHER = "dead_watcher"    # процесс-адаптер не жив (жёсткий сигнал)

DEFAULT_SILENCE_THRESHOLD_SEC = 120.0  # порог тишины, TBD-04 (TD §9.2; --silence-threshold-sec)
HEARTBEAT_DEAD_THRESHOLD_SEC = 10.0    # каденс heartbeat 1 с ⇒ 10 с тишины = мёртвый watcher


def _ts_to_epoch(value) -> float | None:
    """ISO-8601 (формат адаптеров) или эпоха (int/float) → epoch seconds; битое → None."""
    if value is None:
        return None
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value).timestamp()
        except ValueError:
            return None
    return None


def classify_liveness(activity: dict, *, now: float | None = None,
                      silence_threshold_sec: float = DEFAULT_SILENCE_THRESHOLD_SEC,
                      heartbeat_threshold_sec: float = HEARTBEAT_DEAD_THRESHOLD_SEC) -> dict:
    """Классификация одного участника по полям активности (чистая функция).

    activity — dict с ключами ACTIVITY_FIELDS контракта (значения ISO-8601
    или epoch; None допустим). Возвращает диагностический снимок:
    {"class", "activity_age_sec", "heartbeat_age_sec", "silence_threshold_sec"}.
    """
    now = time.time() if now is None else float(now)
    heartbeat_ts = _ts_to_epoch(activity.get("last_heartbeat_at"))
    activity_ts = _ts_to_epoch(activity.get("last_activity_at"))
    heartbeat_age = None if heartbeat_ts is None else now - heartbeat_ts
    activity_age = None if activity_ts is None else now - activity_ts

    if heartbeat_age is None or heartbeat_age > heartbeat_threshold_sec:
        klass = CLASS_DEAD_WATCHER
    elif activity_age is None or activity_age > silence_threshold_sec:
        klass = CLASS_QUIET
    else:
        klass = CLASS_ACTIVE
    return {
        "class": klass,
        "activity_age_sec": activity_age,
        "heartbeat_age_sec": heartbeat_age,
        "silence_threshold_sec": float(silence_threshold_sec),
    }


def classify_participant(cwd: Path, review_id: str, **kwargs) -> dict:
    """Thin IO: поля активности через канонический контракт (T-03), затем
    чистая classify_liveness. Отсутствующий/битый runtime.json → dead_watcher
    (watcher не подтверждён)."""
    import adapter_contract

    activity = adapter_contract.read_participant_activity(Path(cwd), review_id)
    return classify_liveness(activity, **kwargs)

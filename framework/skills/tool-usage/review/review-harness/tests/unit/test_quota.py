"""HU-Q01..Q07 — квотный выбор адаптера (RVSW-01, T-05, FR-14, AC-14).

Решение владельца 2026-08-03 (cross-family gate policy): блокирующий
ревьюер обязан быть из ДРУГОГО семейства моделей относительно вызывающего,
а не просто другим id того же семейства (отменяет прежний identity-floor
«любой участник, кроме буквального caller»). Floor обязателен ПЕРЕД
взвешиванием и сравнивает `family`, а не только `id`; caller_id сохраняется
только для диагностики/сообщений об отказе, а не как критерий фильтра.

Тест-план §3.5 на фикстурах §2.2, TD §10 (обновлено под cross-family):
- floor-фильтр (enabled, family != family вызывающего, для gate —
  gate_legal) ПЕРЕД взвешиванием; пустой пул → fail-closed отказ (NFR-01);
- weighted ТОЛЬКО при all-proven пуле со свежими данными (решение 2026-07-29);
  иначе blind-ротация с записью причины; свежесть данных — по mtime записи
  rollout-файла (F-07): протухшая запись → причина `stale`, отсутствие
  данных → `no data`; выбор не блокируется;
- чтение codex-квоты: последний по mtime rollout-файл → последняя НЕПУСТАЯ
  запись rate_limits; remaining = 100 − used_percent (TD §10.1).
"""
from __future__ import annotations

import json
import os

import pytest

import quota

NOW = 1_800_000_000.0


def participant(pid: str, family: str, *, cli: str | None = None, enabled: bool = True,
                gate_legal: bool = True, introspection: str = "none",
                status: str = "unavailable") -> dict:
    """Запись реестра v2 (TD §4) минимального состава для quota.py."""
    return {
        "id": pid, "family": family, "model": "m", "adapter": "adapters/x.py",
        "cli": cli or family, "context_budget": 120000, "enabled": enabled,
        "gate_legal": gate_legal, "quota_introspection": introspection,
        "quota_status": status,
    }


def proven(pid: str, family: str, **kw) -> dict:
    return participant(pid, family, introspection="codex-rollout", status="proven", **kw)


def quota_record(remaining: float, *, resets_at: float = NOW + 3600.0,
                 recorded_at: float = NOW) -> dict:
    return {"remaining_percent": remaining, "window_minutes": 10080,
            "resets_at": resets_at, "plan_type": "pro",
            "recorded_at": recorded_at,
            "stale_sec": max(0.0, NOW - recorded_at)}


def rate_limits_line(used_percent: float, resets_at: float) -> dict:
    """Формат TD §10.1 (probe 2026-07-29): payload.rate_limits в записи token_count."""
    return {"type": "event_msg", "payload": {"type": "token_count", "rate_limits": {
        "limit_id": "codex",
        "primary": {"used_percent": used_percent, "window_minutes": 10080,
                    "resets_at": resets_at},
        "secondary": None, "plan_type": "pro"}}}


def write_rollout(sessions_dir, name: str, records: list, mtime: float):
    path = sessions_dir / "2026" / "07" / "29" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(json.dumps(r) for r in records) + "\n", encoding="utf-8")
    os.utime(path, (mtime, mtime))
    return path


# ---------- HU-Q01: floor-фильтр ПЕРЕД взвешиванием (cross-family) ----------

def test_hu_q01_floor_before_weighting():
    # Свой family исключается ЦЕЛИКОМ (не только literal caller): участник
    # того же family с максимальной квотой (claude-heavy) не eligible.
    caller = proven("claude-opus", "claude")
    same_family = proven("claude-heavy", "claude")
    cross = proven("codex-gpt", "codex")
    result = quota.select_adapter(
        [caller, same_family, cross], caller_id="claude-opus",
        caller_family="claude", now=NOW,
        quota={"claude-opus": quota_record(100.0),
               "claude-heavy": quota_record(99.0),
               "codex-gpt": quota_record(1.0)})
    assert result["adapter_id"] == "codex-gpt"
    assert result["candidates"] == ["codex-gpt"]
    # Disabled тоже отсекается до весов.
    disabled = proven("codex-off", "codex", enabled=False)
    result = quota.select_adapter(
        [disabled, cross], caller_id="claude-opus", caller_family="claude", now=NOW,
        quota={"codex-off": quota_record(99.0), "codex-gpt": quota_record(1.0)})
    assert result["candidates"] == ["codex-gpt"]


def test_hu_q01_gate_requires_gate_legal():
    kimi = participant("kimi-k2", "kimi", gate_legal=False)   # отключённый gate_legal (синтетика; в боевом реестре kimi легален — RULE_UPDATE 2026-07-30)
    codex = participant("codex-gpt", "codex")
    result = quota.select_adapter([kimi, codex], caller_id="claude-opus",
                                  caller_family="claude", gate=True, now=NOW)
    assert result["adapter_id"] == "codex-gpt"
    assert result["candidates"] == ["codex-gpt"]
    # Advisory-роль: gate_legal не фильтрует.
    result = quota.select_adapter([kimi, codex], caller_id="claude-opus",
                                  caller_family="claude", gate=False, now=NOW)
    assert set(result["candidates"]) == {"kimi-k2", "codex-gpt"}


def test_hu_q01b_kimi_selectable_for_gate_when_legal():
    # RULE_UPDATE 2026-07-30: kimi (k3, frontier) легален для gate-ролей.
    kimi = participant("kimi-k2", "kimi", gate_legal=True)
    result = quota.select_adapter([kimi], caller_id="claude-opus",
                                  caller_family="claude", gate=True, now=NOW)
    assert result["adapter_id"] == "kimi-k2"
    # Literal caller исключается; cross-family gate_legal участник остаётся.
    codex = participant("codex-gpt", "codex")
    result = quota.select_adapter([kimi, codex], caller_id="kimi-k2",
                                  caller_family="kimi", gate=True, now=NOW)
    assert result["adapter_id"] == "codex-gpt"


def test_hu_q01c_same_family_candidate_excluded_even_with_distinct_id():
    """Cross-family floor (владелец, 2026-08-03): участник ТОГО ЖЕ family,
    что вызывающий, не eligible даже при другом id — это отличие от прежнего
    identity-floor («любой, кроме буквального caller»)."""
    caller = proven("claude-opus", "claude")
    same_family_gate_legal = proven("claude-heavy", "claude")
    cross = proven("codex-gpt", "codex")
    result = quota.select_adapter(
        [caller, same_family_gate_legal, cross], caller_id="claude-opus",
        caller_family="claude", gate=True, now=NOW,
        quota={"claude-heavy": quota_record(99.0),
               "codex-gpt": quota_record(1.0)})
    assert result["adapter_id"] == "codex-gpt"
    assert result["candidates"] == ["codex-gpt"]
    assert "claude-heavy" not in result["candidates"]


# ---------- HU-Q02: пустой пул после floor → fail-closed отказ ----------

def test_hu_q02_empty_pool_fail_closed():
    caller = participant("claude-opus", "claude")
    with pytest.raises(quota.QuotaSelectionError):
        quota.select_adapter([caller], caller_id="claude-opus",
                             caller_family="claude", now=NOW)
    # Cross-family floor: участник ТОГО ЖЕ family (claude-sonnet) тоже
    # исключается — пул остаётся пуст, а не «разрешён, раз не literal caller».
    same_family = participant("claude-sonnet", "claude")
    with pytest.raises(quota.QuotaSelectionError):
        quota.select_adapter(
            [caller, same_family], caller_id="claude-opus",
            caller_family="claude", now=NOW)
    # Участник ДРУГОГО family спасает пул.
    cross = participant("codex-gpt", "codex")
    result = quota.select_adapter(
        [caller, same_family, cross], caller_id="claude-opus",
        caller_family="claude", now=NOW)
    assert result["adapter_id"] == "codex-gpt"
    assert result["candidates"] == ["codex-gpt"]
    # Disabled-кандидат другого family не спасает пул.
    cross_disabled = participant("codex-gpt", "codex", enabled=False)
    with pytest.raises(quota.QuotaSelectionError):
        quota.select_adapter([caller, cross_disabled], caller_id="claude-opus",
                             caller_family="claude", now=NOW)
    # Gate без gate_legal-кандидатов другого family — тоже отказ, не понижение floor.
    kimi = participant("kimi-k2", "kimi", gate_legal=False)
    with pytest.raises(quota.QuotaSelectionError):
        quota.select_adapter([caller, kimi], caller_id="claude-opus",
                             caller_family="claude", gate=True, now=NOW)


# ---------- HU-Q03: единственный кандидат — без взвешивания ----------

def test_hu_q03_single_candidate_blind_reason_recorded():
    claude = participant("claude-opus", "claude")  # introspection unavailable
    result = quota.select_adapter([claude], caller_id="codex-gpt",
                                  caller_family="codex", now=NOW)
    assert result["adapter_id"] == "claude-opus"
    assert result["quota_mode"] == "blind"
    assert "introspection unavailable" in result["quota_fallback_reason"]
    assert "claude" in result["quota_fallback_reason"]


def test_hu_q03_single_proven_candidate_is_weighted():
    """TD §10.4: единственный proven-кандидат (codex) — взвешенный режим фактически активен."""
    codex = proven("codex-gpt", "codex")
    result = quota.select_adapter(
        [codex], caller_id="claude-opus", caller_family="claude",
        gate=True, now=NOW,
        quota={"codex-gpt": quota_record(92.0)})
    assert result["adapter_id"] == "codex-gpt"
    assert result["quota_mode"] == "weighted"
    assert result["quota_fallback_reason"] is None


# ---------- HU-Q04: weighted при all-proven — argmax remaining, запрет повтора ----------

def test_hu_q04b_mixed_pool_proven_plus_unavailable_goes_blind():
    """F-003 (рой-ревью RULE_UPDATE 2026-07-30): боевая конфигурация реестра —
    proven codex + unavailable claude/kimi → blind с записью причины,
    weighted НЕ активируется (правило all-proven, TD §10.4)."""
    codex = proven("codex-gpt", "codex")
    claude = participant("claude-opus", "claude")  # introspection unavailable
    kimi = participant("kimi-k2", "kimi")          # introspection unavailable
    result = quota.select_adapter(
        [codex, claude, kimi], caller_id="kimi-k2", caller_family="kimi",
        gate=True, now=NOW,
        quota={"codex-gpt": quota_record(92.0)})
    assert result["quota_mode"] == "blind"
    assert "introspection unavailable" in result["quota_fallback_reason"]
    assert "claude" in result["quota_fallback_reason"]

def test_hu_q04_weighted_argmax_and_no_repeat():
    a = proven("codex-a", "codex")
    b = proven("codex-b", "gpt")
    data = {"codex-a": quota_record(92.0), "codex-b": quota_record(40.0)}
    result = quota.select_adapter([a, b], caller_id="claude-opus",
                                  caller_family="claude", now=NOW, quota=data)
    assert result["adapter_id"] == "codex-a"          # argmax remaining_percent
    assert result["quota_mode"] == "weighted"
    assert result["quota_fallback_reason"] is None
    # Запрет повтора последнего выбора — даже вопреки argmax (детерминировано).
    result = quota.select_adapter([a, b], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota=data, last_choice="codex-a")
    assert result["adapter_id"] == "codex-b"
    # last_choice вне пула не влияет на argmax.
    result = quota.select_adapter([a, b], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota=data, last_choice="kimi-k2")
    assert result["adapter_id"] == "codex-a"


def test_hu_q04_weighted_uses_fresh_rollout_data(tmp_path):
    """Связка с реальным чтением (фикстура codex_rollout_fresh, §2.2)."""
    sessions = tmp_path / ".codex" / "sessions"
    write_rollout(sessions, "rollout-fresh.jsonl",
                  [rate_limits_line(8.0, NOW + 5000.0)], mtime=NOW - 60.0)
    record = quota.read_codex_quota(sessions, now=NOW)
    assert record["remaining_percent"] == pytest.approx(92.0)   # 100 − used_percent
    assert record["window_minutes"] == 10080
    assert record["plan_type"] == "pro"
    assert record["stale_sec"] == pytest.approx(60.0)  # свежесть ЗАПИСИ: now − mtime (F-07)
    assert record["recorded_at"] == pytest.approx(NOW - 60.0)
    codex = proven("codex-gpt", "codex")
    result = quota.select_adapter([codex], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota={"codex-gpt": record})
    assert result["quota_mode"] == "weighted"


# ---------- HU-Q05: смешанный пул → blind-ротация с причиной ----------

def test_hu_q05_mixed_pool_blind_rotation_with_reason():
    codex = proven("codex-gpt", "codex")
    claude = participant("claude-opus", "claude")
    kimi = participant("kimi-k2", "kimi")
    pool = [codex, claude, kimi]
    data = {"codex-gpt": quota_record(95.0)}
    result = quota.select_adapter(pool, caller_id="gpt-caller",
                                  caller_family="gpt", now=NOW, quota=data)
    assert result["quota_mode"] == "blind"
    assert result["candidates"] == ["codex-gpt", "claude-opus", "kimi-k2"]  # порядок реестра
    reason = result["quota_fallback_reason"]
    assert "introspection unavailable" in reason
    assert "claude" in reason and "kimi" in reason and "codex" not in reason
    # Равномерная ротация с запретом повтора: следующий после last_choice.
    first = result["adapter_id"]
    assert first == "codex-gpt"
    result = quota.select_adapter(pool, caller_id="gpt-caller",
                                  caller_family="gpt", now=NOW, quota=data,
                                  last_choice=first)
    assert result["adapter_id"] == "claude-opus"
    result = quota.select_adapter(pool, caller_id="gpt-caller",
                                  caller_family="gpt", now=NOW, quota=data,
                                  last_choice="kimi-k2")
    assert result["adapter_id"] == "codex-gpt"  # круг замкнулся


# ---------- HU-Q06: staleness > 24 ч / файл не читается → blind + stale ----------

def test_hu_q06_stale_quota_falls_back_to_blind(tmp_path):
    sessions = tmp_path / ".codex" / "sessions"
    write_rollout(sessions, "rollout-stale.jsonl",
                  [rate_limits_line(5.0, NOW - 25 * 3600.0)], mtime=NOW - 25 * 3600.0)
    record = quota.read_codex_quota(sessions, now=NOW)
    assert record["stale_sec"] > quota.STALE_LIMIT_SEC  # протухшая ЗАПИСЬ (mtime, F-07)
    codex = proven("codex-gpt", "codex")
    result = quota.select_adapter([codex], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota={"codex-gpt": record})
    assert result["adapter_id"] == "codex-gpt"      # выбор НЕ блокируется
    assert result["quota_mode"] == "blind"
    assert "stale" in result["quota_fallback_reason"]


def test_hu_q06b_write_freshness_governs_not_window_reset(tmp_path):
    """F-07: staleness меряется по mtime rollout-файла (свежесть ЗАПИСИ), а не
    по resets_at (момент сброса окна). resets_at давно в прошлом + свежая запись
    → данные свежие; resets_at в будущем + протухшая запись → stale."""
    sessions = tmp_path / ".codex" / "sessions"
    # resets_at 48 ч назад (окно сброшено), но запись свежая → weighted.
    write_rollout(sessions, "rollout-reset-window.jsonl",
                  [rate_limits_line(10.0, NOW - 48 * 3600.0)], mtime=NOW - 60.0)
    record = quota.read_codex_quota(sessions, now=NOW)
    assert record["stale_sec"] < quota.STALE_LIMIT_SEC
    codex = proven("codex-gpt", "codex")
    result = quota.select_adapter([codex], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota={"codex-gpt": record})
    assert result["quota_mode"] == "weighted"
    assert result["quota_fallback_reason"] is None
    # resets_at в будущем (окно активно), но запись протухла (> 24 ч) → stale.
    stale_record = quota_record(90.0, resets_at=NOW + 3600.0,
                                recorded_at=NOW - 25 * 3600.0)
    result = quota.select_adapter([codex], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota={"codex-gpt": stale_record})
    assert result["quota_mode"] == "blind"
    assert "stale" in result["quota_fallback_reason"]


def test_hu_q06c_fallback_reason_distinguishes_stale_from_no_data():
    """F-07: диагностика quota_fallback_reason различает протухшую запись
    (`stale`) и отсутствие данных (`no data`) — не смешивает в одну причину."""
    stale_codex = proven("codex-stale", "codex")
    missing_codex = proven("codex-nodata", "gpt")
    data = {"codex-stale": quota_record(50.0, recorded_at=NOW - 25 * 3600.0),
            "codex-nodata": None}
    result = quota.select_adapter([stale_codex, missing_codex],
                                  caller_id="claude-opus", caller_family="claude",
                                  now=NOW, quota=data)
    assert result["quota_mode"] == "blind"
    reason = result["quota_fallback_reason"]
    assert "stale: codex-stale" in reason
    assert "no data: codex-nodata" in reason
    assert "stale: codex-nodata" not in reason


def test_hu_q06_unreadable_or_absent_rollout_is_stale(tmp_path):
    # Каталог сессий отсутствует (codex_rollout_absent).
    assert quota.read_codex_quota(tmp_path / "absent", now=NOW) is None
    # Каталог есть, rollout-файлов нет.
    empty = tmp_path / ".codex" / "sessions"
    empty.mkdir(parents=True)
    assert quota.read_codex_quota(empty, now=NOW) is None
    # Файл есть, но не читается как JSONL с rate_limits → None.
    write_rollout(empty, "rollout-garbage.jsonl", [{"broken": True}], mtime=NOW)
    assert quota.read_codex_quota(empty, now=NOW) is None
    # Proven-кандидат без читаемых данных → blind; причина — отсутствие данных
    # (`no data`), отличная от протухшей записи (`stale`) — F-07, TD §10.4 п.4.
    codex = proven("codex-gpt", "codex")
    result = quota.select_adapter([codex], caller_id="claude-opus",
                                  caller_family="claude", now=NOW,
                                  quota={"codex-gpt": None})
    assert result["quota_mode"] == "blind"
    assert "no data" in result["quota_fallback_reason"]


# ---------- HU-Q07: последний по mtime файл, последняя НЕПУСТАЯ rate_limits ----------

def test_hu_q07_multifile_last_nonempty_rate_limits(tmp_path):
    sessions = tmp_path / ".codex" / "sessions"
    # Старый файл: больший расход — его данные брать НЕЛЬЗЯ (не последний по mtime).
    write_rollout(sessions, "rollout-old.jsonl",
                  [rate_limits_line(50.0, NOW + 5000.0)], mtime=NOW - 3600.0)
    # Свежий файл: последняя запись БЕЗ rate_limits — берётся предыдущая непустая.
    write_rollout(sessions, "rollout-new.jsonl", [
        rate_limits_line(8.0, NOW + 5000.0),
        {"type": "event_msg", "payload": {"type": "token_count"}},
        {"type": "response_item", "payload": {"type": "message"}},
    ], mtime=NOW - 60.0)
    record = quota.read_codex_quota(sessions, now=NOW)
    assert record["remaining_percent"] == pytest.approx(92.0)
    assert record["rollout_path"].endswith("rollout-new.jsonl")

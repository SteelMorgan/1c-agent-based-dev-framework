"""SU-V — туры 2–4: вердикты, стоп-правила, анонимность, досрочная остановка
(RVSW-01, T-09, FR-07/AC-07, TD §5.2–5.3, §6.3.5–6.3.6).

Покрытие test-plan SU-V01..V08:
- SU-V01: потолок 2 обмена на находку: второй ответ автора / третья волна
  вотумов отклоняются (fail-closed);
- SU-V02: новизна evidence: повтор (path, line, нормализованный quote) в треде
  отклоняется (stale_evidence);
- SU-V03: новые находки в турах 2–4 запрещены в треде — маршрутизируются в
  общий пул (только тур 1 и re-review порождают находки);
- SU-V04: все upheld → досрочное подтверждение, туры 3–4 не планируются;
- SU-V05: ответ автора maintain/withdraw/accept_reclassify — ровно один ход;
  counter_evidence обязателен при maintain;
- SU-V06: неснятое disagreement после потолка → contested;
- SU-V07: reclassify требует заполненного блока reclassify; evidence обязателен
  всегда и резолвится в проверяемый набор;
- SU-V08: анонимизация payload туров 2–4: anon_id вместо author_id, утечки
  реального id нет; anon_map стабилен в сессии.
"""
from __future__ import annotations

import json

import pytest

import structured
import swarm_core

CHECKED_PATHS = ["services/x/y.py", "README.md"]
ANON_MAP = {"claude-opus": "M1", "codex-gpt": "M2", "kimi-k2": "M3"}


@pytest.fixture
def checked_set(tmp_path):
    """tmp-дерево проверяемого набора: y.py — 200 строк, README.md — 2 строки."""
    root = tmp_path / "review-set"
    (root / "services/x").mkdir(parents=True)
    (root / "services/x/y.py").write_text(
        "".join(f"line {i}\n" for i in range(1, 201)), encoding="utf-8"
    )
    (root / "README.md").write_text("one\ntwo\n", encoding="utf-8")
    return swarm_core.checked_set_from_paths(root, CHECKED_PATHS)


def _finding(**over):
    base = {
        "finding_id": "F-001",
        "author_id": "claude-opus",
        "location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
        "category": "correctness",
        "severity": "P2",
        "in_lens": True,
        "claim": "деление на ноль при пустом списке",
        "evidence": "services/x/y.py:127 — total / len(items)",
        "rationale": "",
    }
    base.update(over)
    return base


def _verdict_raw(fid="F-001", verdict="upheld", path="services/x/y.py",
                 line=150, quote="total / len(items)", **over):
    raw = {
        "finding_id": fid,
        "verdict": verdict,
        "evidence": {"path": path, "line": line, "quote": quote},
    }
    raw.update(over)
    return raw


def _verdict(checked, fid="F-001", verdict="upheld", voter="codex-gpt", **over):
    return swarm_core.validate_verdict(
        _verdict_raw(fid=fid, verdict=verdict, **over), checked, voter_id=voter
    )


def _attack(checked, specs):
    """Тур 2: список (voter, verdict, **over) → (thread, verdicts)."""
    thread = swarm_core.start_thread(_finding())
    prior = swarm_core.thread_evidence(thread)
    verdicts = [
        swarm_core.validate_verdict(
            _verdict_raw(verdict=verdict, **over), checked, prior, voter_id=voter
        )
        for voter, verdict, *rest in specs
        for over in (rest[0] if rest else {},)
    ]
    return swarm_core.apply_verdict_wave(thread, verdicts), verdicts


# ---------- Парсинг/валидация вердиктов тура 2 (enum + evidence) ----------

def test_verdict_enum_values_accepted(checked_set):
    """Все четыре значения enum TD §5.2 принимаются (reclassify — с блоком)."""
    for verdict in ("upheld", "overruled", "uncertain"):
        out = _verdict(checked_set, verdict=verdict)
        assert out["verdict"] == verdict
        assert out["voter_id"] == "codex-gpt"
    out = _verdict(
        checked_set, verdict="reclassify",
        reclassify={"category": "performance", "severity": "P3"},
    )
    assert out["reclassify"] == {"category": "performance", "severity": "P3"}


def test_verdict_unknown_value_rejected(checked_set):
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, verdict="looks_good")
    assert exc.value.reason == "invalid_verdict"


def test_verdict_evidence_resolves_against_checked_set(checked_set):
    """Evidence обязателен и резолвится: путь вне набора / строка за границами —
    fail-closed (TD §5.2, AC-07)."""
    out = _verdict(checked_set, line=123, quote="total / len(items)")
    assert out["evidence"] == {
        "path": "services/x/y.py", "line": 123, "quote": "total / len(items)",
    }
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, path="services/x/zz.py")
    assert exc.value.reason == "invalid_evidence"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, line=201)
    assert exc.value.reason == "invalid_evidence"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, evidence=None)
    assert exc.value.reason == "missing_field:evidence"


def test_su_v07_reclassify_requires_block(checked_set):
    """SU-V07: verdict=reclassify без заполненного блока reclassify отклоняется;
    допустимо одно из полей (category или severity)."""
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, verdict="reclassify")
    assert exc.value.reason == "missing_field:reclassify"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, verdict="reclassify", reclassify={})
    assert exc.value.reason == "missing_field:reclassify"
    out = _verdict(checked_set, verdict="reclassify",
                   reclassify={"severity": "p3"})
    assert out["reclassify"] == {"severity": "P3"}
    with pytest.raises(swarm_core.MoveRejected) as exc:
        _verdict(checked_set, verdict="reclassify",
                 reclassify={"category": "unknown-cat"})
    assert exc.value.reason == "invalid_category"


def test_verdict_finding_mismatch_rejected(checked_set):
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_verdict(
            _verdict_raw(fid="F-002"), checked_set,
            voter_id="codex-gpt", expected_finding_id="F-001",
        )
    assert exc.value.reason == "finding_mismatch"


# ---------- SU-V02: новизна evidence ----------

def test_su_v02_stale_evidence_rejected(checked_set):
    """SU-V02: повтор (path, line, нормализованный quote) треда отклоняется;
    новый quote или новая line — принимается."""
    first = _verdict(checked_set, verdict="overruled",
                     line=127, quote="total / len(items)")
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_verdict(
            _verdict_raw(verdict="overruled", line=127,
                         quote="total / len(items)"),
            checked_set, [first["evidence"]], voter_id="kimi-k2",
        )
    assert exc.value.reason == "stale_evidence"
    # Нормализация quote: лишние пробелы/переводы строк не обходят предикат.
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_verdict(
            _verdict_raw(verdict="overruled", line=127,
                         quote="  total /\n  len(items) "),
            checked_set, [first["evidence"]], voter_id="kimi-k2",
        )
    assert exc.value.reason == "stale_evidence"
    # Новое evidence — ход легален.
    out = swarm_core.validate_verdict(
        _verdict_raw(verdict="overruled", line=128, quote="len(items) == 0"),
        checked_set, [first["evidence"]], voter_id="kimi-k2",
    )
    assert out["evidence"]["line"] == 128


def test_su_v02_author_counter_evidence_novelty(checked_set):
    """SU-V02: предикат новизны действует и на counter_evidence тура 3."""
    attack = _verdict(checked_set, verdict="overruled",
                      line=127, quote="total / len(items)")
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_author_response(
            {"finding_id": "F-001", "response": "maintain",
             "counter_evidence": {"path": "services/x/y.py", "line": 127,
                                  "quote": "total / len(items)"}},
            checked_set, [attack["evidence"]],
        )
    assert exc.value.reason == "stale_evidence"


# ---------- SU-V04: all-upheld → досрочное confirmed ----------

def test_su_v04_all_upheld_early_confirmed(checked_set):
    """SU-V04: все upheld в туре 2 → confirmed, туры 3–4 не планируются
    (next_move is None); любые дальнейшие ходы отклоняются."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "upheld", {"line": 140, "quote": "q1"}),
        ("kimi-k2", "upheld", {"line": 141, "quote": "q2"}),
    ])
    assert swarm_core.thread_status(thread) == "confirmed"
    assert swarm_core.next_move(thread) is None
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.apply_author_response(thread, {
            "finding_id": "F-001", "response": "withdraw",
        })
    assert exc.value.reason == "thread_closed"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.apply_verdict_wave(
            thread, [_verdict(checked_set, line=142, quote="q3")]
        )
    assert exc.value.reason == "thread_closed"


# ---------- SU-V05: тур 3 — ровно один ход автора ----------

def test_su_v05_author_response_enum_and_single_move(checked_set):
    """SU-V05: enum maintain/withdraw/accept_reclassify; второй ответ автора
    отклоняется; counter_evidence обязателен при maintain."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "upheld", {"line": 140, "quote": "q1"}),
        ("kimi-k2", "overruled", {"line": 141, "quote": "q2"}),
    ])
    assert swarm_core.next_move(thread) == "author_response"
    # maintain без counter_evidence — fail-closed.
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_author_response(
            {"finding_id": "F-001", "response": "maintain"}, checked_set,
            swarm_core.thread_evidence(thread),
        )
    assert exc.value.reason == "missing_field:counter_evidence"
    # Неизвестный ответ — fail-closed.
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.validate_author_response(
            {"finding_id": "F-001", "response": "appeal"}, checked_set,
            swarm_core.thread_evidence(thread),
        )
    assert exc.value.reason == "invalid_response"
    # maintain с новым counter_evidence — принят; далее только тур 4.
    response = swarm_core.validate_author_response(
        {"finding_id": "F-001", "response": "maintain",
         "counter_evidence": {"path": "services/x/y.py", "line": 55,
                              "quote": "if not items: return 0"}},
        checked_set, swarm_core.thread_evidence(thread),
    )
    thread2 = swarm_core.apply_author_response(thread, response)
    assert swarm_core.next_move(thread2) == "tour4"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.apply_author_response(thread2, {
            "finding_id": "F-001", "response": "withdraw",
        })
    assert exc.value.reason == "move_limit"


def test_su_v05_withdraw_and_accept_reclassify_close_thread(checked_set):
    """SU-V05: withdraw → withdrawn; accept_reclassify → reclassified; тред
    закрывается без тура 4."""
    for response, status in (("withdraw", "withdrawn"),
                             ("accept_reclassify", "reclassified")):
        thread, _ = _attack(checked_set, [
            ("codex-gpt", "overruled", {"line": 140, "quote": "q1"}),
        ])
        thread2 = swarm_core.apply_author_response(
            thread, {"finding_id": "F-001", "response": response}
        )
        assert swarm_core.thread_status(thread2) == status
        assert swarm_core.next_move(thread2) is None


# ---------- SU-V01: потолок 2 обмена; тур 4 — один вотум на участника ----------

def test_su_v01_exchange_cap_fail_closed(checked_set):
    """SU-V01: потолок 2 обмена после первичной атаки (ответ автора + финальный
    вотум); третья волна вотумов невозможна (fail-closed)."""
    assert swarm_core.MAX_EXCHANGES_AFTER_ATTACK == 2
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "overruled", {"line": 140, "quote": "q1"}),
    ])
    thread = swarm_core.apply_author_response(thread, {
        "finding_id": "F-001", "response": "maintain",
        "counter_evidence": {"path": "services/x/y.py", "line": 55,
                             "quote": "if not items: return 0"},
    })
    assert thread["exchanges"] == 1
    thread = swarm_core.apply_verdict_wave(thread, [
        _verdict(checked_set, line=160, quote="q3"),
    ])
    assert thread["exchanges"] == 2
    assert swarm_core.next_move(thread) is None
    # Третий обмен (и волна, и ответ автора) — fail-closed.
    with pytest.raises(swarm_core.MoveRejected):
        swarm_core.apply_verdict_wave(thread, [
            _verdict(checked_set, line=161, quote="q4"),
        ])
    with pytest.raises(swarm_core.MoveRejected):
        swarm_core.apply_author_response(thread, {
            "finding_id": "F-001", "response": "withdraw",
        })


def test_su_v01_one_final_vote_per_participant(checked_set):
    """SU-V01: тур 4 — ровно один финальный вотум на участника; дубль голоса
    в волне отклоняется; автор находки не голосует."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "overruled", {"line": 140, "quote": "q1"}),
    ])
    thread = swarm_core.apply_author_response(thread, {
        "finding_id": "F-001", "response": "maintain",
        "counter_evidence": {"path": "services/x/y.py", "line": 55,
                             "quote": "if not items: return 0"},
    })
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.apply_verdict_wave(thread, [
            _verdict(checked_set, voter="codex-gpt", line=160, quote="q3"),
            _verdict(checked_set, voter="codex-gpt", line=161, quote="q4"),
        ])
    assert exc.value.reason == "duplicate_vote"
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.apply_verdict_wave(thread, [
            _verdict(checked_set, voter="claude-opus", line=160, quote="q3"),
        ])
    assert exc.value.reason == "author_vote"


def test_thread_functions_pure(checked_set):
    """NFR-04: apply_* не мутируют входной тред (чистые предикаты)."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "overruled", {"line": 140, "quote": "q1"}),
    ])
    snapshot = json.dumps(thread, sort_keys=True, default=list)
    swarm_core.apply_author_response(thread, {
        "finding_id": "F-001", "response": "withdraw",
    })
    assert json.dumps(thread, sort_keys=True, default=list) == snapshot


# ---------- SU-V06: contested ----------

def test_su_v06_unresolved_disagreement_contested(checked_set):
    """SU-V06: неснятое disagreement после тура 4 → статус contested."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "overruled", {"line": 140, "quote": "q1"}),
        ("kimi-k2", "upheld", {"line": 141, "quote": "q2"}),
    ])
    thread = swarm_core.apply_author_response(thread, {
        "finding_id": "F-001", "response": "maintain",
        "counter_evidence": {"path": "services/x/y.py", "line": 55,
                             "quote": "if not items: return 0"},
    })
    thread = swarm_core.apply_verdict_wave(thread, [
        _verdict(checked_set, voter="codex-gpt", verdict="overruled",
                 line=160, quote="q3"),
        _verdict(checked_set, voter="kimi-k2", verdict="upheld",
                 line=161, quote="q4"),
    ])
    assert swarm_core.thread_status(thread) == "contested"


def test_su_v06_tour4_all_upheld_still_confirms(checked_set):
    """SU-V06 (граница): финальный вотум снял disagreement → confirmed."""
    thread, _ = _attack(checked_set, [
        ("codex-gpt", "uncertain", {"line": 140, "quote": "q1"}),
    ])
    thread = swarm_core.apply_author_response(thread, {
        "finding_id": "F-001", "response": "maintain",
        "counter_evidence": {"path": "services/x/y.py", "line": 55,
                             "quote": "if not items: return 0"},
    })
    thread = swarm_core.apply_verdict_wave(thread, [
        _verdict(checked_set, voter="codex-gpt", verdict="upheld",
                 line=160, quote="q3"),
    ])
    assert swarm_core.thread_status(thread) == "confirmed"


# ---------- SU-V03: запрет новых находок в турах 2–4 ----------

def test_su_v03_new_findings_routed_out_of_thread(checked_set):
    """SU-V03: structured-блок с новой находкой внутри ответа туров 2–4 не
    попадает в тред; находка маршрутизируется в общий пул (сырые записи для
    validate_findings)."""
    text = (
        "Возражение по находке.\n"
        "```swarm-verdict\n"
        + json.dumps(_verdict_raw(verdict="overruled", line=127,
                                  quote="total / len(items)"),
                     ensure_ascii=False)
        + "\n```\nПопутно заметил ещё баг:\n"
        "```swarm-structured\n"
        + json.dumps({"findings": [{
            "location": {"path": "services/x/y.py", "line_start": 10},
            "category": "security", "severity": "P1", "in_lens": False,
            "claim": "hardcoded secret", "evidence": "services/x/y.py:10",
        }]}, ensure_ascii=False)
        + "\n```\n"
    )
    raw, routed = swarm_core.parse_verdict_move(text)
    verdict = swarm_core.validate_verdict(raw, checked_set, voter_id="codex-gpt")
    assert verdict["verdict"] == "overruled"
    # Новая находка — НЕ часть треда; уходит в общий пул с нумерацией пула.
    assert len(routed) == 1
    assert "finding_id" not in routed[0]  # id присваивает пул, не тред
    accepted, rejected = swarm_core.validate_findings(
        routed, checked_set, author_id="codex-gpt", start_index=2
    )
    assert rejected == []
    assert accepted[0]["finding_id"] == "F-002"


def test_su_v03_findings_in_author_response_routed(checked_set):
    """SU-V03: то же для тура 3 — находки в ответе автора маршрутизируются в пул."""
    text = (
        "```swarm-author-response\n"
        + json.dumps({"finding_id": "F-001", "response": "withdraw"},
                     ensure_ascii=False)
        + "\n```\n```swarm-structured\n"
        + json.dumps({"findings": [{
            "location": {"path": "README.md", "line_start": 1},
            "category": "tests", "severity": "P5", "in_lens": False,
            "claim": "заметка", "evidence": "README.md:1",
        }]}, ensure_ascii=False)
        + "\n```\n"
    )
    raw, routed = swarm_core.parse_author_response_move(text)
    assert raw["response"] == "withdraw"
    assert len(routed) == 1


def test_move_block_missing_fail_closed(checked_set):
    """NFR-01: ответ без structured-блока хода — fail-closed (retry на уровне CLI)."""
    with pytest.raises(swarm_core.MoveRejected) as exc:
        swarm_core.parse_verdict_move("просто текст без блока")
    assert exc.value.reason == "missing_block"


# ---------- SU-V08: анонимность payload туров 2–4 ----------

def test_su_v08_tour_payload_anonymized(checked_set):
    """SU-V08: payload атакующего содержит anon_id, НЕ содержит author_id;
    утечка реального id — красный тест."""
    payload = swarm_core.tour_payload(_finding(), ANON_MAP)
    assert payload["anon_id"] == "M1"
    assert "author_id" not in payload
    assert "claude-opus" not in json.dumps(payload, ensure_ascii=False)
    # Неизвестный автор — fail-closed (граница анонимизации CONS-01 RISK-07).
    with pytest.raises(ValueError):
        swarm_core.tour_payload(_finding(author_id="ghost"), ANON_MAP)


def test_su_v08_objections_payload_anonymized(checked_set):
    """SU-V08: агрегированные возражения для автора (тур 3) — голоса под
    anon_id, реальные voter_id не утекают."""
    verdicts = [
        _verdict(checked_set, voter="codex-gpt", verdict="overruled",
                 line=140, quote="q1"),
        _verdict(checked_set, voter="kimi-k2", verdict="uncertain",
                 line=141, quote="q2"),
    ]
    payload = swarm_core.objections_payload(verdicts, ANON_MAP)
    assert [v["anon_id"] for v in payload] == ["M2", "M3"]
    blob = json.dumps(payload, ensure_ascii=False)
    assert "codex-gpt" not in blob and "kimi-k2" not in blob
    assert "voter_id" not in blob


def test_su_v08_anon_map_stable_in_session():
    """SU-V08: anon_map стабилен при фиксированном seed (механизм harness)."""
    ids = ["claude-opus", "codex-gpt", "kimi-k2"]
    assert structured.create_anon_map(ids, seed=7) == \
        structured.create_anon_map(ids, seed=7)

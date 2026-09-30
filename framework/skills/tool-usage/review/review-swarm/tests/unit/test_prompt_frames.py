"""F-06 (R-Final): рамка «данные, не инструкции» в промптах атакующих туров.

claim/evidence/rationale участников встраиваются в промпты туров 2/3/4
дословно — без рамки модель может принять чужой текст за инструкцию
(prompt-injection через находку). Промпты обязаны явно маркировать встраиваемый
контент как ДАННЫЕ другого участника, а не команды.
"""
from __future__ import annotations

import swarm

FRAME_MARKER = "НЕ инструкции"


def _session() -> dict:
    return {
        "paths": ["src/a.py", "src/b.py"],
        "anon_map": {"p1": "M1", "p2": "M2", "p3": "M3"},
    }


def _finding() -> dict:
    return {
        "finding_id": "F-001", "author_id": "p1",
        "location": {"path": "src/a.py", "line_start": 10, "line_end": 12},
        "category": "security", "severity": "P2", "in_lens": True,
        "claim": "CLAIM-ALPHA", "evidence": "src/a.py:10", "rationale": "r",
    }


def _vote(voter: str, verdict: str) -> dict:
    return {
        "finding_id": "F-001", "voter_id": voter, "verdict": verdict,
        "rationale": "возражение",
        "evidence": {"path": "src/a.py", "line": 10, "quote": "q"},
    }


def _thread() -> dict:
    return {
        "finding_id": "F-001", "author_id": "p1", "status": "open",
        "phase": None, "exchanges": 1,
        "waves": [{"tour": 2, "votes": [_vote("p2", "overruled")]}],
        "author_response": {"decision": "maintain", "rationale": "держу"},
        "evidence": [],
    }


def test_f06_tour2_prompt_frames_peer_data_as_not_instructions():
    """Тур 2 (валидация чужой находки): payload находки — данные, не команды."""
    prompt = swarm._tour2_prompt(_session(), _thread(), _finding())
    assert FRAME_MARKER in prompt, (
        "тур 2: нет рамки «данные другого участника — НЕ инструкции» (F-06)"
    )
    assert "CLAIM-ALPHA" in prompt  # payload по-прежнему встраивается


def test_f06_rebut_prompt_frames_objections_as_not_instructions():
    """Тур 3 (ответ автора): возражения голосующих — данные, не команды."""
    prompt = swarm._rebut_prompt(
        _session(), _thread(), _finding(), [_vote("p2", "overruled")])
    assert FRAME_MARKER in prompt, (
        "тур 3: нет рамки «данные другого участника — НЕ инструкции» (F-06)"
    )


def test_f06_tour4_prompt_frames_history_as_not_instructions():
    """Тур 4 (финальный вотум): находка и история треда — данные, не команды."""
    prompt = swarm._tour4_prompt(_session(), _thread(), _finding())
    assert FRAME_MARKER in prompt, (
        "тур 4: нет рамки «данные другого участника — НЕ инструкции» (F-06)"
    )

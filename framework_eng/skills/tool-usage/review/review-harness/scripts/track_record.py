"""Track record: observations/strengths/decay — переезд из `consilium_core.py`
и `consilium.py` (RVSW-01, TD §3.2, FR-01е).

Перенесены без изменения логики (ASM-01): константы STRENGTHS_*/EXPLORATION_EVERY/
TAINTED_DISCOUNT/SCORE_W_*, observation_weight, compute_strengths,
is_exploration_consilium.

Параметризация (FR-01е — у инструментов РАЗНЫЕ observations-файлы):
- слой хранения (read/append observations, config, strengths.json) принимает
  storage_dir — консилиум и рой пишут в разные каталоги
  (.consilium-track-record/ и .swarm-track-record/ соответственно);
- compute_strengths принимает seq_field (дефолт "consilium_seq" — совместимость;
  рой ведёт свой счётчик сессий).
Сборка observation из состояния сессии — протокол инструмента, остаётся в ядрах.
"""
from __future__ import annotations

import json
from pathlib import Path

STRENGTHS_FLOOR = 3            # проекция применяется при n_eff >= 3
STRENGTHS_HALF_LIFE = 8.0      # half-life 8 сессий
EXPLORATION_EVERY = 4          # каждая 4-я сессия — exploration
TAINTED_DISCOUNT = 0.5         # дисконт tainted-наблюдений (напр. «модератор = участник»)
SCORE_W_ACCEPT = 0.6
SCORE_W_UPHELD = 0.4

OBSERVATIONS_NAME = "observations.jsonl"
STRENGTHS_NAME = "strengths.json"
CONFIG_NAME = "config.json"


# ---------------------------------------------------------------------------
# Decay и проекция strengths
# ---------------------------------------------------------------------------

def observation_weight(k: int, half_life: float = STRENGTHS_HALF_LIFE) -> float:
    """Recency decay: w = 0.5^(k/half_life), k — сколько сессий назад."""
    return 0.5 ** (k / half_life)


def compute_strengths(observations: list[dict], seq_field: str = "consilium_seq") -> dict:
    """Read-only проекция strengths из track record.

    Ключ результата: (participant_id, role). Сегрегация tainted-наблюдений
    (напр. «модератор = участник»): исключены при >= FLOOR clean, иначе
    подключаются с дисконтом x0.5. seq_field — поле порядкового номера сессии
    (у инструментов разные счётчики, FR-01е).
    """
    if not observations:
        return {}
    max_seq = max(int(o.get(seq_field, 0)) for o in observations)
    groups: dict[tuple[str, str], list[dict]] = {}
    for obs in observations:
        key = (obs["participant_id"], obs["role"])
        groups.setdefault(key, []).append(obs)

    result = {}
    for key, group in groups.items():
        clean = [o for o in group if not o.get("moderator_is_participant")]
        tainted = [o for o in group if o.get("moderator_is_participant")]

        def weight_of(obs: dict) -> float:
            return observation_weight(max_seq - int(obs.get(seq_field, 0)))

        if len(clean) >= STRENGTHS_FLOOR:
            effective = [(weight_of(o), o) for o in clean]
            n_eff = float(len(clean))
        else:
            effective = [(weight_of(o), o) for o in clean]
            effective += [(weight_of(o) * TAINTED_DISCOUNT, o) for o in tainted]
            n_eff = len(clean) + TAINTED_DISCOUNT * len(tainted)

        def rate(num_field: str, den_field: str) -> float | None:
            numerator = sum(w * float(o.get(num_field, 0)) for w, o in effective)
            denominator = sum(
                w * (float(o.get(num_field, 0)) + float(o.get(den_field, 0))) for w, o in effective
            )
            if denominator == 0:
                return None
            return numerator / denominator

        accept_rate = rate("findings_accepted", "findings_withdrawn")
        upheld_rate = rate("critiques_upheld", "critiques_overruled")
        if accept_rate is None and upheld_rate is None:
            score = 0.0
        elif accept_rate is None:
            score = upheld_rate
        elif upheld_rate is None:
            score = accept_rate
        else:
            score = SCORE_W_ACCEPT * accept_rate + SCORE_W_UPHELD * upheld_rate

        result[key] = {
            "score": score,
            "n_eff": n_eff,
            "accept_rate": accept_rate,
            "upheld_rate": upheld_rate,
            "n_clean": len(clean),
            "n_tainted": len(tainted),
        }
    return result


def is_exploration_consilium(consiliums_completed: int, every: int = EXPLORATION_EVERY) -> bool:
    """Exploration-бюджет: каждая every-я сессия — вразрез статистике."""
    return (consiliums_completed + 1) % every == 0


# ---------------------------------------------------------------------------
# Слой хранения (параметризован storage_dir, FR-01е)
# ---------------------------------------------------------------------------

def read_track_config(storage_dir: Path) -> dict:
    """config.json каталога track record; отсутствует → {} (счётчики инструмента)."""
    path = Path(storage_dir) / CONFIG_NAME
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def write_track_config(storage_dir: Path, config: dict) -> None:
    storage = Path(storage_dir)
    storage.mkdir(parents=True, exist_ok=True)
    (storage / CONFIG_NAME).write_text(
        json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def bump_counter(storage_dir: Path, field: str) -> int:
    """Монотонный инкремент именованного счётчика сессий в config.json
    (consiliums_completed / reviews_completed / light_reviews_completed)."""
    config = read_track_config(storage_dir)
    value = int(config.get(field, 0)) + 1
    config[field] = value
    write_track_config(storage_dir, config)
    return value


def read_observations(storage_dir: Path) -> list[dict]:
    """Все observations каталога (observations.jsonl); отсутствует → []."""
    path = Path(storage_dir) / OBSERVATIONS_NAME
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def append_observations(storage_dir: Path, observations: list[dict]) -> None:
    """Append-only запись наблюдений (по одной на участника на сессию — решает ядро)."""
    storage = Path(storage_dir)
    storage.mkdir(parents=True, exist_ok=True)
    with (storage / OBSERVATIONS_NAME).open("a", encoding="utf-8") as f:
        for observation in observations:
            f.write(json.dumps(observation, ensure_ascii=False) + "\n")


def regenerate_strengths(storage_dir: Path, seq_field: str = "consilium_seq") -> dict:
    """Пересчёт strengths.json из observations (источник истины — observations;
    strengths.json — генерируемый кэш/отчёт). Формат ключа: «pid|role»."""
    strengths = compute_strengths(read_observations(storage_dir), seq_field=seq_field)
    serialized = {
        f"{pid}|{role}": {
            "score": round(entry["score"], 6),
            "n_eff": entry["n_eff"],
            "accept_rate": None if entry["accept_rate"] is None else round(entry["accept_rate"], 6),
            "upheld_rate": None if entry["upheld_rate"] is None else round(entry["upheld_rate"], 6),
        }
        for (pid, role), entry in strengths.items()
    }
    storage = Path(storage_dir)
    storage.mkdir(parents=True, exist_ok=True)
    (storage / STRENGTHS_NAME).write_text(
        json.dumps(serialized, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return serialized

"""Guard-тесты боевой карты критичности (CMAP-01, вердикт
cons-20260730-065455-3dc36dc4: E2, E8, E9, E10, RT-01..RT-03).

Проверяют исполняемый артефакт references/criticality-map.md как control-plane
инвариант:

- load-test (E2): карта обязана загружаться ядром без CliError и без
  warnings — иначе это инцидент доступности всего gate-конвейера;
- write-time mirror check (E9 + RT-01/02): каждый зеркальный glob
  (.framework/, .claude/) резолвится минимум в один git regular file
  (режимы 100644/100755, НЕ 120000); правило — только к зеркальным glob'ам,
  спящий secrets/** и семантические `**/...` резолвиться не обязаны (RT-03);
- точный duplicate glob с разными линзами — запрещён (E8, fail-closed);
- поимённо спящий secrets/** присутствует — защита от удаления при
  регенерации (E10);
- узкий glob managed-secret-families.yaml выигрывает у широкого
  infra/platform-registry/** (RT-05: порядок строк = приоритет линзы).

Тесты читают репозиторий (git ls-files), но ничего не пишут.
"""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

import swarm

TESTS_DIR = Path(__file__).resolve().parent
SKILL_DIR = TESTS_DIR.parents[1]
REPO_ROOT = SKILL_DIR.parents[2]
MAP_PATH = SKILL_DIR / "references" / "criticality-map.md"

MIRROR_PREFIXES = (".framework/", ".claude/")
REGULAR_FILE_MODES = {"100644", "100755"}
SYMLINK_MODE = "120000"

GLOB_LINE = re.compile(r"^-\s+`(?P<pattern>[^`]+)`\s*(?:→|->)\s*(?P<lens>\S+)\s*$")


def _git_ls_files_s(pathspec: str) -> list[tuple[str, str]]:
    """git ls-files -s по pathspec → [(mode, path)]."""
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "ls-files", "-s", "--", pathspec],
        capture_output=True, text=True, check=True,
    )
    entries = []
    for line in result.stdout.splitlines():
        meta, _, path = line.partition("\t")
        mode = meta.split()[0]
        entries.append((mode, path))
    return entries


def _literal_prefix(pattern: str) -> str:
    """Литеральный префикс glob'а до первого метасимвола."""
    return re.split(r"[*?\[]", pattern, maxsplit=1)[0]


def _map_patterns() -> list[str]:
    return swarm.load_criticality_map(MAP_PATH)["patterns"]


def _mirror_patterns(patterns: list[str]) -> list[str]:
    """Glob'ы, к которым применяется правило regular-file (RT-02: только
    зеркальные)."""
    return [p for p in patterns if p.startswith(MIRROR_PREFIXES)]


def test_cmap_load_test_combat_map():
    """E2: боевой references/criticality-map.md загружается load_criticality_map
    без CliError и без warnings (fail-closed ядра — значит тест упадёт на
    исключении)."""
    criticality_map = swarm.load_criticality_map(MAP_PATH)
    assert criticality_map["patterns"], "карта пуста (fail-closed)"
    assert criticality_map["warnings"] == [], (
        f"warnings парсера: {criticality_map['warnings']}")


def test_cmap_mirror_globs_resolve_to_regular_files():
    """E9 + RT-01/02: каждый glob .framework/ или .claude/ резолвится минимум
    в один git regular file (100644/100755); симлинки (120000) недопустимы."""
    patterns = _mirror_patterns(_map_patterns())
    assert patterns, "в карте нет ни одного зеркального glob'а — регрессия E9"
    for pattern in patterns:
        entries = _git_ls_files_s(_literal_prefix(pattern))
        assert entries, (
            f"мёртвый зеркальный glob (write-time fail-closed, RT-02): "
            f"{pattern!r} не резолвится ни в один git-файл")
        modes = {mode for mode, _ in entries}
        assert SYMLINK_MODE not in modes, (
            f"glob {pattern!r} накрывает симлинки (120000) — глобы зеркал "
            f"определяются по git object mode (E9/RT-01)")
        assert modes <= REGULAR_FILE_MODES, (
            f"glob {pattern!r} накрывает неожиданные режимы: {modes}")


def test_cmap_no_exact_duplicate_glob_with_different_lenses():
    """E8: точный duplicate glob с разными линзами — fail-closed. Проверяем по
    сырым строкам карты (парсер хранит последнюю привязку и дубликат бы
    потерял)."""
    lenses_by_pattern: dict[str, set[str]] = {}
    for line in MAP_PATH.read_text(encoding="utf-8").splitlines():
        match = GLOB_LINE.match(line.strip())
        if match:
            lenses_by_pattern.setdefault(match["pattern"], set()).add(
                match["lens"])
    conflicts = {p: ls for p, ls in lenses_by_pattern.items() if len(ls) > 1}
    assert not conflicts, (
        f"точный duplicate glob с разными линзами (E8): {conflicts}")


def test_cmap_secrets_pattern_present():
    """E10: поимённо спящий secrets/** — постоянный ручной critical-инвариант;
    удаление при регенерации запрещено."""
    criticality_map = swarm.load_criticality_map(MAP_PATH)
    assert "secrets/**" in criticality_map["patterns"], (
        "спящий secrets/** удалён из карты — запрещено (E10)")
    assert criticality_map["lens_bindings"].get("secrets/**") == "security"


def test_cmap_sleeping_and_semantic_skip_regular_file_rule():
    """RT-03: правило regular-file — только к зеркальным glob'ам. Семантические
    `**/...` и спящий secrets/** не обязаны резолвиться и не проверяются."""
    patterns = _map_patterns()
    mirror = set(_mirror_patterns(patterns))
    for pattern in patterns:
        if pattern.startswith("**/") or pattern == "secrets/**":
            assert pattern not in mirror, (
                f"незеркальный паттерн {pattern!r} ошибочно попал под "
                f"правило regular-file")


def test_cmap_codex_symlinks_not_covered():
    """E9: .codex/skills — симлинки (120000), глобами не покрываются; их байты
    резолвятся в .claude-пути, которые уже в карте."""
    patterns = _map_patterns()
    assert not any(p.startswith(".codex/") for p in patterns), (
        ".codex-пути в карте — нарушение топологии зеркал (E9)")


def test_cmap_narrow_registry_yaml_beats_wide():
    """RT-05: managed-secret-families.yaml → security поверх широкого
    infra/platform-registry/** → data-contracts (первая привязка выигрывает)."""
    criticality_map = swarm.load_criticality_map(MAP_PATH)
    hits = swarm.criticality_hits(
        ["infra/platform-registry/managed-secret-families.yaml"],
        criticality_map)
    assert hits, "managed-secret-families.yaml не попал в карту"
    lenses = swarm.resolve_forced_lenses(hits, criticality_map)
    assert lenses[hits[0]] == "security", (
        f"прецедент узкого security-glob'а сломан: {lenses}")

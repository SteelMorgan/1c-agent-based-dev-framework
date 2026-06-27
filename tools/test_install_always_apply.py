#!/usr/bin/env python3
"""
Тесты обработки alwaysApply для установщика фреймворка.

Проверяет:
  1. Правила и workflow устанавливаются в форме, нужной IDE.
  2. alwaysApply сохраняется в component_map и влияет на оценку контекста, но
     не фильтрует физическую установку.
  3. Навыки всегда в skills_dir, флаг не влияет.
  4. estimate_context_usage: правило без флага в on-demand, а не в always.

Использование:
    python tools/test_install_always_apply.py
    python -m pytest tools/test_install_always_apply.py -v  (опционально)
"""

import json
import os
import sys
import tempfile
import textwrap
from pathlib import Path

# Гарантируем импорт из этого же каталога
TOOLS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS_DIR))

import install as inst


# ─── Вспомогательные функции ──────────────────────────────────────────────────


def _make_fw(tmp_path: Path) -> tuple[Path, Path]:
    """Создаёт минимальный framework/ с тестовыми компонентами.

    Структура:
      framework/rules/rule-always/SKILL.md          — alwaysApply: true
      framework/rules/rule-lazy/SKILL.md            — без alwaysApply
      framework/rules/rule-explicit-false/SKILL.md  — alwaysApply: false
      framework/workflows/my-workflow/SKILL.md      — workflow для проверки Codex
      framework/skills/my-skill/SKILL.md    — навык (always_apply не влияет)
    """
    fw = tmp_path / "framework"

    # Правило с alwaysApply: true
    (fw / "rules" / "rule-always").mkdir(parents=True)
    (fw / "rules" / "rule-always" / "SKILL.md").write_text(textwrap.dedent("""\
        ---
        name: rule-always
        description: Тестовое правило always-on.
        alwaysApply: true
        ---
        # Rule Always
        Содержимое always-on правила.
    """), encoding="utf-8")

    # Правило без флага
    (fw / "rules" / "rule-lazy").mkdir(parents=True)
    (fw / "rules" / "rule-lazy" / "SKILL.md").write_text(textwrap.dedent("""\
        ---
        name: rule-lazy
        description: Тестовое правило без alwaysApply.
        ---
        # Rule Lazy
        Содержимое ленивого правила.
    """), encoding="utf-8")

    # Правило с явным alwaysApply: false
    (fw / "rules" / "rule-explicit-false").mkdir(parents=True)
    (fw / "rules" / "rule-explicit-false" / "SKILL.md").write_text(textwrap.dedent("""\
        ---
        name: rule-explicit-false
        description: Тестовое правило alwaysApply: false.
        alwaysApply: false
        ---
        # Rule Explicit False
        Содержимое правила с явным false.
    """), encoding="utf-8")

    # Воркфлоу
    (fw / "workflows" / "my-workflow").mkdir(parents=True)
    (fw / "workflows" / "my-workflow" / "SKILL.md").write_text(textwrap.dedent("""\
        ---
        name: my-workflow
        description: Тестовый workflow.
        ---
        # My Workflow
        Тело workflow.
    """), encoding="utf-8")

    # Навык
    skill_dir = fw / "skills" / "my-skill"
    skill_dir.mkdir(parents=True)
    (skill_dir / "SKILL.md").write_text(textwrap.dedent("""\
        ---
        name: my-skill
        description: Тестовый навык.
        ---
        # My Skill
        Тело навыка.
    """), encoding="utf-8")

    return fw, tmp_path


def _build_graph(fw: Path) -> "inst.FrameworkGraph":
    """Строит граф из тестового framework/."""
    return inst.FrameworkGraph(fw)


# ─── Тест 1: парсинг alwaysApply ──────────────────────────────────────────────


def test_always_apply_parsed():
    """Проверяет, что alwaysApply корректно парсится в Component.always_apply."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, _ = _make_fw(Path(tmp))
        graph = _build_graph(fw)

        always = graph.components.get("rule/rule-always")
        lazy = graph.components.get("rule/rule-lazy")
        false_rule = graph.components.get("rule/rule-explicit-false")

        assert always is not None, "rule/rule-always должен быть в графе"
        assert lazy is not None, "rule/rule-lazy должен быть в графе"
        assert false_rule is not None, "rule/rule-explicit-false должен быть в графе"

        assert always.always_apply is True, (
            f"rule-always.always_apply ожидается True, получено {always.always_apply}"
        )
        assert lazy.always_apply is False, (
            f"rule-lazy.always_apply ожидается False, получено {lazy.always_apply}"
        )
        assert false_rule.always_apply is False, (
            f"rule-explicit-false.always_apply ожидается False, получено {false_rule.always_apply}"
        )

    print("  OK  test_always_apply_parsed")


# ─── Тест 2: is_always_on_rule ───────────────────────────────────────────────


def test_is_always_on_rule():
    """Проверяет метод FrameworkGraph.is_always_on_rule."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, _ = _make_fw(Path(tmp))
        graph = _build_graph(fw)

        assert graph.is_always_on_rule("rule/rule-always") is True
        assert graph.is_always_on_rule("rule/rule-lazy") is False
        assert graph.is_always_on_rule("rule/rule-explicit-false") is False
        # Навыки — not a rule → False
        assert graph.is_always_on_rule("skill/my-skill") is False

    print("  OK  test_is_always_on_rule")


# ─── Тест 3: install — правила становятся skills ─────────────────────────────


def _run_install(
    fw: Path,
    ide_key: str,
    project_dir: Path,
    use_symlinks: bool = False,
) -> tuple[int, int]:
    """Запускает install_components (реальная установка без интерактивного подтверждения).

    Подтверждение обходится через подмену stdin строкой 'y'.
    По умолчанию использует копирование файлов для надёжности тестов.
    """
    import io
    graph = _build_graph(fw)
    selected = {c.id for c in graph.get_installable_for_user()}

    # Подаём 'y' в stdin, чтобы пройти prompt "Продолжить? [Y/n]"
    old_stdin = sys.stdin
    sys.stdin = io.StringIO("y\n")
    try:
        installed, skipped, removed = inst.install_components(
            graph=graph,
            selected_ids=selected,
            existing_symlink_ids=set(),
            ide_key=ide_key,
            project_dir=project_dir,
            use_symlinks=use_symlinks,
            dry_run=False,
        )
    finally:
        sys.stdin = old_stdin

    return installed, skipped


def test_install_claude_code_rules_as_rule_files():
    """Claude Code: все правила и workflow ставятся файловыми ссылками.

    alwaysApply не фильтрует состав установки: он используется только как
    метаданное/подсказка загрузки для IDE и оценки контекста.
    """
    with tempfile.TemporaryDirectory() as tmp:
        fw, base = _make_fw(Path(tmp))
        project_dir = base / "project"
        project_dir.mkdir()
        legacy_skills_dir = project_dir / ".claude" / "skills"
        legacy_skills_dir.mkdir(parents=True)
        for name in ("rule-always", "rule-lazy", "rule-explicit-false", "my-workflow"):
            (legacy_skills_dir / f"{name}.md").write_text("legacy", encoding="utf-8")

        _run_install(fw, "claude-code", project_dir)

        rules_dir = project_dir / ".claude" / "rules"
        skills_dir = project_dir / ".claude" / "skills"

        for name in ("rule-always", "rule-lazy", "rule-explicit-false", "my-workflow"):
            rule_file = rules_dir / f"{name}.md"
            assert rule_file.exists(), (
                f"{name}.md ожидается как файловая ссылка в {rules_dir}"
            )
            assert not (skills_dir / f"{name}.md").exists(), (
                f"{name}.md НЕ ожидается в {skills_dir}"
            )

        # Навык должен быть в skills_dir
        skill_dir = skills_dir / "my-skill"
        assert (skill_dir / "SKILL.md").exists(), (
            f"Навык my-skill/SKILL.md ожидается в {skills_dir}"
        )

    print("  OK  test_install_claude_code_rules_as_rule_files")


def test_install_codex_rules_as_skills():
    """Codex: правила разворачиваются как навыки в .codex/skills/<name>/SKILL.md.

    Как и для claude-code, фильтр alwaysApply НЕ применяется — берутся ВСЕ
    правила (и always-on, и lazy). Codex дополнительно читает их как навыки по требованию.
    Каталог .codex/rules/ не используется.
    """
    with tempfile.TemporaryDirectory() as tmp:
        fw, base = _make_fw(Path(tmp))
        project_dir = base / "project_codex"
        project_dir.mkdir()

        _run_install(fw, "codex", project_dir)

        skills_dir = project_dir / ".codex" / "skills"
        rules_dir = project_dir / ".codex" / "rules"

        # Все правила → навыки skills/<name>/SKILL.md (включая lazy и explicit-false)
        for rule_name in ("rule-always", "rule-lazy", "rule-explicit-false"):
            skill_file = skills_dir / rule_name / "SKILL.md"
            assert skill_file.exists(), (
                f"Правило {rule_name} ожидается как навык {skill_file} (codex)"
            )

        # Каталог .codex/rules/ не должен содержать .md-правил
        assert not (rules_dir / "rule-always.md").exists(), (
            "rule-always.md НЕ ожидается в .codex/rules/ (правила идут в skills/)"
        )

        # Обычный навык — на месте
        assert (skills_dir / "my-skill" / "SKILL.md").exists(), (
            "Навык my-skill ожидается в .codex/skills/"
        )

    print("  OK  test_install_codex_rules_as_skills")


def test_install_codex_rules_as_directory_symlinks():
    """Codex symlink-mode: правило устанавливается symlink-ом на каталог навыка."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, base = _make_fw(Path(tmp))
        project_dir = base / "project_codex_symlinks"
        project_dir.mkdir()

        _run_install(fw, "codex", project_dir, use_symlinks=True)

        rule_dir = project_dir / ".codex" / "skills" / "rule-always"
        skill_file = rule_dir / "SKILL.md"

        assert rule_dir.is_symlink(), (
            f"Ожидается symlink каталога {rule_dir}, а не symlink файла SKILL.md"
        )
        assert skill_file.exists(), f"Через symlink каталога должен читаться {skill_file}"
        assert not skill_file.is_symlink(), (
            "SKILL.md не должен быть отдельным symlink-ом; symlink должен стоять на каталоге"
        )

    print("  OK  test_install_codex_rules_as_directory_symlinks")


def test_install_codex_workflows_as_skills():
    """Codex: workflow-файлы разворачиваются как навыки, а не в .codex/rules/."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, base = _make_fw(Path(tmp))
        project_dir = base / "project_codex_workflow"
        project_dir.mkdir()

        skills_dir = project_dir / ".codex" / "skills"
        rules_dir = project_dir / ".codex" / "rules"
        rules_dir.mkdir(parents=True)
        legacy_workflow = rules_dir / "my-workflow.md"
        legacy_workflow.write_text("legacy workflow", encoding="utf-8")

        _run_install(fw, "codex", project_dir)

        workflow_skill = skills_dir / "my-workflow" / "SKILL.md"
        assert workflow_skill.exists(), (
            f"Workflow my-workflow ожидается как навык {workflow_skill} (codex)"
        )
        assert not legacy_workflow.exists(), (
            "my-workflow.md НЕ ожидается в .codex/rules/ (workflow идут в skills/)"
        )

    print("  OK  test_install_codex_workflows_as_skills")


def test_install_codex_rule_skill_name_collision():
    """Codex: правило, одноимённое навыку, получает префикс rule_ (не пропускается).

    coding-standards есть и как rule, и как skill → каталоги коллидируют. Правило
    должно лечь в .codex/skills/rule_coding-standards/, навык — в coding-standards/.
    """
    with tempfile.TemporaryDirectory() as tmp:
        fw = Path(tmp) / "framework"
        (fw / "rules" / "coding-standards").mkdir(parents=True)
        (fw / "rules" / "coding-standards" / "SKILL.md").write_text(textwrap.dedent("""\
            ---
            name: coding-standards
            description: Триггер-правило стандартов кодирования.
            alwaysApply: true
            ---
            # Coding Standards (rule)
            Тело правила-триггера.
        """), encoding="utf-8")

        skill_dir = fw / "skills" / "coding-standards"
        skill_dir.mkdir(parents=True)
        (skill_dir / "SKILL.md").write_text(textwrap.dedent("""\
            ---
            name: coding-standards
            description: Навык стандартов кодирования.
            ---
            # Coding Standards (skill)
            Тело навыка.
        """), encoding="utf-8")

        project_dir = Path(tmp) / "project"
        project_dir.mkdir()
        _run_install(fw, "codex", project_dir)

        skills_dir = project_dir / ".codex" / "skills"

        # Правило → с префиксом rule_ (не пропущено)
        assert (skills_dir / "rule_coding-standards" / "SKILL.md").exists(), (
            "Одноимённое правило ожидается как .codex/skills/rule_coding-standards/SKILL.md"
        )
        # Навык → без префикса
        assert (skills_dir / "coding-standards" / "SKILL.md").exists(), (
            "Навык coding-standards ожидается в .codex/skills/coding-standards/"
        )

    print("  OK  test_install_codex_rule_skill_name_collision")


def test_install_codex_agent_to_toml():
    """Codex: агенты конвертируются в .codex/agents/<name>.toml (name/description/body)."""
    with tempfile.TemporaryDirectory() as tmp:
        fw = Path(tmp) / "framework"
        (fw / "subagents").mkdir(parents=True)
        (fw / "subagents" / "frontend-builder.md").write_text(textwrap.dedent("""\
            ---
            name: frontend-builder
            description: Builds approved frontend implementation for this project.
              Use proactively after design approval.
            ---
            You are the frontend-builder for this repository.
            Follow the project implementation rules and use the approved design.

            ---
            depends_on:
              - framework/skills/some-skill/SKILL.md
            ---
        """), encoding="utf-8")

        project_dir = Path(tmp) / "project"
        project_dir.mkdir()
        _run_install(fw, "codex", project_dir)

        toml_path = project_dir / ".codex" / "agents" / "frontend-builder.toml"
        assert toml_path.exists(), f"Ожидается TOML-профиль {toml_path}"

        try:
            import tomllib
        except ModuleNotFoundError:  # py<3.11
            print("  SKIP test_install_codex_agent_to_toml (нет tomllib)")
            return
        data = tomllib.loads(toml_path.read_text(encoding="utf-8"))

        assert data["name"] == "frontend-builder", data.get("name")
        # description folded на 2 строки — должно склеиться целиком
        assert "Use proactively after design approval." in data["description"], (
            f"description должен включать обе строки: {data['description']!r}"
        )
        instr = data["developer_instructions"]
        assert "You are the frontend-builder" in instr, instr[:80]
        # хвостовой backmatter (depends_on) не должен попасть в инструкции
        assert "depends_on" not in instr, "backmatter не должен быть в developer_instructions"

    print("  OK  test_install_codex_agent_to_toml")


# ─── Тест 4: component_map в .install-session.json ───────────────────────────


def test_session_log_component_map():
    """Правило без alwaysApply: true присутствует в component_map, поле always_apply=False."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, base = _make_fw(Path(tmp))
        project_dir = base / "project_log"
        project_dir.mkdir()
        graph = _build_graph(fw)
        selected = {c.id for c in graph.get_installable_for_user()}

        import io
        old_stdin = sys.stdin
        sys.stdin = io.StringIO("y\n")
        try:
            installed, skipped, removed = inst.install_components(
                graph=graph,
                selected_ids=selected,
                existing_symlink_ids=set(),
                ide_key="claude-code",
                project_dir=project_dir,
                use_symlinks=False,
                dry_run=False,
            )
        finally:
            sys.stdin = old_stdin

        inst.write_session_log(
            project_dir=project_dir,
            ide_key="claude-code",
            selected=selected,
            model_map={},
            use_symlinks=False,
            installed=installed,
            skipped=skipped,
            removed=removed,
            graph=graph,
        )

        log_path = project_dir / ".install-session.json"
        assert log_path.exists(), ".install-session.json должен быть создан"

        data = json.loads(log_path.read_text(encoding="utf-8"))
        comp_map = data.get("component_map", {})

        # rule-always должен быть в component_map с always_apply=True
        assert "rule/rule-always" in comp_map, "rule/rule-always отсутствует в component_map"
        assert comp_map["rule/rule-always"].get("always_apply") is True, (
            "rule-always.always_apply ожидается True в component_map"
        )

        # rule-lazy должен быть в component_map с always_apply=False
        assert "rule/rule-lazy" in comp_map, "rule/rule-lazy отсутствует в component_map"
        assert comp_map["rule/rule-lazy"].get("always_apply") is False, (
            "rule-lazy.always_apply ожидается False в component_map"
        )

        # Навык в component_map без поля always_apply
        assert "skill/my-skill" in comp_map, "skill/my-skill отсутствует в component_map"
        assert "always_apply" not in comp_map["skill/my-skill"], (
            "Навык не должен иметь поле always_apply в component_map"
        )

    print("  OK  test_session_log_component_map")


# ─── Тест 5: estimate_context_usage — правило без флага в on-demand ───────────


def test_estimate_context_lazy_rule_is_on_demand():
    """Правило без alwaysApply идёт в on-demand при оценке контекста."""
    with tempfile.TemporaryDirectory() as tmp:
        fw, _ = _make_fw(Path(tmp))
        graph = _build_graph(fw)

        # Проверяем напрямую через _is_always_on_component
        always_comp = graph.components.get("rule/rule-always")
        lazy_comp = graph.components.get("rule/rule-lazy")
        skill_comp = graph.components.get("skill/my-skill")

        assert always_comp is not None and inst._is_always_on_component(always_comp) is True, (
            "rule-always должен быть always-on в оценке контекста"
        )
        assert lazy_comp is not None and inst._is_always_on_component(lazy_comp) is False, (
            "rule-lazy должен быть on-demand в оценке контекста"
        )
        assert skill_comp is not None and inst._is_always_on_component(skill_comp) is False, (
            "Навык всегда on-demand"
        )

    print("  OK  test_estimate_context_lazy_rule_is_on_demand")


# ─── Тест 6: print_tree — группировка правил и пометка on-demand ─────────────


def test_print_tree_rule_grouping():
    """print_tree разделяет правила на always-on / on-demand подгруппы.

    Проверяет:
    - Заголовок подгруппы «always-on» присутствует при наличии правил с флагом.
    - Заголовок подгруппы «on-demand» присутствует при наличии правил без флага.
    - always-on правило выводится без пометки «on-demand».
    - on-demand правило содержит пометку «(on-demand)» в выводе.
    - idx_map содержит оба типа правил (обе подгруппы имеют номера).
    """
    import io
    from contextlib import redirect_stdout

    with tempfile.TemporaryDirectory() as tmp:
        fw, _ = _make_fw(Path(tmp))
        graph = _build_graph(fw)

        buf = io.StringIO()
        with redirect_stdout(buf):
            idx_map = inst.print_tree(graph)
        output = buf.getvalue()

        # Обе подгруппы должны быть в выводе
        assert "always-on" in output, (
            "Заголовок подгруппы 'always-on' отсутствует в print_tree"
        )
        assert "on-demand" in output, (
            "Заголовок подгруппы 'on-demand' отсутствует в print_tree"
        )

        # always-on правило должно быть в idx_map
        always_ids = [cid for cid in idx_map.values() if cid == "rule/rule-always"]
        assert always_ids, "rule/rule-always отсутствует в idx_map print_tree"

        # on-demand правила должны быть в idx_map
        lazy_ids = [cid for cid in idx_map.values() if cid == "rule/rule-lazy"]
        assert lazy_ids, "rule/rule-lazy отсутствует в idx_map print_tree"

        false_ids = [cid for cid in idx_map.values() if cid == "rule/rule-explicit-false"]
        assert false_ids, "rule/rule-explicit-false отсутствует в idx_map print_tree"

        # Проверяем, что строка с on-demand правилом содержит пометку
        lines = output.splitlines()
        lazy_lines = [l for l in lines if "rule-lazy" in l]
        assert lazy_lines, "Строка с rule-lazy не найдена в выводе"
        assert any("on-demand" in l for l in lazy_lines), (
            f"Строка с rule-lazy не содержит '(on-demand)': {lazy_lines}"
        )

        # Строка с always-on правилом НЕ должна содержать пометку on-demand
        always_lines = [l for l in lines if "rule-always" in l and "always-on" not in l]
        if always_lines:
            # Если rule-always отображается как строка элемента (не заголовок)
            assert not any("(on-demand)" in l for l in always_lines), (
                f"Строка с rule-always содержит '(on-demand)' — не должна: {always_lines}"
            )

    print("  OK  test_print_tree_rule_grouping")


# ─── Тест 7: _build_checklist_items — on-demand в описании ───────────────────


def test_build_checklist_items_ondemand_label():
    """_build_checklist_items помечает on-demand правила в описании.

    Проверяет:
    - always-on правило НЕ имеет префикс [on-demand] в description.
    - on-demand правила имеют префикс [on-demand] в description.
    - Заголовки подгрупп «always-on» и «on-demand» присутствуют.
    """
    with tempfile.TemporaryDirectory() as tmp:
        fw, _ = _make_fw(Path(tmp))
        graph = _build_graph(fw)

        items = inst._build_checklist_items(graph)
        # items: List[(id, label, description, is_header)]

        # Собираем заголовки (is_header=True)
        headers = [label for _, label, _, is_hdr in items if is_hdr]
        assert any("always-on" in h for h in headers), (
            f"Заголовок 'always-on' отсутствует в _build_checklist_items; headers={headers}"
        )
        assert any("on-demand" in h for h in headers), (
            f"Заголовок 'on-demand' отсутствует в _build_checklist_items; headers={headers}"
        )

        # Находим элементы для конкретных правил
        rule_items = {cid: (label, desc) for cid, label, desc, is_hdr in items if not is_hdr and cid}

        assert "rule/rule-always" in rule_items, "rule/rule-always отсутствует в items"
        assert "rule/rule-lazy" in rule_items, "rule/rule-lazy отсутствует в items"
        assert "rule/rule-explicit-false" in rule_items, "rule/rule-explicit-false отсутствует в items"

        # always-on правило НЕ должно иметь [on-demand] в описании
        _, always_desc = rule_items["rule/rule-always"]
        assert "[on-demand]" not in always_desc, (
            f"rule-always не должен иметь '[on-demand]' в описании: {always_desc!r}"
        )

        # on-demand правила ДОЛЖНЫ иметь [on-demand] в описании
        _, lazy_desc = rule_items["rule/rule-lazy"]
        assert "[on-demand]" in lazy_desc, (
            f"rule-lazy должен иметь '[on-demand]' в описании: {lazy_desc!r}"
        )

        _, false_desc = rule_items["rule/rule-explicit-false"]
        assert "[on-demand]" in false_desc, (
            f"rule-explicit-false должен иметь '[on-demand]' в описании: {false_desc!r}"
        )

    print("  OK  test_build_checklist_items_ondemand_label")


# ─── Запуск всех тестов ──────────────────────────────────────────────────────


def main():
    tests = [
        test_always_apply_parsed,
        test_is_always_on_rule,
        test_install_claude_code_rules_as_rule_files,
        test_install_codex_rules_as_skills,
        test_install_codex_rules_as_directory_symlinks,
        test_install_codex_workflows_as_skills,
        test_install_codex_rule_skill_name_collision,
        test_install_codex_agent_to_toml,
        test_session_log_component_map,
        test_estimate_context_lazy_rule_is_on_demand,
        test_print_tree_rule_grouping,
        test_build_checklist_items_ondemand_label,
    ]

    print(f"\n{'─' * 60}")
    print(f"  Тесты обработки alwaysApply (install.py)")
    print(f"{'─' * 60}\n")

    passed = 0
    failed = 0
    for test in tests:
        try:
            test()
            passed += 1
        except AssertionError as e:
            print(f"  FAIL {test.__name__}: {e}")
            failed += 1
        except Exception as e:
            print(f"  ERROR {test.__name__}: {type(e).__name__}: {e}")
            import traceback
            traceback.print_exc()
            failed += 1

    total = passed + failed
    print(f"\n{'─' * 60}")
    if failed == 0:
        print(f"  Все {passed} тестов прошли успешно.")
    else:
        print(f"  {passed}/{total} прошли, {failed} упали.")
    print(f"{'─' * 60}\n")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Валидатор зависимостей фреймворка.

Проверяет:
1. Все зависимости в depends_on существуют
2. Все упоминания навыков/правил в тексте задекларированы в depends_on
3. Навыки агентов существуют
4. Нет циклических зависимостей

Использование:
    python3 tools/validate-framework-deps.py           # проверка
    python3 tools/validate-framework-deps.py --fix     # исправление предупреждений
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple
import yaml

# Корень репозитория
REPO_ROOT = Path(__file__).parent.parent
FRAMEWORK_DIR = REPO_ROOT / "framework"

# Типы артефактов
ARTIFACT_TYPES = {
    "skills": FRAMEWORK_DIR / "skills",
    "rules": FRAMEWORK_DIR / "rules",
    "agents": FRAMEWORK_DIR / "agents",
    "workflows": FRAMEWORK_DIR / "workflows",
}


class FrameworkValidator:
    def __init__(self, fix_mode: bool = False):
        self.fix_mode = fix_mode
        self.errors: List[str] = []
        self.warnings: List[str] = []
        self.info: List[str] = []
        self.all_files: Set[Path] = set()
        self.dependencies: Dict[Path, Set[Path]] = {}
        self.fixes: Dict[Path, Set[Path]] = {}  # файл -> недостающие зависимости
        
    def find_all_framework_files(self) -> Set[Path]:
        """Находит все .md и .mdc файлы во фреймворке."""
        files = set()
        for pattern in ["**/*.md", "**/*.mdc"]:
            files.update(FRAMEWORK_DIR.glob(pattern))
        # Исключаем шаблоны
        files = {f for f in files if not f.name.startswith("_template")}
        return files
    
    def parse_frontmatter(self, file_path: Path) -> Tuple[dict, str]:
        """Парсит YAML frontmatter и возвращает (metadata, body)."""
        content = file_path.read_text(encoding="utf-8")
        
        # Ищем frontmatter между ---
        match = re.match(r'^---\s*\n(.*?)\n---\s*\n(.*)$', content, re.DOTALL)
        if not match:
            return {}, content
        
        try:
            metadata = yaml.safe_load(match.group(1)) or {}
            body = match.group(2)
            return metadata, body
        except yaml.YAMLError as e:
            self.errors.append(f"{file_path.relative_to(REPO_ROOT)}: Ошибка парсинга YAML: {e}")
            return {}, content
    
    def resolve_path(self, ref: str, from_file: Path) -> Path:
        """Резолвит путь относительно корня репо."""
        # Убираем якоря и query params
        ref = ref.split('#')[0].split('?')[0]
        
        # Если путь начинается с framework/ — от корня репо
        if ref.startswith("framework/"):
            return REPO_ROOT / ref
        
        # Иначе — относительно текущего файла
        return (from_file.parent / ref).resolve()
    
    def extract_references_from_text(self, body: str, file_path: Path) -> Set[Path]:
        """Извлекает все ссылки на файлы фреймворка из текста."""
        refs = set()
        
        # Markdown ссылки: [text](path)
        for match in re.finditer(r'\[([^\]]+)\]\(([^)]+)\)', body):
            ref = match.group(2)
            if any(pattern in ref for pattern in ["framework/skills", "framework/rules", "framework/agents", "framework/workflows"]):
                resolved = self.resolve_path(ref, file_path)
                refs.add(resolved)
        
        # Прямые упоминания путей в тексте
        for match in re.finditer(r'framework/(skills|rules|agents|workflows)/[^\s\)]+\.(?:md|mdc)', body):
            ref = match.group(0)
            resolved = self.resolve_path(ref, file_path)
            refs.add(resolved)
        
        return refs
    
    def check_file(self, file_path: Path):
        """Проверяет один файл."""
        rel_path = file_path.relative_to(REPO_ROOT)
        
        metadata, body = self.parse_frontmatter(file_path)
        
        # 1. Проверяем depends_on
        depends_on = metadata.get("depends_on", [])
        if not isinstance(depends_on, list):
            self.errors.append(f"{rel_path}: depends_on должен быть списком")
            depends_on = []
        
        declared_deps = set()
        for dep in depends_on:
            dep_path = self.resolve_path(dep, file_path)
            declared_deps.add(dep_path)
            
            # Проверяем существование
            if not dep_path.exists():
                self.errors.append(f"{rel_path}: зависимость не существует: {dep}")
        
        # 2. Извлекаем ссылки из текста
        text_refs = self.extract_references_from_text(body, file_path)
        
        # 3. Проверяем, что все ссылки задекларированы
        undeclared = text_refs - declared_deps
        for ref in undeclared:
            if ref.exists() and ref in self.all_files:
                self.warnings.append(
                    f"{rel_path}: упоминается {ref.relative_to(REPO_ROOT)}, "
                    f"но не задекларировано в depends_on"
                )
                if self.fix_mode:
                    if file_path not in self.fixes:
                        self.fixes[file_path] = set()
                    self.fixes[file_path].add(ref)
        
        # 4. Для агентов проверяем skills
        if file_path.parent.name == "agents":
            skills = metadata.get("skills", [])
            if isinstance(skills, list):
                for skill_name in skills:
                    # Ищем навык по имени
                    found = False
                    for skill_file in (FRAMEWORK_DIR / "skills").rglob("SKILL.md"):
                        skill_meta, _ = self.parse_frontmatter(skill_file)
                        if skill_meta.get("name") == skill_name:
                            found = True
                            # Проверяем, что навык в depends_on (опционально)
                            if skill_file not in declared_deps:
                                self.warnings.append(
                                    f"{rel_path}: использует навык '{skill_name}', "
                                    f"но {skill_file.relative_to(REPO_ROOT)} не в depends_on"
                                )
                            break
                    
                    if not found:
                        self.errors.append(f"{rel_path}: навык '{skill_name}' не найден")
        
        # Сохраняем зависимости для проверки циклов
        self.dependencies[file_path] = declared_deps
    
    def apply_fixes(self):
        """Применяет автоматические исправления."""
        if not self.fixes:
            print("\n✅ Нечего исправлять")
            return
        
        print(f"\n🔧 Применение исправлений ({len(self.fixes)} файлов)...\n")
        
        for file_path, missing_deps in self.fixes.items():
            rel_path = file_path.relative_to(REPO_ROOT)
            content = file_path.read_text(encoding="utf-8")
            
            # Парсим frontmatter
            match = re.match(r'^(---\s*\n)(.*?)(\n---\s*\n)(.*)$', content, re.DOTALL)
            if not match:
                print(f"  ⚠️  {rel_path}: нет frontmatter, пропускаем")
                continue
            
            try:
                metadata = yaml.safe_load(match.group(2)) or {}
            except yaml.YAMLError:
                print(f"  ⚠️  {rel_path}: ошибка парсинга YAML, пропускаем")
                continue
            
            # Добавляем недостающие зависимости
            depends_on = metadata.get("depends_on", [])
            if not isinstance(depends_on, list):
                depends_on = []
            
            for dep in missing_deps:
                dep_rel = dep.relative_to(REPO_ROOT).as_posix()
                if dep_rel not in depends_on:
                    depends_on.append(dep_rel)
            
            # Сортируем для стабильности
            depends_on.sort()
            metadata["depends_on"] = depends_on
            
            # Записываем обратно
            new_yaml = yaml.dump(metadata, allow_unicode=True, sort_keys=False, default_flow_style=False)
            new_content = f"---\n{new_yaml}---\n{match.group(4)}"
            
            file_path.write_text(new_content, encoding="utf-8")
            print(f"  ✅ {rel_path}: добавлено {len(missing_deps)} зависимостей")
        
        print(f"\n✅ Исправлено {len(self.fixes)} файлов")
    
    def find_mutual_groups(self) -> List[Set[Path]]:
        """Находит группы взаимозависимых артефактов.
        
        Группа — это множество узлов, где каждый достижим из каждого (SCC — strongly connected component).
        Возвращает только нетривиальные SCC (размер ≥ 2).
        """
        # Алгоритм Тарьяна для поиска SCC
        index_counter = [0]
        stack: List[Path] = []
        lowlink: Dict[Path, int] = {}
        index: Dict[Path, int] = {}
        on_stack: Set[Path] = set()
        sccs: List[Set[Path]] = []
        
        nodes = set(self.dependencies.keys())
        
        def strongconnect(node: Path):
            index[node] = index_counter[0]
            lowlink[node] = index_counter[0]
            index_counter[0] += 1
            stack.append(node)
            on_stack.add(node)
            
            for dep in self.dependencies.get(node, set()):
                if dep not in nodes:
                    continue
                if dep not in index:
                    strongconnect(dep)
                    lowlink[node] = min(lowlink[node], lowlink[dep])
                elif dep in on_stack:
                    lowlink[node] = min(lowlink[node], index[dep])
            
            if lowlink[node] == index[node]:
                scc: Set[Path] = set()
                while True:
                    w = stack.pop()
                    on_stack.discard(w)
                    scc.add(w)
                    if w == node:
                        break
                if len(scc) >= 2:
                    sccs.append(scc)
        
        for node in sorted(nodes):
            if node not in index:
                strongconnect(node)
        
        return sccs
    
    def format_groups(self, groups: List[Set[Path]]) -> List[str]:
        """Форматирует группы взаимозависимостей для вывода."""
        result = []
        for group in groups:
            parts = sorted(p.relative_to(REPO_ROOT).as_posix() for p in group)
            if len(parts) == 2:
                result.append(f"{parts[0]} ↔ {parts[1]}")
            else:
                result.append(" ↔ ".join(parts))
        return result
    
    def export_cycles_to_json(self, output_file: str):
        """Экспортирует взаимозависимости в JSON для использования в install.py."""
        import json
        from datetime import datetime, timezone
        
        print("🔍 Поиск файлов фреймворка...")
        self.all_files = self.find_all_framework_files()
        
        print("📋 Анализ зависимостей...")
        for file_path in sorted(self.all_files):
            metadata, _ = self.parse_frontmatter(file_path)
            depends_on = metadata.get("depends_on", [])
            if isinstance(depends_on, list):
                declared_deps = set()
                for dep in depends_on:
                    dep_path = self.resolve_path(dep, file_path)
                    if dep_path.exists():
                        declared_deps.add(dep_path)
                self.dependencies[file_path] = declared_deps
        
        print("🔄 Поиск взаимозависимостей...")
        groups = self.find_mutual_groups()
        
        # Формируем структуру для JSON
        cycles_data = {
            "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "description": "Взаимозависимости между артефактами фреймворка",
            "cycles": []
        }
        
        for group in groups:
            parts = sorted(p.relative_to(REPO_ROOT).as_posix() for p in group)
            cycle_type = "bidirectional" if len(parts) == 2 else "circular"
            cycles_data["cycles"].append({
                "artifacts": parts,
                "type": cycle_type
            })
        
        # Записываем в файл
        output_path = Path(output_file)
        output_path.write_text(json.dumps(cycles_data, indent=2, ensure_ascii=False), encoding="utf-8")
        
        print(f"\n✅ Экспортировано {len(groups)} взаимозависимостей в {output_file}")
    
    def run(self) -> int:
        """Запускает валидацию."""
        print("🔍 Поиск файлов фреймворка...")
        self.all_files = self.find_all_framework_files()
        print(f"   Найдено файлов: {len(self.all_files)}")
        
        print("\n📋 Проверка зависимостей...")
        for file_path in sorted(self.all_files):
            self.check_file(file_path)
        
        print("\n🔄 Поиск циклических зависимостей...")
        groups = self.find_mutual_groups()
        group_strings = self.format_groups(groups)
        if group_strings:
            for group_str in group_strings:
                self.info.append(f"Взаимозависимость: {group_str}")
        
        # Вывод результатов
        print("\n" + "="*80)
        
        if self.errors:
            print(f"\n❌ ОШИБКИ ({len(self.errors)}):\n")
            for error in self.errors:
                print(f"  • {error}")
        
        if self.warnings:
            print(f"\n⚠️  ПРЕДУПРЕЖДЕНИЯ ({len(self.warnings)}):\n")
            for warning in self.warnings:
                print(f"  • {warning}")
        
        if self.info:
            print(f"\n💡 ИНФОРМАЦИЯ ({len(self.info)}):\n")
            for info in self.info:
                print(f"  • {info}")
        
        if not self.errors and not self.warnings and not self.info:
            print("\n✅ Все проверки пройдены!")
            return 0
        
        print("\n" + "="*80)
        summary_parts = []
        if self.errors:
            summary_parts.append(f"{len(self.errors)} ошибок")
        if self.warnings:
            summary_parts.append(f"{len(self.warnings)} предупреждений")
        if self.info:
            summary_parts.append(f"{len(self.info)} взаимозависимостей")
        
        if summary_parts:
            print(f"\nИтого: {', '.join(summary_parts)}")
        
        # Применяем исправления если режим --fix
        if self.fix_mode and self.warnings and not self.errors:
            self.apply_fixes()
            print("\n💡 Запустите валидатор снова для проверки")
        
        return 1 if self.errors else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Валидатор зависимостей фреймворка",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Примеры:
  python3 tools/validate-framework-deps.py                    # проверка
  python3 tools/validate-framework-deps.py --fix              # исправление предупреждений
  python3 tools/validate-framework-deps.py --export-cycles    # экспорт взаимозависимостей в JSON
        """
    )
    parser.add_argument(
        "--fix",
        action="store_true",
        help="Автоматически исправить предупреждения (добавить недостающие зависимости)"
    )
    parser.add_argument(
        "--export-cycles",
        metavar="FILE",
        help="Экспортировать взаимозависимости в JSON файл для использования в install.py"
    )
    
    args = parser.parse_args()
    validator = FrameworkValidator(fix_mode=args.fix)
    
    if args.export_cycles:
        validator.export_cycles_to_json(args.export_cycles)
        sys.exit(0)
    
    sys.exit(validator.run())

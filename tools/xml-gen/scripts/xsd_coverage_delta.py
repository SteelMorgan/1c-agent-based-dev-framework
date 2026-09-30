#!/usr/bin/env python3
"""Compare namespace-forest XSD facts with xml-gen implementation coverage.

This script does not assume xml-gen has its own XSD set. Instead it builds:

1. Platform facts from a namespace-forest checkout:
   namespaces, imports, global declarations, enum values, required members.
2. xml-gen coverage facts from source code:
   string literals, namespace URI references, mdclasses imports, validator/writer
   references, test XML snippets.

The result is a review-oriented delta report. Missing entries mean "not found in
xml-gen source facts", not necessarily "unsupported at runtime".
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


XS_NS = "http://www.w3.org/2001/XMLSchema"
XS = f"{{{XS_NS}}}"

STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
JAVA_IMPORT_RE = re.compile(r"^\s*import\s+([^;]+);", re.MULTILINE)
JAVA_CLASS_RE = re.compile(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)")
URI_RE = re.compile(r"https?://[^\s\"'<>]+")
XML_NAME_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_.:-]{1,80}$")

SOURCE_DIRS = [
    "src/main/java",
    "src/main/resources",
    "src/test/java",
    "src/test/resources",
]


@dataclass
class SymbolHit:
    symbol: str
    count: int = 0
    samples: list[str] = field(default_factory=list)

    def add(self, sample: str, limit: int = 8) -> None:
        self.count += 1
        if len(self.samples) < limit and sample not in self.samples:
            self.samples.append(sample)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def rel(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def decode_java_string(token: str) -> str:
    try:
        return ast.literal_eval(token)
    except Exception:
        return token[1:-1]


def clean_uri(uri: str) -> str:
    return uri.rstrip("\\).,;")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def schema_versions(forest: Path) -> list[str]:
    schemas = forest / "schemas"
    if not schemas.is_dir():
        raise SystemExit(f"schemas directory not found: {schemas}")
    versions = [p.name for p in schemas.iterdir() if p.is_dir() and re.match(r"^\d+\.\d+$", p.name)]
    return sorted(versions, key=lambda v: tuple(int(x) for x in v.split(".")))


def select_versions(forest: Path, requested: str) -> list[str]:
    versions = schema_versions(forest)
    if requested == "all":
        return versions
    if requested == "latest":
        return versions[-1:]
    if requested not in versions:
        raise SystemExit(f"schema version {requested!r} not found; available: {', '.join(versions)}")
    return [requested]


def add_hit(index: dict[str, SymbolHit], symbol: str, sample: str) -> None:
    hit = index.setdefault(symbol, SymbolHit(symbol))
    hit.add(sample)


def extract_xsd_facts(forest: Path, versions: list[str]) -> dict[str, Any]:
    facts: dict[str, Any] = {
        "versions": {},
        "all": {
            "namespaces": {},
            "globalElements": {},
            "globalComplexTypes": {},
            "globalSimpleTypes": {},
            "globalAttributes": {},
            "groups": {},
            "attributeGroups": {},
            "enumValues": {},
            "requiredAttributes": {},
            "requiredElements": {},
            "imports": [],
            "files": [],
        },
    }
    all_facts = facts["all"]

    for version in versions:
        version_dir = forest / "schemas" / version
        version_facts = {
            "files": [],
            "namespaces": {},
            "globalElements": {},
            "globalComplexTypes": {},
            "globalSimpleTypes": {},
            "globalAttributes": {},
            "groups": {},
            "attributeGroups": {},
            "enumValues": {},
            "requiredAttributes": {},
            "requiredElements": {},
            "imports": [],
        }
        for xsd_file in sorted(version_dir.glob("*.xsd")):
            file_key = f"{version}/{xsd_file.name}"
            version_facts["files"].append(xsd_file.name)
            all_facts["files"].append(file_key)
            try:
                tree = ET.parse(xsd_file)
            except ET.ParseError as exc:
                version_facts.setdefault("parseErrors", []).append({"file": xsd_file.name, "error": str(exc)})
                continue

            root = tree.getroot()
            target_ns = root.attrib.get("targetNamespace", "")
            if target_ns:
                add_hit(version_facts["namespaces"], target_ns, xsd_file.name)
                add_hit(all_facts["namespaces"], target_ns, file_key)

            for child in root:
                lname = local_name(child.tag)
                name = child.attrib.get("name")
                if lname == "import":
                    entry = {
                        "version": version,
                        "file": xsd_file.name,
                        "namespace": child.attrib.get("namespace", ""),
                        "schemaLocation": child.attrib.get("schemaLocation", ""),
                    }
                    version_facts["imports"].append(entry)
                    all_facts["imports"].append(entry)
                elif lname == "include":
                    entry = {
                        "version": version,
                        "file": xsd_file.name,
                        "schemaLocation": child.attrib.get("schemaLocation", ""),
                    }
                    version_facts.setdefault("includes", []).append(entry)
                    all_facts.setdefault("includes", []).append(entry)
                elif name and lname in {
                    "element",
                    "complexType",
                    "simpleType",
                    "attribute",
                    "group",
                    "attributeGroup",
                }:
                    bucket = {
                        "element": "globalElements",
                        "complexType": "globalComplexTypes",
                        "simpleType": "globalSimpleTypes",
                        "attribute": "globalAttributes",
                        "group": "groups",
                        "attributeGroup": "attributeGroups",
                    }[lname]
                    add_hit(version_facts[bucket], name, xsd_file.name)
                    add_hit(all_facts[bucket], name, file_key)
                    if lname == "simpleType":
                        collect_enum_values(child, name, xsd_file.name, file_key, version_facts, all_facts)

            collect_required_members(root, xsd_file.name, file_key, version_facts, all_facts)

        facts["versions"][version] = freeze_hits(version_facts)

    facts["all"] = freeze_hits(all_facts)
    return facts


def collect_enum_values(
    simple_type: ET.Element,
    type_name: str,
    file_name: str,
    file_key: str,
    version_facts: dict[str, Any],
    all_facts: dict[str, Any],
) -> None:
    for enum in simple_type.findall(f".//{XS}enumeration"):
        value = enum.attrib.get("value")
        if value is None:
            continue
        key = f"{type_name}::{value}"
        add_hit(version_facts["enumValues"], key, file_name)
        add_hit(all_facts["enumValues"], key, file_key)


def collect_required_members(
    root: ET.Element,
    file_name: str,
    file_key: str,
    version_facts: dict[str, Any],
    all_facts: dict[str, Any],
) -> None:
    for node in root.iter():
        lname = local_name(node.tag)
        name = node.attrib.get("name") or node.attrib.get("ref")
        if not name:
            continue
        if lname == "attribute" and node.attrib.get("use") == "required":
            add_hit(version_facts["requiredAttributes"], name, file_name)
            add_hit(all_facts["requiredAttributes"], name, file_key)
        elif lname == "element":
            min_occurs = node.attrib.get("minOccurs")
            max_occurs = node.attrib.get("maxOccurs", "1")
            if min_occurs not in ("0", None) and max_occurs != "0":
                add_hit(version_facts["requiredElements"], name, file_name)
                add_hit(all_facts["requiredElements"], name, file_key)


def extract_xmlgen_facts(xmlgen_root: Path) -> dict[str, Any]:
    facts: dict[str, Any] = {
        "filesScanned": [],
        "stringLiterals": {},
        "namespaceUris": {},
        "xmlNameLiterals": {},
        "javaImports": {},
        "mdclassesImports": {},
        "validatorClasses": {},
        "writerClasses": {},
        "oracleClasses": {},
        "dslClasses": {},
        "rootElementMentions": {},
    }

    files: list[Path] = []
    for source_dir in SOURCE_DIRS:
        root = xmlgen_root / source_dir
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path.suffix in {".java", ".json", ".xml", ".properties", ".template", ".tmpl"}:
                files.append(path)
    for build_file in ("build.gradle", "build.gradle.kts", "pom.xml"):
        path = xmlgen_root / build_file
        if path.is_file():
            files.append(path)

    for path in sorted(files):
        sample_file = rel(path, xmlgen_root)
        facts["filesScanned"].append(sample_file)
        text = read_text(path)

        if path.suffix == ".java":
            for imp in JAVA_IMPORT_RE.findall(text):
                add_hit(facts["javaImports"], imp, sample_file)
                if "com.github._1c_syntax" in imp or "mdclasses" in imp or "bsl" in imp:
                    add_hit(facts["mdclassesImports"], imp, sample_file)
            for cls in JAVA_CLASS_RE.findall(text):
                bucket = class_bucket(sample_file, cls)
                if bucket:
                    add_hit(facts[bucket], cls, sample_file)

        for token in STRING_RE.findall(text):
            value = decode_java_string(token)
            if not value or len(value) > 240:
                continue
            add_hit(facts["stringLiterals"], value, sample_file)
            for uri in URI_RE.findall(value):
                if "v8.1c.ru" in uri or "w3.org" in uri:
                    add_hit(facts["namespaceUris"], clean_uri(uri), sample_file)
            if XML_NAME_RE.match(value) and not looks_like_message_word(value):
                add_hit(facts["xmlNameLiterals"], value, sample_file)
        if path.name in {"build.gradle", "build.gradle.kts", "pom.xml"}:
            collect_dependency_hints(text, sample_file, facts)

        if path.suffix == ".xml" or "<" in text:
            for match in re.finditer(r"<([A-Za-z_][A-Za-z0-9_.:-]*)\b", text):
                add_hit(facts["rootElementMentions"], match.group(1), sample_file)

    return freeze_hits(facts)


def extract_corpus_facts(corpus_root: Path, file_limit: int) -> dict[str, Any]:
    facts: dict[str, Any] = {
        "root": str(corpus_root),
        "filesScanned": [],
        "parseErrors": {},
        "namespaces": {},
        "elementNames": {},
        "attributeNames": {},
        "rootElements": {},
    }

    scanned = 0
    for path in iter_corpus_xml_files(corpus_root):
        if file_limit and scanned >= file_limit:
            break
        sample_file = rel(path, corpus_root)
        try:
            for event, elem in ET.iterparse(path, events=("start",)):
                tag_ns, tag_name = split_xml_name(elem.tag)
                if tag_ns:
                    add_hit(facts["namespaces"], tag_ns, sample_file)
                add_hit(facts["elementNames"], tag_name, sample_file)
                if event == "start" and not facts.get("_rootSeen"):
                    add_hit(facts["rootElements"], tag_name, sample_file)
                    facts["_rootSeen"] = True
                for attr_name in elem.attrib:
                    attr_ns, attr_local = split_xml_name(attr_name)
                    if attr_ns:
                        add_hit(facts["namespaces"], attr_ns, sample_file)
                    add_hit(facts["attributeNames"], attr_local, sample_file)
                elem.clear()
        except (ET.ParseError, OSError, UnicodeError) as exc:
            add_hit(facts["parseErrors"], type(exc).__name__, f"{sample_file}: {exc}")
        finally:
            facts.pop("_rootSeen", None)
            facts["filesScanned"].append(sample_file)
            scanned += 1

    return freeze_hits(facts)


def iter_corpus_xml_files(corpus_root: Path) -> list[Path]:
    suffixes = {".xml", ".xsd", ".mxl", ".xcu", ".form", ".right", ".mdo", ".cmd"}
    return sorted(
        path
        for path in corpus_root.rglob("*")
        if path.is_file() and (path.suffix.lower() in suffixes or path.name == "ConfigDumpInfo.xml")
    )


def split_xml_name(name: str) -> tuple[str, str]:
    if name.startswith("{") and "}" in name:
        namespace, local = name[1:].split("}", 1)
        return namespace, local
    return "", name


def collect_dependency_hints(text: str, sample_file: str, facts: dict[str, Any]) -> None:
    for match in re.finditer(r"io\.github\.1c-syntax:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+\-]+", text):
        add_hit(facts["mdclassesImports"], match.group(0), sample_file)


def class_bucket(sample_file: str, class_name: str) -> str | None:
    if "/validator/" in sample_file and class_name.endswith("Validator"):
        return "validatorClasses"
    if "/writer/" in sample_file and class_name.endswith(("Writer", "Editor", "Remover")):
        return "writerClasses"
    if "/oracle/" in sample_file:
        return "oracleClasses"
    if "/dsl/" in sample_file:
        return "dslClasses"
    return None


def looks_like_message_word(value: str) -> bool:
    if len(value) < 2:
        return True
    if value.lower() in {"true", "false", "null", "json", "text", "full", "brief"}:
        return True
    return False


def freeze_hits(obj: Any) -> Any:
    if isinstance(obj, dict):
        result = {}
        for key, value in obj.items():
            if isinstance(value, SymbolHit):
                result[key] = {"count": value.count, "samples": value.samples}
            else:
                result[key] = freeze_hits(value)
        return result
    if isinstance(obj, list):
        return [freeze_hits(x) for x in obj]
    return obj


def keys(facts: dict[str, Any], bucket: str) -> set[str]:
    value = facts.get(bucket, {})
    if not isinstance(value, dict):
        return set()
    return set(value.keys())


def build_delta(xsd: dict[str, Any], xmlgen: dict[str, Any], corpus: dict[str, Any] | None = None) -> dict[str, Any]:
    xsd_all = xsd["all"]
    literals = keys(xmlgen, "stringLiterals")
    xml_names = keys(xmlgen, "xmlNameLiterals") | keys(xmlgen, "rootElementMentions")
    namespace_refs = keys(xmlgen, "namespaceUris")

    xsd_namespaces = keys(xsd_all, "namespaces")
    xsd_elements = keys(xsd_all, "globalElements")
    xsd_complex = keys(xsd_all, "globalComplexTypes")
    xsd_simple = keys(xsd_all, "globalSimpleTypes")
    xsd_attrs = keys(xsd_all, "globalAttributes")
    xsd_required_attrs = keys(xsd_all, "requiredAttributes")
    xsd_required_elements = keys(xsd_all, "requiredElements")
    xsd_enum_pairs = keys(xsd_all, "enumValues")
    xsd_enum_values = {pair.split("::", 1)[1] for pair in xsd_enum_pairs if "::" in pair}

    mdclasses_symbols = set()
    for imp in keys(xmlgen, "mdclassesImports"):
        mdclasses_symbols.add(imp.rsplit(".", 1)[-1])

    delta = {
        "summary": {
            "xsdVersions": list(xsd["versions"].keys()),
            "xsdFiles": len(xsd_all.get("files", [])),
            "xmlgenFilesScanned": len(xmlgen.get("filesScanned", [])),
            "xsdNamespaces": len(xsd_namespaces),
            "xmlgenNamespaceRefs": len(namespace_refs),
            "xsdGlobalElements": len(xsd_elements),
            "xsdGlobalComplexTypes": len(xsd_complex),
            "xsdGlobalSimpleTypes": len(xsd_simple),
            "xsdEnumPairs": len(xsd_enum_pairs),
            "xmlgenNameLiterals": len(xml_names),
            "mdclassesImports": len(keys(xmlgen, "mdclassesImports")),
        },
        "namespaceDelta": {
            "xsdNotReferencedByXmlGen": sorted(xsd_namespaces - namespace_refs),
            "xmlGenReferencedNotInXsd": sorted(namespace_refs - xsd_namespaces),
            "common": sorted(xsd_namespaces & namespace_refs),
        },
        "coverageDelta": {
            "globalElementsNotMentioned": sorted(xsd_elements - xml_names - literals),
            "globalComplexTypesNotMentioned": sorted(xsd_complex - xml_names - literals - mdclasses_symbols),
            "globalSimpleTypesNotMentioned": sorted(xsd_simple - xml_names - literals - mdclasses_symbols),
            "globalAttributesNotMentioned": sorted(xsd_attrs - xml_names - literals),
            "requiredAttributesNotMentioned": sorted(xsd_required_attrs - xml_names - literals),
            "requiredElementsNotMentioned": sorted(xsd_required_elements - xml_names - literals),
            "enumValuesNotMentioned": sorted(xsd_enum_values - literals - xml_names),
            "xmlNameLiteralsNotInXsdGlobalNames": sorted(
                xml_names - xsd_elements - xsd_complex - xsd_simple - xsd_attrs
            ),
        },
        "mdclassesCoverageHints": {
            "imports": sorted(keys(xmlgen, "mdclassesImports")),
            "symbols": sorted(mdclasses_symbols),
        },
    }
    if corpus is not None:
        corpus_namespaces = keys(corpus, "namespaces")
        corpus_elements = keys(corpus, "elementNames") | keys(corpus, "rootElements")
        corpus_attrs = keys(corpus, "attributeNames")
        delta["summary"]["corpusRoot"] = corpus.get("root", "")
        delta["summary"]["corpusFilesScanned"] = len(corpus.get("filesScanned", []))
        delta["summary"]["corpusParseErrorKinds"] = len(corpus.get("parseErrors", {}))
        delta["corpusDelta"] = {
            "xsdNamespacesObservedInCorpusButNotReferencedByXmlGen": sorted(
                (xsd_namespaces & corpus_namespaces) - namespace_refs
            ),
            "xsdGlobalElementsObservedInCorpusButNotMentionedByXmlGen": sorted(
                (xsd_elements & corpus_elements) - xml_names - literals
            ),
            "xsdRequiredAttributesObservedInCorpusButNotMentionedByXmlGen": sorted(
                (xsd_required_attrs & corpus_attrs) - xml_names - literals
            ),
            "corpusNamespacesNotInXsdSet": sorted(corpus_namespaces - xsd_namespaces),
            "corpusElementsNotInXsdGlobalNames": sorted(
                corpus_elements - xsd_elements - xsd_complex - xsd_simple
            ),
            "corpusAttributesNotInXsdGlobalOrRequiredAttributes": sorted(
                corpus_attrs - xsd_attrs - xsd_required_attrs
            ),
        }
    return delta


def compact_delta(delta: dict[str, Any], limit: int) -> dict[str, Any]:
    result = json.loads(json.dumps(delta, ensure_ascii=False))
    for section in ("namespaceDelta", "coverageDelta", "corpusDelta"):
        for key, value in list(result.get(section, {}).items()):
            if isinstance(value, list):
                result[section][key] = {
                    "total": len(value),
                    "items": value[:limit],
                }
    return result


def write_markdown(report: dict[str, Any], path: Path, limit: int) -> None:
    delta = report["delta"]
    summary = delta["summary"]
    lines = [
        "# XSD coverage delta",
        "",
        "This report compares namespace-forest XSD facts with xml-gen source coverage facts.",
        "Missing entries mean not found in scanned xml-gen source literals/imports/classes.",
        "",
        "## Summary",
        "",
    ]
    for key, value in summary.items():
        lines.append(f"- `{key}`: {value}")

    lines.extend(["", "## Namespace Delta", ""])
    append_list_section(lines, "XSD namespaces not referenced by xml-gen", delta["namespaceDelta"]["xsdNotReferencedByXmlGen"], limit)
    append_list_section(lines, "xml-gen namespace refs not in XSD set", delta["namespaceDelta"]["xmlGenReferencedNotInXsd"], limit)

    lines.extend(["", "## Coverage Delta", ""])
    for title, key in [
        ("Global elements not mentioned", "globalElementsNotMentioned"),
        ("Global complex types not mentioned", "globalComplexTypesNotMentioned"),
        ("Global simple types not mentioned", "globalSimpleTypesNotMentioned"),
        ("Global attributes not mentioned", "globalAttributesNotMentioned"),
        ("Required attributes not mentioned", "requiredAttributesNotMentioned"),
        ("Required elements not mentioned", "requiredElementsNotMentioned"),
        ("Enum values not mentioned", "enumValuesNotMentioned"),
        ("xml-gen XML-name literals not in XSD global names", "xmlNameLiteralsNotInXsdGlobalNames"),
    ]:
        append_list_section(lines, title, delta["coverageDelta"][key], limit)

    lines.extend(["", "## mdclasses / bsl-common-library hints", ""])
    append_list_section(lines, "Imported symbols", delta["mdclassesCoverageHints"]["imports"], limit)

    if "corpusDelta" in delta:
        lines.extend(["", "## Corpus Delta", ""])
        for title, key in [
            (
                "XSD namespaces observed in corpus but not referenced by xml-gen",
                "xsdNamespacesObservedInCorpusButNotReferencedByXmlGen",
            ),
            (
                "XSD global elements observed in corpus but not mentioned by xml-gen",
                "xsdGlobalElementsObservedInCorpusButNotMentionedByXmlGen",
            ),
            (
                "XSD required attributes observed in corpus but not mentioned by xml-gen",
                "xsdRequiredAttributesObservedInCorpusButNotMentionedByXmlGen",
            ),
            ("Corpus namespaces not in XSD set", "corpusNamespacesNotInXsdSet"),
            ("Corpus elements not in XSD global names", "corpusElementsNotInXsdGlobalNames"),
            ("Corpus attributes not in XSD global/required attributes", "corpusAttributesNotInXsdGlobalOrRequiredAttributes"),
        ]:
            append_list_section(lines, title, delta["corpusDelta"][key], limit)

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def append_list_section(lines: list[str], title: str, items: list[str], limit: int) -> None:
    lines.append(f"### {title}")
    lines.append("")
    lines.append(f"Total: `{len(items)}`")
    lines.append("")
    for item in items[:limit]:
        lines.append(f"- `{item}`")
    if len(items) > limit:
        lines.append(f"- ... {len(items) - limit} more")
    lines.append("")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build namespace-forest XSD vs xml-gen implementation coverage delta."
    )
    parser.add_argument("--forest", required=True, type=Path, help="Path to namespace-forest checkout.")
    parser.add_argument(
        "--xmlgen-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Path to tools/xml-gen root. Default: parent of this script.",
    )
    parser.add_argument(
        "--version",
        default="latest",
        help="Schema version to compare: latest, all, or concrete version like 2.21.",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("build/xsd-coverage-delta"),
        help="Output directory for JSON and Markdown reports.",
    )
    parser.add_argument(
        "--corpus-root",
        type=Path,
        help="Optional root with real XML/config dumps to add observed-source coverage.",
    )
    parser.add_argument(
        "--corpus-file-limit",
        type=int,
        default=0,
        help="Max corpus files to parse. Default 0 means no limit.",
    )
    parser.add_argument("--limit", type=int, default=80, help="Max items per Markdown section.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    forest = args.forest.resolve()
    xmlgen_root = args.xmlgen_root.resolve()
    out_dir = args.out_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    versions = select_versions(forest, args.version)
    xsd_facts = extract_xsd_facts(forest, versions)
    xmlgen_facts = extract_xmlgen_facts(xmlgen_root)
    corpus_facts = extract_corpus_facts(args.corpus_root.resolve(), args.corpus_file_limit) if args.corpus_root else None
    delta = build_delta(xsd_facts, xmlgen_facts, corpus_facts)

    report = {
        "inputs": {
            "forest": str(forest),
            "xmlgenRoot": str(xmlgen_root),
            "version": args.version,
            "resolvedVersions": versions,
        },
        "delta": delta,
        "xsdFacts": xsd_facts,
        "xmlgenFacts": xmlgen_facts,
    }
    if corpus_facts is not None:
        report["corpusFacts"] = corpus_facts

    full_json = out_dir / "xsd-coverage-delta.json"
    compact_json = out_dir / "xsd-coverage-delta.compact.json"
    markdown = out_dir / "xsd-coverage-delta.md"
    full_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    compact_json.write_text(
        json.dumps({"inputs": report["inputs"], "delta": compact_delta(delta, args.limit)}, ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )
    write_markdown(report, markdown, args.limit)

    summary = delta["summary"]
    print(f"Versions: {', '.join(versions)}")
    print(f"XSD files: {summary['xsdFiles']}; xml-gen files scanned: {summary['xmlgenFilesScanned']}")
    if corpus_facts is not None:
        print(f"Corpus files scanned: {summary['corpusFilesScanned']}")
    print(f"Report: {markdown}")
    print(f"Full JSON: {full_json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

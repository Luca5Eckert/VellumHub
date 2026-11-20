#!/usr/bin/env python3
"""Verify the Markdown architecture knowledge graph without third-party dependencies.

Run from any working directory: python3 tools/architecture/validate_docs.py
No application network or credentials are required. Rendering Mermaid is a
separate visual validation; this tool checks structure, references and paths.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs" / "architecture"
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
FENCE = re.compile(r"```mermaid\s*([\s\S]*?)```")
VALID_DIAGRAMS = {
    "C4Context",
    "C4Container",
    "C4Component",
    "sequenceDiagram",
    "stateDiagram-v2",
}
DIAGRAM_TYPES = {
    "c4-context",
    "c4-container-diagram",
    "c4-component-diagram",
    "sequence-diagram",
    "state-machine",
}
CODES = {"VH-AUT", "VH-GTW", "VH-USR", "VH-CAT", "VH-ENG", "VH-REC"}


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def mermaid_text_hazards(block: str) -> list[str]:
    """Return lines whose text would break Mermaid parsing.

    In sequence and state diagrams the lexer ends a statement at ';' and treats '#'
    as the start of an entity code, so either character inside a message or label
    silently truncates it and usually turns the rest into a parse error.
    """
    hazards = []
    for number, line in enumerate(block.splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("%%"):
            continue
        if ";" in stripped or "#" in stripped:
            hazards.append(f"line {number}: {stripped}")
    return hazards


def metadata(raw: str, path: Path, errors: list[str]) -> tuple[dict[str, str], str]:
    if not raw.startswith("---\n"):
        fail(f"{path}: missing frontmatter", errors)
        return {}, raw
    split = raw.find("\n---\n", 4)
    if split == -1:
        fail(f"{path}: unclosed frontmatter", errors)
        return {}, raw
    header, body = raw[4:split], raw[split + 5:]
    values = dict(re.findall(r"^([a-z_]+):\s*(.*?)\s*$", header, re.M))
    for key in ("id", "slug", "type", "system", "status", "related"):
        if key not in values:
            fail(f"{path}: missing {key} metadata", errors)
    if values.get("system") != "vellumhub":
        fail(f"{path}: unexpected system", errors)
    if not re.fullmatch(r"vh-[a-z0-9-]+", values.get("id", "")):
        fail(f"{path}: id must be stable vh-kebab-case", errors)
    if not re.fullmatch(r"[a-z0-9-]+", values.get("slug", "")):
        fail(f"{path}: slug must be kebab-case", errors)
    return values, body


def related_keys(raw: str) -> list[str]:
    match = re.search(r"^related:\s*(.*)$", raw, re.M)
    if not match:
        return []
    inline = match.group(1).strip()
    if inline.startswith("["):
        return [s.strip() for s in inline.strip("[]").split(",") if s.strip()]
    content = raw[match.end():]
    result = []
    for line in content.splitlines():
        if line.startswith("  - "):
            result.append(line.removeprefix("  - ").strip())
        elif line.strip():
            break
    return result


def validate() -> int:
    errors: list[str] = []
    ids: dict[str, Path] = {}
    services: dict[str, Path] = {}
    docs = sorted(DOCS.rglob("*.md"))
    if len(docs) < 18:
        fail(f"Expected >=18 architecture Markdown pages, found {len(docs)}", errors)
    entries = []
    for path in docs:
        raw = path.read_text(encoding="utf-8")
        meta, body = metadata(raw, path, errors)
        document_id = meta.get("id")
        if document_id in ids:
            fail(f"{path}: duplicate id {document_id} (first {ids[document_id]})", errors)
        elif document_id:
            ids[document_id] = path
        if path.parent.name == "services" and path.name != "README.md":
            code = meta.get("code")
            if code not in CODES:
                fail(f"{path}: invalid or missing code {code}", errors)
            elif code in services:
                fail(f"{path}: repeated service code {code}", errors)
            services[code] = path
            if meta.get("slug") != path.stem or "source_path" not in meta or "data_store" not in meta:
                fail(f"{path}: service slug/source_path/data_store mismatch", errors)
        blocks = FENCE.findall(body)
        if meta.get("type") in DIAGRAM_TYPES and len(blocks) != 1:
            fail(f"{path}: expected one Mermaid diagram, got {len(blocks)}", errors)
        for block in blocks:
            first = next((l.strip() for l in block.splitlines() if l.strip()), "")
            if first not in VALID_DIAGRAMS:
                fail(f"{path}: unsupported Mermaid diagram type {first}", errors)
            if first in ("sequenceDiagram", "stateDiagram-v2"):
                for hazard in mermaid_text_hazards(block):
                    fail(f"{path}: ';' or '#' breaks Mermaid text ({hazard})", errors)
        opened = raw.count("```")
        if opened % 2:
            fail(f"{path}: unbalanced code fences", errors)
        for href in LINK.findall(body):
            href = href.strip().split(" ", 1)[0]
            if href.startswith(("http://", "https://", "#", "mailto:")):
                continue
            target = unquote(href.split("#", 1)[0])
            if target and not (path.parent / target).resolve().exists():
                fail(f"{path}: broken relative Markdown link -> {target}", errors)
        entries.append((path, raw))
    if set(services) != CODES:
        fail(f"Expected all six service codes; got {set(services)}", errors)
    for path, raw in entries:
        for document_id in related_keys(raw):
            if document_id not in ids:
                fail(f"{path}: related id does not exist: {document_id}", errors)
    viewer = DOCS / "architecture-explorer.html"
    if not viewer.is_file():
        fail("Missing Markdown-backed HTML explorer", errors)
    for err in errors:
        print("ERROR", err, file=sys.stderr)
    if errors:
        print(f"Architecture graph invalid: {len(errors)} issue(s)", file=sys.stderr)
        return 1
    print(f"Architecture graph OK: {len(docs)} pages, {len(services)} service codes, "
          f"{len(ids)} unique IDs, valid relative links and diagram fences")
    return 0


if __name__ == "__main__":
    raise SystemExit(validate())

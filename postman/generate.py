#!/usr/bin/env python3
"""Regenerate the VellumHub Postman collection from live OpenAPI contracts."""

from __future__ import annotations

import argparse
import copy
import json
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
OUTPUT = HERE / "VellumHub.postman_collection.json"
WORKFLOWS = HERE / "workflows.json"
SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"

SERVICES = (
    ("user", "User Service", "/docs/user/v3/api-docs", "/api/v1"),
    ("catalog", "Catalog Service", "/docs/catalog/v3/api-docs", "/api/v1/catalog"),
    ("engagement", "Engagement Service", "/docs/engagement/v3/api-docs", "/api/v1/engagement"),
    ("recommendation", "Recommendation Service", "/docs/recommendation/v3/api-docs", "/api/v1"),
)
METHODS = ("get", "post", "put", "patch", "delete", "head", "options")
KNOWN = {
    "email": "{{email}}",
    "password": "{{password}}",
    "bookid": "{{bookId}}",
    "userid": "{{userId}}",
    "ratingid": "{{ratingId}}",
    "reactionid": "{{reactionId}}",
    "booklistid": "{{bookListId}}",
    "listid": "{{bookListId}}",
    "isbn": "{{isbn}}",
}


class GenerationError(RuntimeError):
    pass


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-url", default="http://localhost:8080")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def fetch(url: str, timeout: float) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "vellumhub-postman-generator/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read().decode(response.headers.get_content_charset() or "utf-8")
    except (urllib.error.URLError, TimeoutError) as exc:
        raise GenerationError(f"failed to fetch {url}: {exc}") from exc
    try:
        result = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise GenerationError(f"{url} did not return valid JSON") from exc
    if not isinstance(result, dict):
        raise GenerationError(f"{url} did not return a JSON object")
    return result


def validate(spec: dict[str, Any], service: str) -> None:
    if not str(spec.get("openapi", "")).startswith("3."):
        raise GenerationError(f"{service} must expose OpenAPI 3.x")
    if not isinstance(spec.get("paths"), dict) or not spec["paths"]:
        raise GenerationError(f"{service} exposes no OpenAPI operations")


def resolve(spec: dict[str, Any], value: Any) -> Any:
    if not isinstance(value, dict) or "$ref" not in value:
        return value
    ref = value["$ref"]
    if not isinstance(ref, str) or not ref.startswith("#/"):
        raise GenerationError(f"unsupported OpenAPI reference: {ref!r}")
    current: Any = spec
    for part in ref[2:].split("/"):
        part = part.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or part not in current:
            raise GenerationError(f"broken OpenAPI reference: {ref}")
        current = current[part]
    return current


def named_value(name: str | None) -> str | None:
    if not name:
        return None
    key = name.lower()
    if key in KNOWN:
        return KNOWN[key]
    if key.endswith("id"):
        return "{{" + name + "}}"
    return None


def example(
    spec: dict[str, Any],
    raw_schema: Any,
    name: str | None = None,
    depth: int = 0,
    refs: frozenset[str] = frozenset(),
) -> Any:
    if depth > 7 or not isinstance(raw_schema, dict):
        return None

    if "$ref" in raw_schema:
        ref = raw_schema["$ref"]
        if ref in refs:
            return None
        return example(spec, resolve(spec, raw_schema), name, depth + 1, refs | {ref})

    for key in ("example", "default"):
        if key in raw_schema:
            return copy.deepcopy(raw_schema[key])
    if raw_schema.get("enum"):
        return copy.deepcopy(raw_schema["enum"][0])

    for union in ("oneOf", "anyOf"):
        if raw_schema.get(union):
            return example(spec, raw_schema[union][0], name, depth + 1, refs)

    if raw_schema.get("allOf"):
        merged: dict[str, Any] = {}
        for part in raw_schema["allOf"]:
            value = example(spec, part, name, depth + 1, refs)
            if isinstance(value, dict):
                merged.update(value)
        return merged

    kind = raw_schema.get("type")
    if kind is None and "properties" in raw_schema:
        kind = "object"

    if kind == "object":
        result = {}
        for prop_name, prop_schema in raw_schema.get("properties", {}).items():
            prop_schema = resolve(spec, prop_schema)
            if isinstance(prop_schema, dict) and prop_schema.get("readOnly"):
                continue
            value = example(spec, prop_schema, prop_name, depth + 1, refs)
            if value is not None:
                result[prop_name] = value
        return result

    if kind == "array":
        singular = name[:-1] if name and name.endswith("s") else name
        value = example(spec, raw_schema.get("items", {}), singular, depth + 1, refs)
        return [] if value is None else [value]

    variable = named_value(name)
    if variable is not None:
        return variable

    if kind == "boolean":
        return False
    if kind == "integer":
        return int(raw_schema.get("minimum", 0))
    if kind == "number":
        return float(raw_schema.get("minimum", 0))
    if kind in ("string", None):
        fmt = raw_schema.get("format")
        if fmt == "uuid":
            return "{{" + (name or "id") + "}}"
        if fmt == "email":
            return "{{email}}"
        if fmt == "date-time":
            return "2026-01-01T00:00:00Z"
        if fmt == "date":
            return "2026-01-01"
        if fmt == "uri":
            return "https://example.com"
        if fmt in ("binary", "byte"):
            return None
        return "string"
    return None


def parameter_value(spec: dict[str, Any], parameter: dict[str, Any]) -> str:
    if "example" in parameter:
        return str(parameter["example"])
    schema = resolve(spec, parameter.get("schema", {}))
    if isinstance(schema, dict):
        for key in ("example", "default"):
            if key in schema:
                return str(schema[key])
        if schema.get("enum"):
            return str(schema["enum"][0])
    name = str(parameter.get("name", "value"))
    return named_value(name) or "{{" + name + "}}"


def parameters(spec: dict[str, Any], path_item: dict[str, Any], operation: dict[str, Any]) -> list[dict[str, Any]]:
    merged: dict[tuple[str, str], dict[str, Any]] = {}
    for source in (path_item.get("parameters", []), operation.get("parameters", [])):
        if not isinstance(source, list):
            continue
        for raw in source:
            parameter = resolve(spec, raw)
            if isinstance(parameter, dict):
                merged[(str(parameter.get("in", "")), str(parameter.get("name", "")))] = parameter
    return list(merged.values())


def request_body(spec: dict[str, Any], operation: dict[str, Any]) -> tuple[dict[str, Any] | None, list[dict[str, str]]]:
    raw = operation.get("requestBody")
    if raw is None:
        return None, []
    body = resolve(spec, raw)
    content = body.get("content", {}) if isinstance(body, dict) else {}
    if not content:
        return None, []

    preferred = ("application/json", "multipart/form-data", "application/x-www-form-urlencoded", "text/plain")
    media_type = next((item for item in preferred if item in content), next(iter(content)))
    media = content[media_type]
    schema = resolve(spec, media.get("schema", {})) if isinstance(media, dict) else {}

    if media_type == "application/json" or media_type.endswith("+json"):
        value = media.get("example") if isinstance(media, dict) else None
        if value is None:
            value = example(spec, schema)
        return {
            "mode": "raw",
            "raw": json.dumps(value if value is not None else {}, indent=2, ensure_ascii=False),
            "options": {"raw": {"language": "json"}},
        }, [{"key": "Content-Type", "value": media_type, "type": "text"}]

    if media_type == "multipart/form-data":
        rows = []
        for name, raw_prop in (schema.get("properties", {}) if isinstance(schema, dict) else {}).items():
            prop = resolve(spec, raw_prop)
            if isinstance(prop, dict) and prop.get("format") == "binary":
                rows.append({"key": name, "type": "file", "src": []})
            else:
                value = example(spec, prop, name)
                if isinstance(value, (dict, list)):
                    value = json.dumps(value, ensure_ascii=False)
                rows.append({"key": name, "value": "" if value is None else str(value), "type": "text"})
        return {"mode": "formdata", "formdata": rows}, []

    if media_type == "application/x-www-form-urlencoded":
        rows = []
        for name, raw_prop in (schema.get("properties", {}) if isinstance(schema, dict) else {}).items():
            value = example(spec, resolve(spec, raw_prop), name)
            rows.append({"key": name, "value": "" if value is None else str(value), "type": "text"})
        return {"mode": "urlencoded", "urlencoded": rows}, [
            {"key": "Content-Type", "value": media_type, "type": "text"}
        ]

    value = media.get("example") if isinstance(media, dict) else None
    if value is None:
        value = example(spec, schema)
    return {"mode": "raw", "raw": "" if value is None else str(value)}, [
        {"key": "Content-Type", "value": media_type, "type": "text"}
    ]


def secured(spec: dict[str, Any], operation: dict[str, Any]) -> bool:
    security = operation["security"] if "security" in operation else spec.get("security")
    return bool(security)


def request_item(
    spec: dict[str, Any],
    contract_path: str,
    prefix: str,
    path: str,
    path_item: dict[str, Any],
    method: str,
    operation: dict[str, Any],
) -> tuple[dict[str, Any], set[str]]:
    variables: set[str] = set()
    query = []
    public = prefix.rstrip("/") + "/" + path.lstrip("/")

    for parameter in parameters(spec, path_item, operation):
        name = str(parameter.get("name", ""))
        location = parameter.get("in")
        if location == "path":
            variables.add(name)
            public = public.replace("{" + name + "}", "{{" + name + "}}")
        elif location == "query":
            query.append({
                "key": name,
                "value": parameter_value(spec, parameter),
                "disabled": not bool(parameter.get("required")),
                "description": parameter.get("description", ""),
            })

    raw_url = "{{gatewayUrl}}" + public
    enabled = [entry for entry in query if not entry["disabled"]]
    if enabled:
        raw_url += "?" + "&".join(f"{entry['key']}={entry['value']}" for entry in enabled)

    body, body_headers = request_body(spec, operation)
    request: dict[str, Any] = {
        "method": method.upper(),
        "header": [{"key": "Accept", "value": "application/json", "type": "text"}] + body_headers,
        "url": {
            "raw": raw_url,
            "host": ["{{gatewayUrl}}"],
            "path": [part for part in public.split("/") if part],
        },
        "description": (
            f"{operation.get('description') or operation.get('summary') or ''}\n\n"
            f"Generated from `{contract_path}`. Operation ID: `{operation.get('operationId', 'n/a')}`."
        ).strip(),
    }
    if query:
        request["url"]["query"] = query
    if body is not None:
        request["body"] = body
    if not secured(spec, operation):
        request["auth"] = {"type": "noauth"}

    return {
        "name": operation.get("summary") or operation.get("operationId") or f"{method.upper()} {path}",
        "request": request,
        "response": [],
    }, variables


def service_folder(
    spec: dict[str, Any],
    service_name: str,
    contract_path: str,
    prefix: str,
) -> tuple[dict[str, Any], set[str], int]:
    grouped: dict[str, list[tuple[str, str, dict[str, Any], dict[str, Any]]]] = defaultdict(list)
    for path in sorted(spec["paths"]):
        path_item = spec["paths"][path]
        if not isinstance(path_item, dict):
            continue
        for method in METHODS:
            operation = path_item.get(method)
            if isinstance(operation, dict):
                tags = operation.get("tags")
                tag = str(tags[0]) if isinstance(tags, list) and tags else "Other"
                grouped[tag].append((path, method, path_item, operation))

    folders = []
    variables: set[str] = set()
    count = 0
    for tag in sorted(grouped):
        items = []
        for path, method, path_item, operation in grouped[tag]:
            item, found = request_item(spec, contract_path, prefix, path, path_item, method, operation)
            items.append(item)
            variables.update(found)
            count += 1
        folders.append({"name": tag, "item": items})
    return {
        "name": service_name,
        "description": f"Generated from `{contract_path}`.",
        "item": folders,
    }, variables, count


def workflows() -> dict[str, Any]:
    try:
        value = json.loads(WORKFLOWS.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GenerationError(f"cannot read {WORKFLOWS}: {exc}") from exc
    if not isinstance(value, dict) or not isinstance(value.get("item"), list):
        raise GenerationError("workflows.json must be a Postman folder object")
    return value


def discovery() -> dict[str, Any]:
    return {
        "name": "OpenAPI contract discovery",
        "description": "Diagnostic requests for the live service-owned OpenAPI documents.",
        "item": [
            {
                "name": service_name,
                "request": {
                    "auth": {"type": "noauth"},
                    "method": "GET",
                    "header": [{"key": "Accept", "value": "application/json", "type": "text"}],
                    "url": "{{gatewayUrl}}" + contract_path,
                    "description": "Live OpenAPI input used by `postman/generate.py`.",
                },
                "response": [],
            }
            for _, service_name, contract_path, _ in SERVICES
        ],
    }


def generate(gateway_url: str, timeout: float) -> tuple[dict[str, Any], dict[str, int]]:
    gateway_url = gateway_url.rstrip("/")
    items = [workflows(), discovery()]
    variables: set[str] = set()
    counts = {}

    for key, service_name, contract_path, prefix in SERVICES:
        spec = fetch(gateway_url + contract_path, timeout)
        validate(spec, service_name)
        folder, found, count = service_folder(spec, service_name, contract_path, prefix)
        items.append(folder)
        variables.update(found)
        counts[key] = count

    collection_variables = [
        {"key": "gatewayUrl", "value": gateway_url, "type": "string"},
        {"key": "token", "value": "", "type": "string"},
    ]
    for name in sorted(variables):
        if name not in ("gatewayUrl", "token"):
            collection_variables.append({"key": name, "value": "", "type": "string"})

    return {
        "info": {
            "_postman_id": "18d90453-70d7-4f70-8a68-5b341f9bf035",
            "name": "VellumHub",
            "description": (
                "Generated from service-owned OpenAPI contracts exposed by the gateway. "
                "Only `Cross-service workflows` is maintained manually."
            ),
            "schema": SCHEMA,
        },
        "auth": {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}],
        },
        "variable": collection_variables,
        "item": items,
    }, counts


def render(value: dict[str, Any]) -> str:
    return json.dumps(value, indent=2, ensure_ascii=False) + "\n"


def main() -> int:
    options = args()
    try:
        collection, counts = generate(options.gateway_url, options.timeout)
    except GenerationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    output = options.output.resolve()
    generated = render(collection)
    if options.check:
        if not output.exists() or output.read_text(encoding="utf-8") != generated:
            print(
                "error: Postman collection is out of date; run `python postman/generate.py` "
                "with the local stack running and commit the result.",
                file=sys.stderr,
            )
            return 1
        print("Postman collection is aligned with the current OpenAPI contracts.")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(generated, encoding="utf-8")
        print(f"Wrote {output}")

    print("Operations imported: " + ", ".join(f"{key}={value}" for key, value in counts.items()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

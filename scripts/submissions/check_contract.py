#!/usr/bin/env python3
"""Check the draft's references, examples and critical HTTP contract invariants.

Requires PyYAML and jsonschema. This is not a substitute for an OpenAPI validator
or runtime integration tests.
"""
import json
import datetime
import uuid
from pathlib import Path

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "docs/design/submissions"
spec = yaml.safe_load((CONTRACT / "openapi.yaml").read_text())
formats = jsonschema.FormatChecker()


@formats.checks("uuid", raises=(ValueError, AttributeError))
def valid_uuid(value):
    return not isinstance(value, str) or str(uuid.UUID(value)) == value.lower()


@formats.checks("date-time", raises=(ValueError, TypeError))
def valid_timestamp(value):
    if not isinstance(value, str):
        return True
    return "T" in value and datetime.datetime.fromisoformat(value.replace("Z", "+00:00")).tzinfo is not None


def resolve(value):
    if isinstance(value, dict):
        if "$ref" in value:
            target = spec
            assert value["$ref"].startswith("#/"), value
            for part in value["$ref"][2:].split("/"):
                target = target[part.replace("~1", "/").replace("~0", "~")]
            return resolve(target)
        return {key: resolve(item) for key, item in value.items()}
    if isinstance(value, list):
        return [resolve(item) for item in value]
    return value


expanded = resolve(spec)
operations = []
for path, methods in expanded["paths"].items():
    assert path.startswith("/api/v3/submissions")
    for method, operation in methods.items():
        operations.append(operation["operationId"])
        parameters = operation.get("parameters", [])
        if "{id}" in path:
            assert any(p["name"] == "id" and p["required"] for p in parameters)
        if method in ("post", "put", "delete") and "{id}" in path:
            assert any(p["name"] == "If-Match" and p["required"] for p in parameters)
            assert "412" in operation["responses"] and "428" in operation["responses"]
        assert "401" in operation["responses"]
        assert "403" in operation["responses"]
        assert "Retry-After" in operation["responses"]["429"]["headers"]
        if "requestBody" in operation:
            for media in operation["requestBody"]["content"].values():
                jsonschema.Draft4Validator.check_schema(media["schema"])
assert len(operations) == len(set(operations))

for name, example in json.loads((CONTRACT / "examples.json").read_text()).items():
    schema = expanded["components"]["schemas"][example["schema"]]
    errors = list(jsonschema.Draft4Validator(
        schema, format_checker=formats
    ).iter_errors(example["value"]))
    assert bool(errors) != example.get("valid", True), (name, errors)
    print("Checked example:", name)

create = expanded["components"]["schemas"]["Create"]
assert create["properties"]["processing"]["properties"]["mode"]["default"] == "detect-and-identify"
published_modes = yaml.safe_load((ROOT / "src/main/resources/openapi.yaml").read_text())["components"]["schemas"]["SubmissionApiProcessing"]["properties"]["mode"]
assert published_modes == spec["components"]["schemas"]["Processing"]["properties"]["mode"]
commit = expanded["paths"]["/api/v3/submissions/{id}/commit"]["post"]
assert "202" in commit["responses"]
assert any(p["name"] == "Idempotency-Key" and p["required"] for p in commit["parameters"])
assert any(p["name"] == "Idempotency-Key" and p["required"] for p in
           expanded["paths"]["/api/v3/submissions"]["post"]["parameters"])
for path, method in [("/api/v3/submissions/{id}", "get"),
                     ("/api/v3/submissions/{id}/rows", "put"),
                     ("/api/v3/submissions/{id}/rows", "get"),
                     ("/api/v3/submissions/{id}/files", "post"),
                     ("/api/v3/submissions/{id}/validate", "post")]:
    assert "ETag" in expanded["paths"][path][method]["responses"]["200"]["headers"]
print(f"Checked {len(operations)} operations and all local references.")


# Optional runtime evidence, emitted by the servlet and PostgreSQL acceptance tests.
if "--runtime" in __import__("sys").argv:
    published = yaml.safe_load((ROOT / "src/main/resources/openapi.yaml").read_text())
    for label, schema_name in [("capabilities", "Capabilities"), ("accepted", "Accepted")]:
        value = json.loads((ROOT / "target" / ("submissions-" + label + ".json")).read_text())
        jsonschema.Draft4Validator(expanded["components"]["schemas"][schema_name], format_checker=formats).validate(value)
        original = spec
        spec = published
        runtime_schema = resolve(published["components"]["schemas"]["SubmissionApi" + schema_name])
        spec = original
        jsonschema.Draft4Validator(runtime_schema, format_checker=formats).validate(value)
        print("Checked runtime response against both specs:", label)

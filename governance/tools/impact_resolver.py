#!/usr/bin/env python3
"""Read-only Amin governance impact resolver.

Inputs are a JSON change request. The resolver reads the Node Registry and
Dependency Impact Rules, calculates reverse dependency impact, and emits JSON.
It never modifies repository source or governance source-of-truth files.
"""
from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict, deque
from pathlib import Path

NODE_RE = re.compile(r"^\s*- node_id:\s*(AMIN-\d+)\s*$")
DEP_RE = re.compile(r"^\s*dependencies:\s*\[(.*?)\]\s*$")
PARENT_RE = re.compile(r"^\s*parent_id:\s*(AMIN-\d+|null)\s*$")
SOURCE_REF_RE = re.compile(r"reference:\s*([^,}\n]+)")
AMIN_ID_RE = re.compile(r"AMIN-\d+")


def parse_registry(path: Path):
    nodes = {}
    current = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        m = NODE_RE.match(raw)
        if m:
            current = m.group(1)
            nodes.setdefault(current, {"dependencies": [], "parent_id": None, "source_paths": []})
            continue
        if not current:
            continue
        m = DEP_RE.match(raw)
        if m:
            nodes[current]["dependencies"] = AMIN_ID_RE.findall(m.group(1))
            continue
        m = PARENT_RE.match(raw)
        if m:
            nodes[current]["parent_id"] = None if m.group(1) == "null" else m.group(1)
            continue
        if "reference:" in raw:
            ref = SOURCE_REF_RE.search(raw)
            if ref:
                value = ref.group(1).strip().strip("'\"")
                if "/" in value and not value.startswith("PR-"):
                    nodes[current]["source_paths"].append(value)
    return nodes


def trigger_nodes(rules_text: str, changed_paths):
    # Conservative path-driven extraction: if a changed path appears in a known
    # trigger block, collect AMIN IDs from that block only.
    result = set()
    blocks = re.split(r"\n\s*- trigger:\s*", rules_text)
    for block in blocks[1:]:
        if any(path in block for path in changed_paths):
            result.update(AMIN_ID_RE.findall(block))
    return result


def classify(change, impacted, trigger_hits):
    high_flags = [
        change.get("permission_changes"),
        change.get("release_path_changes"),
        change.get("destructive_data_change"),
        change.get("security_boundary_change"),
    ]
    if change.get("change_type") == "release" or any(high_flags):
        return "high", True, True
    if trigger_hits or len(impacted) > len(change.get("changed_node_ids", [])):
        return "medium", False, False
    if change.get("change_type") in ("docs", "governance"):
        return "low", False, False
    return "medium", False, False


def resolve(change, registry, rules_text):
    changed = list(dict.fromkeys(change.get("changed_node_ids", [])))
    unknown = [n for n in changed if n not in registry]
    if unknown:
        return {
            "change_id": change.get("change_id", "unknown"),
            "changed_nodes": changed,
            "impacted_nodes": [],
            "validation_scope": [],
            "risk_level": "high",
            "unresolved_gaps": [f"unknown_node:{n}" for n in unknown],
            "requires_human_review": True,
            "block_release": True,
            "valid": False,
        }

    reverse = defaultdict(set)
    for node, data in registry.items():
        for dep in data["dependencies"]:
            reverse[dep].add(node)

    impacted = set(changed)
    queue = deque(changed)
    impact_edges = []
    while queue:
        node = queue.popleft()
        for consumer in sorted(reverse.get(node, ())):
            impact_edges.append({"from": node, "to": consumer, "type": "reverse_dependency"})
            if consumer not in impacted:
                impacted.add(consumer)
                queue.append(consumer)

    changed_paths = change.get("changed_paths", []) or []
    shared_path_nodes = set()
    for node, data in registry.items():
        if any(p in data["source_paths"] or any(p == s for s in data["source_paths"]) for p in changed_paths):
            shared_path_nodes.add(node)
    impacted.update(shared_path_nodes)

    trigger_hits = trigger_nodes(rules_text, changed_paths)
    impacted.update(trigger_hits)

    risk, requires_review, block_release = classify(change, impacted, trigger_hits)
    scope = ["changed_nodes", "module_tests"]
    if len(impacted) > len(changed):
        scope = ["changed_nodes", "transitive_dependents", "integration_tests"]
    if trigger_hits:
        scope.append("contract_trigger_validation")
    if risk == "high":
        scope.append("human_approval")

    return {
        "change_id": change.get("change_id", "unknown"),
        "changed_nodes": changed,
        "impacted_nodes": sorted(impacted),
        "impact_paths": impact_edges,
        "validation_scope": list(dict.fromkeys(scope)),
        "risk_level": risk,
        "unresolved_gaps": [],
        "requires_human_review": requires_review,
        "block_release": block_release,
        "source_records": [
            "governance/node-registry.yaml",
            "governance/dependency-impact-rules.yaml",
        ],
        "valid": True,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("request", help="Path to JSON change request")
    parser.add_argument("--registry", default="governance/node-registry.yaml")
    parser.add_argument("--rules", default="governance/dependency-impact-rules.yaml")
    parser.add_argument("--output", default="")
    args = parser.parse_args()

    change = json.loads(Path(args.request).read_text(encoding="utf-8"))
    registry = parse_registry(Path(args.registry))
    rules_text = Path(args.rules).read_text(encoding="utf-8")
    result = resolve(change, registry, rules_text)
    payload = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(payload + "\n", encoding="utf-8")
    print(payload)
    raise SystemExit(0 if result["valid"] else 2)


if __name__ == "__main__":
    main()

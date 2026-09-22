#!/usr/bin/env python3
"""Generate a governed work package from natural-language request text.

This tool is read-only with respect to source-of-truth governance and product files.
It maps text to existing registered node candidates, requires explicit selection when
ambiguous, runs the existing impact resolver, and emits a structured work package.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from impact_resolver import parse_registry, resolve  # noqa: E402

NAME_RE = re.compile(r"^\s*name:\s*(.+?)\s*$")
NODE_RE = re.compile(r"^\s*- node_id:\s*(AMIN-\d+)\s*$")


def normalize(text: str) -> str:
    return re.sub(r"[\s_\-:/]+", "", (text or "").strip().lower())


def parse_names(path: Path):
    names = {}
    current = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        m = NODE_RE.match(raw)
        if m:
            current = m.group(1)
            continue
        if current:
            m = NAME_RE.match(raw)
            if m and current not in names:
                names[current] = m.group(1).strip().strip("'\"")
    return names


def candidate_nodes(text, registry, names, alias_data):
    q = normalize(text)
    scored = []
    for node_id in registry:
        phrases = [names.get(node_id, "")] + alias_data.get("nodes", {}).get(node_id, [])
        best = 0
        matched = []
        for phrase in phrases:
            p = normalize(phrase)
            if not p:
                continue
            score = 0
            if p == q:
                score = 100
            elif p in q:
                score = 80 + min(15, len(p))
            elif q and q in p:
                score = 55 + min(15, len(q))
            if score > 0:
                matched.append(phrase)
                best = max(best, score)
        if best:
            scored.append({
                "node_id": node_id,
                "name": names.get(node_id, ""),
                "score": best,
                "matched_phrases": matched,
            })
    scored.sort(key=lambda x: (-x["score"], x["node_id"]))
    return scored


def slug(text):
    cleaned = re.sub(r"[^a-z0-9]+", "-", (text or "change").lower()).strip("-")
    return cleaned[:40] or "change"


def make_issue_body(request, impact):
    nodes = "\n".join(f"  - {n}" for n in impact["changed_nodes"])
    impacted = "\n".join(f"  - {n}" for n in impact["impacted_nodes"])
    scope = "\n".join(f"  - {s}" for s in impact["validation_scope"])
    return f"""## Request\n{request['request_text']}\n\n## Governance\n```yaml\nchange_id: {request['change_id']}\nnode_ids:\n{nodes}\nchange_type: {request['change_type']}\nrisk_level: {impact['risk_level']}\nrequires_human_review: {str(impact['requires_human_review']).lower()}\n```\n\n## Impacted nodes\n{impacted or '- none'}\n\n## Validation scope\n{scope or '- none'}\n\n## Unresolved gaps\n{chr(10).join('- ' + x for x in impact.get('unresolved_gaps', [])) or '- none'}\n"""


def generate(request, registry_path, rules_path, aliases_path):
    registry = parse_registry(registry_path)
    names = parse_names(registry_path)
    aliases = json.loads(aliases_path.read_text(encoding="utf-8"))
    rules_text = rules_path.read_text(encoding="utf-8")

    text = request.get("request_text", "").strip()
    if not text:
        return {"valid": False, "status": "invalid_request", "unresolved_gaps": ["missing_request_text"], "block_release": True}

    candidates = candidate_nodes(text, registry, names, aliases)
    selected = request.get("selected_node_ids") or []

    if selected:
        unknown = [n for n in selected if n not in registry]
        if unknown:
            return {
                "valid": False,
                "status": "unknown_selected_node",
                "candidates": candidates[:8],
                "unresolved_gaps": [f"unknown_node:{n}" for n in unknown],
                "block_release": True,
            }
        chosen = selected
    elif not candidates:
        return {
            "valid": False,
            "status": "no_candidate",
            "candidates": [],
            "unresolved_gaps": ["no_registered_node_matches_request"],
            "requires_human_review": True,
            "block_release": True,
        }
    else:
        top = candidates[0]["score"]
        top_candidates = [c for c in candidates if c["score"] >= top - 5]
        if len(top_candidates) != 1 or top < 80:
            return {
                "valid": False,
                "status": "selection_required",
                "candidates": candidates[:8],
                "unresolved_gaps": ["ambiguous_node_mapping"],
                "requires_human_review": True,
                "block_release": True,
            }
        chosen = [top_candidates[0]["node_id"]]

    change = {
        "change_id": request.get("change_id", "WP-UNSPECIFIED"),
        "changed_node_ids": chosen,
        "change_type": request.get("change_type", "feature"),
        "changed_paths": request.get("changed_paths", []),
        "permission_changes": bool(request.get("permission_changes", False)),
        "release_path_changes": bool(request.get("release_path_changes", False)),
        "destructive_data_change": bool(request.get("destructive_data_change", False)),
        "security_boundary_change": bool(request.get("security_boundary_change", False)),
    }
    impact = resolve(change, registry, rules_text)
    if not impact.get("valid"):
        return {"valid": False, "status": "impact_invalid", "candidates": candidates[:8], "impact": impact, "block_release": True}

    branch = f"feature/{slug(request.get('branch_hint') or request['change_id'])}"
    package = {
        "package_version": "0.1.0-draft",
        "status": "generated",
        "review_status": "generated",
        "request": request,
        "node_resolution": {
            "selected_node_ids": chosen,
            "candidates": candidates[:8],
            "selection_source": "explicit" if selected else "unique_high_confidence_candidate",
        },
        "impact": impact,
        "execution": {
            "suggested_branch": branch,
            "issue_title": f"[Governed Change] {request['request_text'][:80]}",
            "issue_body": make_issue_body(request, impact),
            "pr_governance": {
                "change_id": change["change_id"],
                "node_ids": chosen,
                "change_type": change["change_type"],
                "affected_dependencies": impact["impacted_nodes"],
                "validation_scope": impact["validation_scope"],
                "risk_level": impact["risk_level"],
                "human_approval_required": impact["requires_human_review"],
            },
        },
        "source_records": [
            "governance/node-registry.yaml",
            "governance/node-aliases.json",
            "governance/dependency-impact-rules.yaml",
            "governance/tools/impact_resolver.py",
        ],
        "unresolved_gaps": impact.get("unresolved_gaps", []),
        "can_modify_source": False,
        "can_make_final_decision": False,
        "valid": True,
    }
    return package


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("request", help="Path to natural-language request JSON")
    parser.add_argument("--registry", default="governance/node-registry.yaml")
    parser.add_argument("--rules", default="governance/dependency-impact-rules.yaml")
    parser.add_argument("--aliases", default="governance/node-aliases.json")
    parser.add_argument("--output", default="")
    args = parser.parse_args()

    request = json.loads(Path(args.request).read_text(encoding="utf-8"))
    result = generate(request, Path(args.registry), Path(args.rules), Path(args.aliases))
    payload = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(payload + "\n", encoding="utf-8")
    print(payload)
    raise SystemExit(0 if result.get("valid") else 2)


if __name__ == "__main__":
    main()

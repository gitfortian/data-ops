#!/usr/bin/env python3
"""R7: real role denial matrix on already-normalized Golden source successes."""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import sys

from bootstrap import Api, MARKER, PROJECT_NAME, current_commit, required_env
from historical_recovery import PendingEvidence, RECOVERY, connect_readonly, positive_id, validate_page, verify_project
from historical_recovery_denials import assert_denied, assert_unchanged, request_raw, source_snapshot, usage_snapshot
from historical_recovery_reader_outage import select_existing_success, request_params


def actor_digest(api):
    value = api.user.get("id")
    if value is None:
        raise PendingEvidence("PENDING: authenticated actor ID missing")
    return sha256(str(value).encode()).hexdigest()[:16]


def verify_roles(owner, restricted, anonymous, db, project_id, control_id, kind, sample):
    cfg = RECOVERY[kind]
    source_id = positive_id(sample["id"])
    version_id = positive_id(sample[cfg["version"]])
    source = source_snapshot(db, kind, project_id, source_id, version_id)
    if not source:
        raise PendingEvidence("PENDING: no retained successful source audit")
    selected = select_existing_success(
        db, kind, project_id, control_id, sample, positive_id(source[0]["id"]))
    if actor_digest(owner) == actor_digest(restricted):
        raise ValueError("Owner and restricted actor must be different authenticated users")
    before = usage_snapshot(db, project_id, control_id)
    states = {
        (kind, project_id, source_id, version_id): source,
        (kind, control_id, source_id, version_id): selected["control"],
    }
    params = request_params(selected)
    owner.project_id = str(project_id)
    allowed = owner.request("POST", cfg["path"], params=params)
    validate_page(kind, allowed, selected["productKey"], version_id,
                  selected["cursor"], [selected["row"]], 1)
    assert_unchanged(db, project_id, control_id, before, states)
    owner.project_id = str(control_id)
    try:
        empty = owner.request("POST", cfg["path"], params=params)
        validate_page(kind, empty, selected["productKey"], version_id,
                      selected["cursor"], [], 1)
    finally:
        owner.project_id = str(project_id)
    assert_unchanged(db, project_id, control_id, before, states)
    anonymous_denied = assert_denied(request_raw(
        anonymous, "POST", owner.base_url, cfg["path"], params, project_id),
        "anonymous-with-forged-project")
    assert_unchanged(db, project_id, control_id, before, states)
    restricted_denied = assert_denied(request_raw(
        restricted.session, "POST", restricted.base_url, cfg["path"], params, project_id),
        "restricted-account")
    assert_unchanged(db, project_id, control_id, before, states)
    no_project_denied = assert_denied(request_raw(
        restricted.session, "POST", restricted.base_url, cfg["path"], params),
        "restricted-without-project")
    assert_unchanged(db, project_id, control_id, before, states)
    return {
        "kind": kind, "productKey": selected["productKey"],
        "sourceVersionIdentity": selected["sourceVersionIdentity"],
        "auditId": selected["auditId"],
        "ownerActorDigest": actor_digest(owner),
        "restrictedActorDigest": actor_digest(restricted),
        "ownerAlreadyNormalizedReplay": "PASSED", "controlProjectEmpty": "PASSED",
        "anonymousDenied": anonymous_denied, "restrictedDenied": restricted_denied,
        "restrictedNoProjectDenied": no_project_denied,
        "usageAndSourceUnchanged": "PASSED",
        "accessAttribution": "DENIAL_OBSERVED_ROLE_CAUSE_NOT_VERIFIED",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--kind", choices=["DATASET", "DATA_SERVICE", "BOTH"], default="BOTH")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({
            "mode": "PLAN", "scenarios": [
                "owner already-normalized one-row replay", "empty control Project",
                "anonymous forged Project rejection", "separate restricted login denial",
                "restricted missing Project rejection"],
            "required": ["R1/R2 Golden owners", "preexisting persisted Usage",
                         "two distinct real accounts", "SELECT-only app database"],
            "limitations": ["denial alone does not prove Asset.READ rather than membership",
                            "browser and deployment identity remain PENDING"]}, indent=2))
        return
    if not all((args.physical_manifest, args.consumption_report, args.output)):
        raise ValueError("--apply requires both manifests and output")
    physical = json.loads(args.physical_manifest.read_text(encoding="utf-8"))
    consumption = json.loads(args.consumption_report.read_text(encoding="utf-8"))
    if physical.get("marker") != MARKER or consumption.get("marker") != MARKER:
        raise ValueError("Only Golden-owned manifests are allowed")
    project_id, control_id = positive_id(physical["projectId"]), positive_id(physical["controlProjectId"])
    if project_id == control_id or positive_id(consumption["projectId"]) != project_id:
        raise ValueError("Golden Project IDs disagree")
    base_url = os.environ.get("YAK_OPS_BASE_URL", physical["baseUrl"])
    owner = Api(base_url, required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    restricted = Api(base_url, required_env("YAK_GOLDEN_RESTRICTED_USERNAME"),
                     required_env("YAK_GOLDEN_RESTRICTED_PASSWORD"))
    verify_project(owner, project_id, PROJECT_NAME)
    verify_project(owner, control_id, PROJECT_NAME + " 对照")
    owner.project_id = str(project_id)
    import requests
    kinds = list(RECOVERY) if args.kind == "BOTH" else [args.kind]
    with connect_readonly() as db:
        rows = [verify_roles(owner, restricted, requests, db, project_id, control_id,
                             kind, consumption["samples"][RECOVERY[kind]["sample"]])
                for kind in kinds]
    report = {
        "marker": MARKER, "sceneId": "J3-RECOVERY-REAL-ROLE-MATRIX",
        "result": "REAL_ROLE_DENIAL_MATRIX_OBSERVED", "productAcceptance": "PARTIAL",
        "repositoryCommit": current_commit(), "deploymentCommit": None,
        "deploymentIdentity": "UNVERIFIED", "projectId": str(project_id),
        "controlProjectId": str(control_id),
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "scenarios": rows, "remaining": [
            "independently verified Project membership / Asset.READ permission attribution",
            "browser and deployed artifact identity"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except PendingEvidence as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
    except Exception as exc:
        print("Golden role matrix failed: " +
              (str(exc) if isinstance(exc, ValueError)
               else type(exc).__name__ + "; inspect private application logs"), file=sys.stderr)
        sys.exit(1)

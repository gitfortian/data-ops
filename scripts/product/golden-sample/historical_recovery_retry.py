#!/usr/bin/env python3
"""R5: opt-in two-stage *real* same-page recovery after a source normalization fault.

An operator prepares/restores a fault in an isolated Golden Sample deployment.
This script does not create faults, mutate source audits, or write Usage via SQL.
Stage blocked proves one real page cannot advance. Stage recovered repeats its
identical cursor, checks the source-owned audit, normalized Usage and idempotence.
"""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import sys

from bootstrap import Api, MARKER, PROJECT_NAME, current_commit, required_env
from historical_recovery import (
    PendingEvidence, RECOVERY, connect_readonly, indexed_usage, positive_id,
    read_source, read_usage, require, source_refs, transport_cursor,
    validate_page, verify_project,
)
from historical_recovery_denials import (
    SNAPSHOT_MAX_ROWS, assert_unchanged, source_snapshot, usage_snapshot,
)

LONG_MAX = 9223372036854775807


def signature(row):
    """Fingerprint the source-owned record, never emit user identity or query text."""
    return sha256(json.dumps(
        row, sort_keys=True, separators=(",", ":"), default=str
    ).encode("utf-8")).hexdigest()


def select_audit(db, kind, project_id, source_id, version_id, target_id):
    audits = source_snapshot(db, kind, project_id, source_id, version_id)
    matched = [audit for audit in audits if positive_id(audit["id"]) == target_id]
    if len(matched) != 1:
        raise PendingEvidence("PENDING: target successful audit is not retained in the exact version")
    row = matched[0]
    # With LIMIT=1, the exclusive persisted-id cursor must select exactly this row.
    # target+1 avoids relying on the presence of any consecutive audit ID.
    if target_id == LONG_MAX:
        require(positive_id(audits[0]["id"]) == LONG_MAX,
                "Largest signed audit ID must be the first persisted source record")
        before = None
    else:
        before = str(target_id + 1)
    identity = source_refs(kind, [row])
    return audits, row, before, identity


def request_one(api, kind, product_key, version_id, before):
    cfg = RECOVERY[kind]
    params = {
        "productKey": product_key, "sourceVersionIdentity": str(version_id), "limit": 1,
    }
    if before is not None:
        params[cfg["cursor"]] = before
    # Existing Api enforces authenticated HTTP 200 + application code 200.
    # A server 5xx is a FAIL, not proof of an expected recoverable GAP.
    return api.request("POST", cfg["path"], params=params)


def validate_blocked(kind, page, product_key, version_id, before):
    cfg = RECOVERY[kind]
    require(isinstance(page, dict), "Blocked recovery returned no structured page")
    require(page.get("productKey") == product_key
            and page.get("sourceVersionIdentity") == str(version_id),
            "Blocked response lost the exact source version")
    require(page.get("requestedBeforeAuditId" if kind == "DATASET"
                     else "requestedBeforeInvocationId") == before,
            "Blocked page changed the exclusive persisted audit cursor")
    require(page.get("requestedLimit") == 1
            and page.get("visitedAuditCount") == 1,
            "Blocked fixture must be exactly one retained successful source audit")
    require(page.get("normalizedOrAlreadyPresentCount") == 0
            and page.get("normalizationGapCount", 0) + page.get(
                "normalizationUnavailableCount", 0) == 1,
            "No real normalization GAP/IGNORED or UNAVAILABLE occurred")
    require(page.get("retryRequired") is True
            and page.get(cfg["next"]) is None
            and page.get("retainedAuditExhausted") is False,
            "Blocked page falsely advanced cursor or claimed source exhausted")
    # GAP counts also include IGNORED in the server DTO. Do not claim root cause.
    return {
        "gapOrIgnoredCount": page["normalizationGapCount"],
        "unavailableCount": page["normalizationUnavailableCount"],
        "retryRequired": True, "continuationBlocked": True,
    }


def compare_recovered_usage(before, after, expected, kind):
    prior = {row["id"]: row for row in before}
    current = {row["id"]: row for row in after}
    require(len(prior) == len(before) and len(current) == len(after),
            "Usage snapshot contains repeated row IDs")
    require(all(current.get(i) == value for i, value in prior.items()),
            "Same-page recovery rewrote or deleted unrelated persisted Usage")
    new = [row for row in after if row["id"] not in prior]
    require(len(new) == 1, "Same-page recovery did not create exactly one Usage row")
    refs = indexed_usage(new, expected, RECOVERY[kind]["mode"])
    require(len(refs) == 1, "Recovered Usage identity/mode/outcome differs from source truth")
    return new[0]["id"]


def verify_stage(api, db, kind, sample, project_id, control_id,
                 target_id, stage, blocked_report=None):
    cfg = RECOVERY[kind]
    source_id = positive_id(sample["id"])
    version_id = positive_id(sample[cfg["version"]])
    product_key = f"{kind}:{source_id}"
    source, row, before, expected = select_audit(
        db, kind, project_id, source_id, version_id, target_id)
    control = source_snapshot(db, kind, control_id, source_id, version_id)
    if control:
        raise PendingEvidence("PENDING: control Project contains colliding source success audit")
    current_signature = signature(row)
    evidence_fingerprint = sha256(next(iter(expected)).encode("utf-8")).hexdigest()
    before_all = usage_snapshot(db, project_id, control_id)
    before_exact = read_usage(
        db, kind, project_id, product_key, version_id, SNAPSHOT_MAX_ROWS + 1)
    if len(before_exact) > SNAPSHOT_MAX_ROWS:
        raise PendingEvidence("PENDING: exact-version Usage exceeds bounded snapshot")
    old_matches = indexed_usage(before_exact, expected, cfg["mode"])
    source_states = {
        (kind, project_id, source_id, version_id): source,
        (kind, control_id, source_id, version_id): control,
    }
    if stage == "blocked":
        if old_matches:
            raise PendingEvidence("PENDING: selected audit was already normalized before fault observation")
        page = request_one(api, kind, product_key, version_id, before)
        blocked = validate_blocked(kind, page, product_key, version_id, before)
        assert_unchanged(db, project_id, control_id, before_all, source_states)
        return {
            "stage": "BLOCKED_REAL_OBSERVED", "kind": kind,
            "productKey": product_key, "sourceVersionIdentity": str(version_id),
            "auditId": str(target_id), "requestedCursor": before,
            "sourceSignature": current_signature,
            "providerEvidenceFingerprint": evidence_fingerprint,
            "blocked": blocked, "usageAndSourceUnchanged": "PASSED",
        }

    require(stage == "recovered" and isinstance(blocked_report, dict),
            "Recovered stage requires the prior real blocked-stage report")
    for field, value in {
        "marker": MARKER, "result": "REAL_BLOCKED_PAGE_OBSERVED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
    }.items():
        require(blocked_report.get(field) == value,
                "Blocked report identity or result does not match current Golden environment")
    prior = blocked_report.get("scenario")
    require(isinstance(prior, dict)
            and prior.get("stage") == "BLOCKED_REAL_OBSERVED"
            and prior.get("kind") == kind
            and prior.get("productKey") == product_key
            and prior.get("sourceVersionIdentity") == str(version_id)
            and prior.get("auditId") == str(target_id)
            and prior.get("requestedCursor") == before
            and prior.get("sourceSignature") == current_signature
            and prior.get("providerEvidenceFingerprint") == evidence_fingerprint,
            "Recovered run must use the identical source audit, version and cursor")
    if old_matches:
        raise PendingEvidence("PENDING: chosen Usage is already normalized before this retry")
    # The environment owner restores the fault *outside* this tool. We never
    # mutate a source audit or change a Consumer / Version to manufacture PASS.
    result = request_one(api, kind, product_key, version_id, before)
    validate_page(kind, result, product_key, version_id, before, [row], 1)
    after_all = usage_snapshot(db, project_id, control_id)
    new_id = compare_recovered_usage(before_all, after_all, expected, kind)
    require(source_snapshot(db, kind, project_id, source_id, version_id) == source
            and source_snapshot(db, kind, control_id, source_id, version_id) == control,
            "Successful retry changed source-owned audit or control Project")

    # Repeat precisely the same request: the same normalized Usage row must survive.
    replay = request_one(api, kind, product_key, version_id, before)
    validate_page(kind, replay, product_key, version_id, before, [row], 1)
    assert_unchanged(db, project_id, control_id, after_all, source_states)
    after_exact = read_usage(
        db, kind, project_id, product_key, version_id, SNAPSHOT_MAX_ROWS + 1)
    require(indexed_usage(after_exact, expected, cfg["mode"]) == {
        next(iter(expected)): new_id
    }, "Replayed recovery changed the persisted source-to-Usage identity")
    return {
        "stage": "RECOVERED_REAL_VERIFIED", "kind": kind,
        "productKey": product_key, "sourceVersionIdentity": str(version_id),
        "auditId": str(target_id), "requestedCursor": before,
        "sourceSignature": current_signature,
        "providerEvidenceFingerprint": evidence_fingerprint,
        "priorBlockedContinuation": "VERIFIED",
        "normalizedUsageCreated": 1, "sameCursorReplay": "PASSED",
        "sourceAuditUnchanged": "PASSED", "controlProjectUnchanged": "PASSED",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--stage", choices=["blocked", "recovered"], required=False)
    parser.add_argument("--kind", choices=["DATASET", "DATA_SERVICE"])
    parser.add_argument("--audit-id")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    parser.add_argument("--blocked-report", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({
            "mode": "PLAN", "stages": ["blocked", "recovered"],
            "fixture": "operator-staged reversible normalization fault on exactly one "
                       "retained, source-attributable successful audit",
            "writes": ["existing authenticated one-row recovery POST only"],
            "cannotClaim": ["unobserved GAP or UNAVAILABLE",
                            "outage inferred from HTTP 5xx",
                            "confirmed deployment build hash",
                            "all-time historical usage or browser E2E"],
        }, indent=2))
        return
    require(args.stage and args.kind and args.audit_id
            and args.physical_manifest and args.consumption_report and args.output,
            "--apply requires stage, kind, audit-id, both manifests and output")
    require(args.stage != "recovered" or args.blocked_report,
            "Recovered stage requires --blocked-report from the previous real blocked run")
    if args.blocked_report:
        require(args.output.resolve() != args.blocked_report.resolve(),
                "Recovery result cannot overwrite the blocked-stage evidence")
    target_id = positive_id(args.audit_id)
    physical = json.loads(args.physical_manifest.read_text(encoding="utf-8"))
    consumption = json.loads(args.consumption_report.read_text(encoding="utf-8"))
    require(physical.get("marker") == MARKER and consumption.get("marker") == MARKER,
            "Only owner-marked isolated Golden Sample manifests are accepted")
    project_id = positive_id(physical["projectId"])
    control_id = positive_id(physical["controlProjectId"])
    require(project_id != control_id
            and positive_id(consumption["projectId"]) == project_id,
            "Golden Project and control Project identities must match both manifests")
    api = Api(os.environ.get("YAK_OPS_BASE_URL", physical["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    verify_project(api, project_id, PROJECT_NAME)
    verify_project(api, control_id, PROJECT_NAME + " 对照")
    api.project_id = str(project_id)
    prior = None
    if args.stage == "recovered":
        prior = json.loads(args.blocked_report.read_text(encoding="utf-8"))
    with connect_readonly() as db:
        scenario = verify_stage(
            api, db, args.kind, consumption["samples"][RECOVERY[args.kind]["sample"]],
            project_id, control_id, target_id, args.stage, prior)
    report = {
        "marker": MARKER,
        "sceneId": "J3-EXACT-VERSION-SAME-PAGE-FAULT-RETRY",
        "result": ("REAL_BLOCKED_PAGE_OBSERVED" if args.stage == "blocked"
                   else "REAL_SAME_CURSOR_RECOVERY_VERIFIED"),
        "productAcceptance": "PARTIAL",
        "repositoryCommit": current_commit(),
        "deploymentCommit": None, "deploymentIdentity": "UNVERIFIED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "scenario": scenario,
        "remaining": ["verified deployment artifact identity",
                      "browser/restricted role matrix",
                      "source reader outage and HTTP 5xx are not certified by this tool",
                      "all-time audit retention"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except PendingEvidence as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
    except Exception as error:
        print("Golden same-page recovery failed: "
              + (str(error) if isinstance(error, ValueError)
                 else type(error).__name__ + "; consult restricted application logs"),
              file=sys.stderr)
        sys.exit(1)

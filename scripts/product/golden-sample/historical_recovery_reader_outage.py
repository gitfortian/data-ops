#!/usr/bin/env python3
"""R6: real source-reader outage rejection and same-cursor restoration evidence.

Only operator-owned isolated Golden projects. This runner NEVER injects a fault,
alters source audit, creates synthetic success, or writes Usage via SQL. Target
a source SUCCESS which already has its persisted normalized Usage: both outage
and restored-page requests are required to leave all Usage rows unchanged.
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
    read_usage, require, source_refs, validate_page, verify_project,
)
from historical_recovery_denials import (
    SNAPSHOT_MAX_ROWS, assert_unchanged, source_snapshot, usage_snapshot,
)
from historical_recovery_retry import LONG_MAX, signature

# Generic application exception advice may return Result.fail(999) under HTTP 200.
# Neither 4xx, other business codes nor 200/success count as source-reader outage.
APP_INTERNAL_FAILURE = 999


def select_existing_success(db, kind, project_id, control_id, sample, audit_id):
    cfg = RECOVERY[kind]
    source_id = positive_id(sample["id"])
    version_id = positive_id(sample[cfg["version"]])
    product = f"{kind}:{source_id}"
    source = source_snapshot(db, kind, project_id, source_id, version_id)
    control = source_snapshot(db, kind, control_id, source_id, version_id)
    if control:
        raise PendingEvidence("PENDING: control Project has colliding source audit")
    matching = [x for x in source if positive_id(x["id"]) == audit_id]
    if len(matching) != 1:
        raise PendingEvidence("PENDING: exact successful audit is no longer retained")
    row = matching[0]
    expected = source_refs(kind, [row])
    # Force limit=1 and an exclusive source audit ID cursor for precisely one row.
    if audit_id == LONG_MAX:
        require(positive_id(source[0]["id"]) == LONG_MAX,
                "Largest signed BIGINT must be the first retained audit ID")
        cursor = None
    else:
        cursor = str(audit_id + 1)
    usage = read_usage(
        db, kind, project_id, product, version_id, SNAPSHOT_MAX_ROWS + 1)
    if len(usage) > SNAPSHOT_MAX_ROWS:
        raise PendingEvidence("PENDING: exact-version Usage exceeds bounded snapshot")
    normalized = indexed_usage(usage, expected, cfg["mode"])
    if len(normalized) != 1:
        raise PendingEvidence(
            "PENDING: chose a SUCCESS source audit without already normalized Usage")
    return {
        "kind": kind, "productKey": product, "sourceVersionIdentity": str(version_id),
        "auditId": str(audit_id), "cursor": cursor, "row": row,
        "source": source, "control": control,
        "expected": expected,
        "sourceId": source_id, "versionId": version_id,
        "sourceSignature": signature(row),
        "evidenceRefDigest": sha256(next(iter(expected)).encode("utf-8")).hexdigest(),
        "persistedUsageId": str(next(iter(normalized.values()))),
    }


def request_params(selected):
    cfg = RECOVERY[selected["kind"]]
    params = {
        "productKey": selected["productKey"],
        "sourceVersionIdentity": selected["sourceVersionIdentity"],
        "limit": 1,
    }
    if selected["cursor"] is not None:
        params[cfg["cursor"]] = selected["cursor"]
    return params


def classify_reader_failure(reply):
    """Only genuine server/internal failure responses can count as outage observed."""
    status = reply.status_code
    require(isinstance(status, int), "Reader outage returned no HTTP status")
    if 500 <= status <= 599:
        return f"HTTP_{status}"
    if status == 200:
        try:
            envelope = reply.json()
        except (ValueError, TypeError):
            raise ValueError("HTTP 200 reader failure lacked a structured application envelope") from None
        require(isinstance(envelope, dict)
                and envelope.get("code") == APP_INTERNAL_FAILURE
                and envelope.get("data") is None,
                "HTTP 200 was not a generic internal application failure")
        return "APP_INTERNAL_999"
    raise ValueError("Reader outage response is not a server failure (4xx/redirect/success)")


def request_during_outage(api, selected):
    cfg = RECOVERY[selected["kind"]]
    return api.session.request(
        "POST", api.base_url + cfg["path"], params=request_params(selected),
        headers={"X-YAK-SECURITY-PROJECT-ID": str(api.project_id)},
        timeout=30, allow_redirects=False)


def verify_stage(api, db, project_id, control_id, selected, stage, failure_report=None):
    kind = selected["kind"]
    before = usage_snapshot(db, project_id, control_id)
    source_states = {
        (kind, project_id, selected["sourceId"], selected["versionId"]):
            selected["source"],
        (kind, control_id, selected["sourceId"], selected["versionId"]):
            selected["control"],
    }
    identity = {
        "kind": kind, "productKey": selected["productKey"],
        "sourceVersionIdentity": selected["sourceVersionIdentity"],
        "auditId": selected["auditId"], "requestedCursor": selected["cursor"],
        "sourceSignature": selected["sourceSignature"],
        "evidenceRefDigest": selected["evidenceRefDigest"],
        "persistedUsageId": selected["persistedUsageId"],
    }

    if stage == "outage":
        reply = request_during_outage(api, selected)
        # A wrongly unstaged outage could produce a successful idempotent page.
        # We must inspect HTTP rather than trusting a hidden Result.success(empty).
        assert_unchanged(db, project_id, control_id, before, source_states)
        failure = classify_reader_failure(reply)
        return {**identity, "stage": "REAL_READER_FAILURE_OBSERVED",
                "serverFailure": failure, "usageAndSourceUnchanged": "PASSED",
                "cause": "OPERATOR_LOG_CORRELATION_REQUIRED"}

    require(stage == "restored" and isinstance(failure_report, dict),
            "Restored stage requires prior outage report")
    required = {
        "marker": MARKER, "sceneId": "J3-EXACT-VERSION-SOURCE-READER-OUTAGE",
        "result": "REAL_READER_FAILURE_OBSERVED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
    }
    require(all(failure_report.get(k) == v for k, v in required.items()),
            "Prior reader failure report is not for this isolated environment")
    prior = failure_report.get("scenario")
    require(isinstance(prior, dict) and
            all(prior.get(k) == v for k, v in identity.items())
            and prior.get("stage") == "REAL_READER_FAILURE_OBSERVED"
            and prior.get("usageAndSourceUnchanged") == "PASSED"
            and prior.get("serverFailure") is not None,
            "Prior failed read does not match the exact source/Usage identity and cursor")
    cfg = RECOVERY[kind]
    # Recovery must return one already normalized source audit, not a successful
    # empty page or an incorrect exhausted=true/next cursor.
    result = api.request("POST", cfg["path"], params=request_params(selected))
    validate_page(
        kind, result, selected["productKey"],
        selected["sourceVersionIdentity"], selected["cursor"], [selected["row"]], 1)
    assert_unchanged(db, project_id, control_id, before, source_states)

    # Idempotent replay: exact same request and unchanged persisted Usage IDs.
    replay = api.request("POST", cfg["path"], params=request_params(selected))
    validate_page(
        kind, replay, selected["productKey"],
        selected["sourceVersionIdentity"], selected["cursor"], [selected["row"]], 1)
    assert_unchanged(db, project_id, control_id, before, source_states)
    after_usage = read_usage(
        db, kind, project_id, selected["productKey"], selected["versionId"],
        SNAPSHOT_MAX_ROWS + 1)
    require(indexed_usage(after_usage, selected["expected"], cfg["mode"]) == {
        next(iter(selected["expected"])): int(selected["persistedUsageId"])
    }, "Restored Reader changed source-to-Usage normalized identity")
    return {**identity, "stage": "REAL_READER_RESTORED",
            "priorServerFailure": prior["serverFailure"],
            "restoredExactAuditCount": 1,
            "sameCursorReplay": "PASSED",
            "usageAndSourceUnchanged": "PASSED",
            "cause": "OPERATOR_LOG_CORRELATION_REQUIRED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--stage", choices=["outage", "restored"])
    parser.add_argument("--kind", choices=["DATASET", "DATA_SERVICE"])
    parser.add_argument("--audit-id")
    parser.add_argument("--confirm-reader-outage", action="store_true")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    parser.add_argument("--outage-report", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({
            "mode": "PLAN",
            "stages": ["outage", "restored"],
            "required": ["owned R1/R2 Golden Projects", "source SUCCESS with existing normalized Usage",
                         "independently SELECT-accessible application DB",
                         "operator-staged reversible *reader* failure and logs"],
            "writes": ["existing idempotent single-audit recovery POST; no SQL mutations"],
            "cannotClaim": ["reader-origin without operator-log correlation",
                            "browser/roles, deployed artifact identity, all historical usage"],
        }, indent=2))
        return
    require(args.stage and args.kind and args.audit_id
            and args.physical_manifest and args.consumption_report and args.output,
            "--apply requires stage, kind, audit-id, two manifests and an output file")
    require(args.stage != "outage" or args.confirm_reader_outage,
            "Outage stage requires --confirm-reader-outage after operator stages a reader failure")
    require(args.stage != "restored" or args.outage_report,
            "Restored stage requires --outage-report from the prior real HTTP failure")
    if args.outage_report:
        require(args.output.resolve() != args.outage_report.resolve(),
                "Restored report may not overwrite first-stage failure evidence")
    target_id = positive_id(args.audit_id)
    physical = json.loads(args.physical_manifest.read_text(encoding="utf-8"))
    consumption = json.loads(args.consumption_report.read_text(encoding="utf-8"))
    require(physical.get("marker") == MARKER and consumption.get("marker") == MARKER,
            "Only dedicated owner-marked Golden Sample manifests are accepted")
    project_id, control_id = (
        positive_id(physical["projectId"]), positive_id(physical["controlProjectId"]))
    require(project_id != control_id
            and positive_id(consumption["projectId"]) == project_id,
            "Project identities do not match isolated Golden manifests")
    api = Api(os.environ.get("YAK_OPS_BASE_URL", physical["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    verify_project(api, project_id, PROJECT_NAME)
    verify_project(api, control_id, PROJECT_NAME + " 对照")
    api.project_id = str(project_id)
    prior = (json.loads(args.outage_report.read_text(encoding="utf-8"))
             if args.stage == "restored" else None)
    with connect_readonly() as db:
        selected = select_existing_success(
            db, args.kind, project_id, control_id,
            consumption["samples"][RECOVERY[args.kind]["sample"]], target_id)
        scenario = verify_stage(api, db, project_id, control_id, selected,
                                args.stage, prior)
    report = {
        "marker": MARKER, "sceneId": "J3-EXACT-VERSION-SOURCE-READER-OUTAGE",
        "result": ("REAL_READER_FAILURE_OBSERVED" if args.stage == "outage"
                   else "REAL_READER_RESTORED"),
        "productAcceptance": "PARTIAL", "repositoryCommit": current_commit(),
        "deploymentCommit": None, "deploymentIdentity": "UNVERIFIED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
        "capturedAt": datetime.now(timezone.utc).isoformat(), "scenario": scenario,
        "remaining": ["operator logs proving failure root cause is specifically the source reader",
                      "verified deployment SHA/build hash", "restricted-role/browser matrix",
                      "GAP/UNAVAILABLE normalization (R5), history beyond retention"],
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
        print("Golden source reader outage test failed: "
              + (str(error) if isinstance(error, ValueError)
                 else type(error).__name__ + "; consult access-controlled app logs"),
              file=sys.stderr)
        sys.exit(1)

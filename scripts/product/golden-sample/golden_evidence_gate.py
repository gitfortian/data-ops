#!/usr/bin/env python3
"""R8: strict offline R3-R7 Golden report and artifact declaration correlation."""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import re
import sys

from bootstrap import MARKER
from historical_recovery import PendingEvidence, RECOVERY, positive_id, require

SHA40 = re.compile(r"^[a-fA-F0-9]{40}$")
SHA64 = re.compile(r"^[a-fA-F0-9]{64}$")
SCENES = {
    "r3": ("J3-EXACT-REVISION-RETAINED-OVER-200", "REAL_API_DB_RECOVERY_VERIFIED"),
    "r4": ("J3-RECOVERY-NEGATIVE-HTTP-DB", "REAL_HTTP_DB_NEGATIVE_PATHS_VERIFIED"),
    "r5Blocked": ("J3-EXACT-VERSION-SAME-PAGE-FAULT-RETRY", "REAL_BLOCKED_PAGE_OBSERVED"),
    "r5Recovered": ("J3-EXACT-VERSION-SAME-PAGE-FAULT-RETRY", "REAL_SAME_CURSOR_RECOVERY_VERIFIED"),
    "r6Outage": ("J3-EXACT-VERSION-SOURCE-READER-OUTAGE", "REAL_SERVER_FAILURE_OBSERVED"),
    "r6Restored": ("J3-EXACT-VERSION-SOURCE-READER-OUTAGE", "REAL_RESTORED_SOURCE_PAGE_VERIFIED"),
    "roles": ("J3-RECOVERY-REAL-ROLE-MATRIX", "REAL_ROLE_DENIAL_MATRIX_OBSERVED"),
}
STAGES = {
    "r5Blocked": "BLOCKED_REAL_OBSERVED",
    "r5Recovered": "RECOVERED_REAL_VERIFIED",
    "r6Outage": "REAL_SERVER_FAILURE_OBSERVED",
    "r6Restored": "REAL_RESTORED_SOURCE_PAGE_VERIFIED",
}


def load(path):
    obj = json.loads(Path(path).read_text(encoding="utf-8"))
    require(isinstance(obj, dict), "Golden evidence must be a JSON object")
    return obj


def validate_report(slot, report, project_id, control_id):
    scene, expected = SCENES[slot]
    require(report.get("marker") == MARKER
            and report.get("sceneId") == scene
            and report.get("result") == expected,
            "Incorrect Golden scene or result: " + slot)
    require(str(report.get("projectId")) == str(project_id)
            and str(report.get("controlProjectId")) == str(control_id),
            "Cross-Project evidence mismatch: " + slot)
    require(report.get("productAcceptance") == "PARTIAL"
            and report.get("deploymentCommit") is None
            and report.get("deploymentIdentity") == "UNVERIFIED",
            "Report falsely claims deployment/product PASS: " + slot)
    require(bool(SHA40.fullmatch(str(report.get("repositoryCommit", "")))),
            "Missing repository commit: " + slot)
    datetime.fromisoformat(report["capturedAt"].replace("Z", "+00:00"))
    if slot in STAGES:
        row = report.get("scenario")
        require(isinstance(row, dict)
                and row.get("stage") == STAGES[slot]
                and row.get("kind") in RECOVERY,
                "Invalid Golden staged evidence: " + slot)
        positive_id(row["auditId"])
        positive_id(row["sourceVersionIdentity"])
    else:
        rows = report.get("scenarios")
        require(isinstance(rows, list) and bool(rows),
                "Missing source scenario records: " + slot)
        kinds = set()
        for row in rows:
            require(isinstance(row, dict) and row.get("kind") in RECOVERY
                    and row["kind"] not in kinds
                    and row.get("productKey", "").startswith(row["kind"] + ":"),
                    "Duplicate or wrong source kind: " + slot)
            kinds.add(row["kind"])
            positive_id(row["sourceVersionIdentity"])
            if slot == "r3":
                require(row.get("retainedSuccessfulAudits", 0) > 200
                        and row.get("olderMissingUsageRecovered", 0) > 0
                        and all(row.get(k) == "PASSED" for k in (
                            "pageReplay", "crossProjectIsolation", "sourceAuditUnchanged")),
                        "Unproven R3 historical recovery")
            if slot == "r4":
                require(row.get("usageAndSourceUnchanged") == "PASSED"
                        and all(str(row.get("negativeCases", {}).get(k, "")).startswith(
                            ("HTTP_", "APP_DENIED_")) for k in (
                                "anonymousRejected", "missingProjectRejected")),
                        "Unproven R4 rejection")
            if slot == "roles":
                require(all(row.get(k) == "PASSED" for k in (
                    "ownerAlreadyNormalizedReplay", "controlProjectEmpty",
                    "usageAndSourceUnchanged"))
                    and all(str(row.get(k, "")).startswith(("HTTP_", "APP_DENIED_"))
                            for k in ("restrictedDenied", "anonymousDenied"))
                    and row.get("accessAttribution") == "DENIAL_OBSERVED_ROLE_CAUSE_NOT_VERIFIED"
                    and row.get("ownerActorDigest") != row.get("restrictedActorDigest"),
                    "Unproven R7 independent restricted actor")


def same_identity(first, second, keys):
    for k in keys:
        require(first.get(k) == second.get(k),
                "Same-audit recovery changed source identity: " + k)


def validate_pairs(reports):
    common = ("kind", "productKey", "sourceVersionIdentity", "auditId",
              "requestedCursor", "sourceSignature")
    if "r5Recovered" in reports:
        require("r5Blocked" in reports, "R5 recovery without actual blocked stage")
        a, b = reports["r5Blocked"]["scenario"], reports["r5Recovered"]["scenario"]
        same_identity(a, b, common + ("providerEvidenceFingerprint",))
        require(a.get("blocked", {}).get("continuationBlocked") is True
                and a.get("usageAndSourceUnchanged") == "PASSED"
                and b.get("sameCursorReplay") == "PASSED"
                and b.get("normalizedUsageCreated") == 1,
                "R5 real blocked-to-recovered facts incomplete")
    if "r6Restored" in reports:
        require("r6Outage" in reports, "R6 restore without server failure report")
        a, b = reports["r6Outage"]["scenario"], reports["r6Restored"]["scenario"]
        same_identity(a, b, common + ("evidenceRefDigest", "persistedUsageId"))
        require((a.get("serverFailure", "").startswith("HTTP_5")
                 or a.get("serverFailure") == "APP_INTERNAL_999")
                and b.get("restoredExactAuditCount") == 1
                and b.get("sameCursorReplay") == "PASSED"
                and a.get("usageAndSourceUnchanged") == "PASSED"
                and b.get("usageAndSourceUnchanged") == "PASSED",
                "R6 real failure-to-restored facts incomplete")


def correlate_artifact(artifact=None, build=None, runtime=None, base_url=None):
    if artifact is None and build is None and runtime is None:
        return {"state": "PENDING_ARTIFACT_RECEIPTS",
                "runtimeIndependentlyVerified": False}
    require(all((artifact, build, runtime)),
            "Artifact, build receipt and runtime declaration must be provided together")
    build_report, runtime_report = load(build), load(runtime)
    require(build_report.get("marker") == MARKER
            and runtime_report.get("marker") == MARKER,
            "Golden build/runtime marker missing")
    commit = build_report.get("repositoryCommit")
    require(bool(SHA40.fullmatch(str(commit)))
            and runtime_report.get("deployedCommit") == commit,
            "Build/runtime declared commit mismatch")
    digest = build_report.get("artifactSha256")
    require(bool(SHA64.fullmatch(str(digest)))
            and str(runtime_report.get("artifactSha256", "")).lower() == digest.lower(),
            "Build/runtime artifact digest mismatch")
    require(runtime_report.get("instanceBaseUrl") == base_url
            and isinstance(runtime_report.get("observationReference"), str)
            and bool(runtime_report["observationReference"].strip()),
            "Runtime declaration URL or observation reference missing")
    actual = sha256()
    with Path(artifact).open("rb") as f:
        while chunk := f.read(1024 * 1024):
            actual.update(chunk)
    require(actual.hexdigest() == digest.lower(),
            "Actual artifact bytes do not match both declarations")
    return {
        "state": "ARTIFACT_HASH_AND_DECLARATIONS_CORRELATED",
        "artifactSha256": actual.hexdigest(), "declaredCommit": commit,
        "runtimeIndependentlyVerified": False,
        "limitation": "Operator declarations cannot independently prove the running process uses these bytes",
    }


def assess(physical, consumption, reports, artifact=None, build=None, runtime=None):
    require(physical.get("marker") == MARKER and consumption.get("marker") == MARKER,
            "Only owner-marked Golden manifests may be assessed")
    project_id, control_id = positive_id(physical["projectId"]), positive_id(physical["controlProjectId"])
    require(project_id != control_id and positive_id(consumption["projectId"]) == project_id,
            "Golden manifest Project IDs mismatch")
    require(isinstance(physical.get("baseUrl"), str) and bool(physical["baseUrl"]),
            "Physical manifest has no explicit runtime URL")
    for slot, report in reports.items():
        validate_report(slot, report, project_id, control_id)
    validate_pairs(reports)
    artifact_check = correlate_artifact(artifact, build, runtime, physical["baseUrl"])
    pending = [
        "independent verification of the process deployed artifact",
        "browser login and restricted-role journeys",
        "independent membership/Asset.READ attribution",
        "operator fault and Source Reader root-cause log correlation",
    ]
    for slot in SCENES:
        if slot not in reports:
            pending.append("missing genuine real-environment scene: " + slot)
    for kind in RECOVERY:
        if not any(kind == row["kind"] for slot, report in reports.items()
                   if slot not in STAGES for row in report["scenarios"]):
            pending.append("missing scope coverage for " + kind)
    return {
        "marker": MARKER, "sceneId": "P0-GOLDEN-CONSOLIDATED-REVIEW",
        "result": "CONSISTENT_PARTIAL_EVIDENCE", "productAcceptance": "PENDING",
        "deploymentIdentity": "UNVERIFIED", "automatedProductPass": False,
        "projectId": str(project_id), "controlProjectId": str(control_id),
        "evidenceSlots": {slot: ("STRUCTURALLY_VALIDATED" if slot in reports else "PENDING")
                          for slot in SCENES},
        "artifactCorrelation": artifact_check,
        "knownPending": pending, "capturedAt": datetime.now(timezone.utc).isoformat(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assess", action="store_true")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    for slot in SCENES:
        parser.add_argument("--" + re.sub(r"([A-Z])", r"-\1", slot).lower(), type=Path)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--build-receipt", type=Path)
    parser.add_argument("--runtime-receipt", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.assess:
        print(json.dumps({"mode": "PLAN", "slots": list(SCENES),
                          "proof": "SHA256 bytes+two matching external declarations",
                          "limitations": ["not independent runtime attestation",
                                          "no automatic product PASS"]}, indent=2))
        return
    require(args.physical_manifest and args.consumption_report and args.output,
            "--assess requires manifests and output")
    paths = {slot: getattr(args, re.sub(r"([A-Z])", r"_\1", slot).lower())
             for slot in SCENES}
    require(all(args.output.resolve() != path.resolve()
                for path in paths.values() if path is not None),
            "Summary cannot overwrite original Golden reports")
    reports = {slot: load(path) for slot, path in paths.items() if path is not None}
    summary = assess(load(args.physical_manifest), load(args.consumption_report),
                     reports, args.artifact, args.build_receipt, args.runtime_receipt)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise PendingEvidence("PENDING: structural evidence consistency does not close real deployment acceptance")


if __name__ == "__main__":
    try:
        main()
    except PendingEvidence as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
    except Exception as exc:
        print("Golden evidence gate failed: " +
              (str(exc) if isinstance(exc, ValueError)
               else type(exc).__name__ + "; inspect local report files"), file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env python3
"""P0 #336 / PR 4 of 4: offline, fail-closed real-environment evidence handoff.

Never connects to a running service, executes a fault, or signs product acceptance.
A complete, hash-consistent bundle is READY_FOR_HUMAN_REVIEW, never E2E_PASS.
"""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import re
import sys

SHA40 = re.compile(r"^[a-fA-F0-9]{40}$")
SHA64 = re.compile(r"^[a-fA-F0-9]{64}$")
SCHEMA = "P0-GOLDEN-EXIT-HANDOFF-V1"
SCENES = {
    "R1_PHYSICAL": "REAL_API_DB",
    "R2_CONSUMPTION": "REAL_API_DB",
    "R3_HISTORICAL_RECOVERY": "REAL_API_DB",
    "R4_DENIAL": "REAL_API_DB",
    "R5_BLOCKED": "REAL_API_DB",
    "R5_RECOVERED": "REAL_API_DB",
    "R6_OUTAGE": "REAL_API_DB",
    "R6_RESTORED": "REAL_API_DB",
    "R7_ROLES": "REAL_API_DB",
    "R8_STRUCTURAL_GATE": "OFFLINE_STRUCTURAL_GATE",
    "PHASE4_SOURCE_USAGE": "REAL_API_DB",
    "PHASE5_METRIC": "LIVE_HTTP",
    "PROJECT_ROLE_READS": "LIVE_HTTP",
    "KEY_IP_NEGATIVES": "REAL_API_DB",
    "BROWSER_J1": "LIVE_BROWSER",
    "BROWSER_J2": "LIVE_BROWSER",
    "BROWSER_J3": "LIVE_BROWSER",
    "BROWSER_J4": "LIVE_BROWSER",
    "BROWSER_J5": "LIVE_BROWSER",
    "VERSION_CONCURRENCY": "REAL_API_DB",
    "FAILURE_RECOVERY": "REAL_API_DB",
}
SIGNERS = ("QA", "Product", "Release", "TruthOwner")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def text(value):
    return isinstance(value, str) and bool(value.strip())


def sha40(value):
    return isinstance(value, str) and bool(SHA40.fullmatch(value))


def sha64(value):
    return isinstance(value, str) and bool(SHA64.fullmatch(value))


def timestamp(value):
    require(text(value), "Evidence timestamp is missing")
    try:
        t = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("Evidence timestamp is invalid") from exc
    require(t.tzinfo is not None, "Evidence timestamp must include timezone")
    return t.astimezone(timezone.utc)


def read_json(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    require(isinstance(data, dict), "Evidence document must be a JSON object")
    return data


def digest_file(path):
    result = sha256()
    with Path(path).open("rb") as f:
        while chunk := f.read(1024 * 1024):
            result.update(chunk)
    return result.hexdigest()


def secure_path(root, relative):
    require(text(relative), "Evidence file name is missing")
    p = Path(relative)
    require(not p.is_absolute() and ".." not in p.parts,
            "Evidence files must be inside the controlled evidence directory")
    location = (root / p).resolve()
    require(location.is_relative_to(root.resolve()),
            "Evidence file escapes the controlled evidence directory")
    require(location.is_file(), "Referenced evidence file is unavailable: " + str(relative))
    return location


def verify_deployment(deployment, expected_commit, evidence_root):
    require(isinstance(deployment, dict), "Deployment receipts are missing")
    checks = []
    for name in ("backend", "frontend", "process"):
        item = deployment.get(name)
        if not isinstance(item, dict):
            checks.append(name + ":MISSING")
            continue
        require(item.get("commit") == expected_commit,
                name + " commit does not match the pinned deployment")
        require(sha64(item.get("artifactSha256")),
                name + " artifact SHA-256 is missing")
        require(text(item.get("evidenceRef")),
                name + " independent build/runtime receipt reference is missing")
        if name in ("backend", "frontend"):
            artifact = secure_path(evidence_root, item.get("artifactFile"))
            actual = digest_file(artifact)
            require(actual == item["artifactSha256"].lower(),
                    name + " packaged artifact SHA-256 does not match the actual bytes")
            checks.append(name + ":PACKAGED_HASH_VERIFIED")
        else:
            checks.append(name + ":DECLARED_MATCH")
    # Comparing three user-supplied declarations is not runtime proof.
    if all(deployment.get(n) for n in ("backend", "frontend", "process")):
        backend = deployment["backend"]["artifactSha256"].lower()
        require(deployment["process"]["artifactSha256"].lower() == backend,
                "Running backend digest declaration differs from packaged backend artifact")
        require(deployment.get("releaseVerifiedProcess") is True,
                "Release has not independently attested the observed running process")
        require(text(deployment.get("processObservationRef")),
                "Release process observation reference is missing")
        checks.append("RUNNING_PROCESS:RELEASE_ATTESTED_NOT_MACHINE_PROVEN")
    else:
        checks.append("RUNNING_PROCESS:BLOCKED")
    return checks


def validate_special(slot, data, commit, primary_project, control_project, deployed_at):
    if slot in ("R1_PHYSICAL", "R2_CONSUMPTION",
                "R3_HISTORICAL_RECOVERY", "R4_DENIAL",
                "R5_BLOCKED", "R5_RECOVERED", "R6_OUTAGE", "R6_RESTORED",
                "R7_ROLES"):
        require(data.get("marker") == "yak-golden-sample-v1"
                and data.get("repositoryCommit") == commit
                and str(data.get("projectId")) == str(primary_project),
                slot + " source report belongs to an older commit or Project")
        require(timestamp(data.get("capturedAt")) >= deployed_at,
                slot + " original source report predates this deployment")
        if slot not in ("R1_PHYSICAL", "R2_CONSUMPTION"):
            require(str(data.get("controlProjectId")) == str(control_project)
                    and data.get("productAcceptance") == "PARTIAL"
                    and data.get("deploymentCommit") is None,
                    slot + " historical source report did not preserve the R8 contract")
        return
    if slot in ("PHASE4_SOURCE_USAGE", "PHASE5_METRIC"):
        require(data.get("commit") == commit,
                slot + " live execution did not record the pinned deployed commit")

    if slot == "R8_STRUCTURAL_GATE":
        require(str(data.get("projectId")) == str(primary_project)
                and str(data.get("controlProjectId")) == str(control_project)
                and timestamp(data.get("capturedAt")) >= deployed_at,
                "R8 consolidated review must match the same Project and deployment window")
        require(data.get("result") == "CONSISTENT_PARTIAL_EVIDENCE"
                and data.get("productAcceptance") == "PENDING"
                and data.get("automatedProductPass") is False,
                "R8 report cannot claim automatic product acceptance")
        slots = data.get("evidenceSlots", {})
        require(isinstance(slots, dict)
                and all(slots.get(k) == "STRUCTURALLY_VALIDATED"
                        for k in ("r3", "r4", "r5Blocked", "r5Recovered",
                                  "r6Outage", "r6Restored", "roles")),
                "R8 source scenes are not all structurally validated")
    elif slot == "PHASE4_SOURCE_USAGE":
        require(data.get("probe") == "phase4-real-env-acceptance"
                and str(data.get("projectId")) == str(primary_project),
                "Phase4 source and Project identity do not match")
        fields = (
            "datasetRealQueryEvidence", "dataServicePublicInvokeEvidence",
            "dataServiceExternalAuthorizationBoundary",
            "dataServiceInvalidApiKeyRejected", "dataServiceUsageNormalized",
            "canonicalProductIdentityStable", "sourceNavigationResolved",
            "consumerImpactCaptured", "crossProjectChecked",
            "forbiddenDatasetConsumeChecked", "datasetSourceSuccess",
            "datasetExactAuditAndVersionLinked",
            "datasetImpactSourceAuditLinked", "dataServiceSourceSuccess",
            "dataServiceExactAuditRevisionConsumerLinked",
            "dataServiceNormalizedUsageAndImpactLinked",
        )
        assertions = data.get("assertions", {})
        require(isinstance(assertions, dict)
                and all(assertions.get(k) is True for k in fields),
                "Phase4 actual source and Usage assertions are incomplete")
        require(data.get("dataset", {}).get("golden", {}).get(
            "queryPerformance", {}).get("status") == "SUCCESS",
            "Dataset successful audit is absent")
        require(data.get("dataService", {}).get("golden", {}).get(
            "invocationRecord", {}).get("success") is True,
            "Data Service successful invocation audit is absent")
    elif slot == "PHASE5_METRIC":
        require(data.get("probe") == "phase5-real-env-acceptance"
                and str(data.get("projectId")) == str(primary_project),
                "Phase5 Metric real project and probe are inconsistent")
        checks = data.get("assertions", {})
        require(isinstance(checks, dict)
                and all(checks.get(k) is True for k in (
                    "loggedInRealUser", "exactImmutableMetricVersionResolved",
                    "activePublicationExactVersion", "publicationLedgerCaptured",
                    "referenceUsagePresent", "impactIdentityStable")),
                "Phase5 Metric exact-version, publication or impact proof is incomplete")
        require(data.get("acceptanceStatus") in ("PASSED", "INCOMPLETE"),
                "Phase5 Metric acceptance status is invalid")
    elif slot == "PROJECT_ROLE_READS":
        require(data.get("type") == "LIVE_HTTP_READ_ONLY"
                and data.get("verdict") == "PROBES_PASS_PENDING_QA"
                and data.get("deployment", {}).get("declaredCommit") == commit,
                "Project/role live-read proof or deployed commit does not match")
        probes = data.get("probes")
        require(isinstance(probes, list) and len(probes) >= 9
                and all(p.get("result") == "PASS" for p in probes),
                "Actual Project/role denial and read probes are incomplete")


def assess(manifest, root, expected_commit):
    require(isinstance(manifest, dict) and manifest.get("schema") == SCHEMA,
            "Unknown P0 exit evidence schema")
    require(sha40(expected_commit)
            and manifest.get("repositoryCommit") == expected_commit,
            "Evidence cannot be accepted from a different repository commit")
    require(text(manifest.get("environmentRef"))
            and manifest.get("environmentClass") == "AUTHORIZED_NON_PRODUCTION",
            "Authorized non-production Golden environment receipt is absent")
    project = manifest.get("projectPair")
    require(isinstance(project, dict), "Project pair is absent")
    a, b = str(project.get("primary", "")), str(project.get("control", ""))
    require(re.fullmatch(r"[1-9]\d*", a) is not None
            and re.fullmatch(r"[1-9]\d*", b) is not None and a != b,
            "Two distinct Project IDs are required")
    deployed_at = timestamp(manifest.get("deployedAt"))
    deployment = manifest.get("deployment") or {}
    deployment_checks = verify_deployment(deployment, expected_commit, root)
    docs = manifest.get("evidence", {})
    require(isinstance(docs, dict), "Evidence index must be a map")
    require(all(k in SCENES for k in docs), "Unknown Golden evidence slot")
    statuses = {}
    for slot, kind in SCENES.items():
        entry = docs.get(slot)
        if entry is None:
            statuses[slot] = "BLOCKED_MISSING_EVIDENCE"
            continue
        require(isinstance(entry, dict), slot + " receipt must be an object")
        require(entry.get("evidenceKind") == kind,
                slot + " cannot substitute mock/offline evidence for a live scenario")
        require(entry.get("deploymentCommit") == expected_commit,
                slot + " belongs to another deployment commit")
        require(str(entry.get("projectId")) == a
                and str(entry.get("controlProjectId")) == b,
                slot + " Project pair differs from the deployment")
        require(text(entry.get("reviewerRef")), slot + " lacks a QA review reference")
        observed_at = timestamp(entry.get("observedAt"))
        require(observed_at >= deployed_at,
                slot + " evidence predates the reported deployment")
        require(sha64(entry.get("sha256")), slot + " SHA-256 is absent")
        file = secure_path(root, entry.get("file"))
        actual = digest_file(file)
        require(actual == entry["sha256"].lower(),
                slot + " persisted evidence SHA-256 mismatch")
        # Inspect original source metadata as well as the manifest digest. In particular
        # 2026-10-04 source receipts cannot be re-labelled as current deployments.
        validate_special(slot, read_json(file), expected_commit, a, b, deployed_at)
        statuses[slot] = "HASH_VERIFIED_UNDER_HUMAN_REVIEW"
    signoffs = manifest.get("signoffs") or {}
    signoff_status = {
        role: ("DECLARED_APPROVED_REQUIRES_INDEPENDENT_CONFIRMATION"
               if isinstance(signoffs.get(role), dict)
               and signoffs[role].get("decision") == "APPROVED"
               and text(signoffs[role].get("reviewer"))
               and text(signoffs[role].get("approvalRef"))
               else "BLOCKED_MISSING_SIGNOFF")
        for role in SIGNERS
    }
    blockers = [slot for slot, state in statuses.items()
                if state.startswith("BLOCKED")]
    blockers += [role + "_SIGNOFF" for role, state in signoff_status.items()
                 if state.startswith("BLOCKED")]
    if any(check.endswith(":MISSING") or check.endswith(":BLOCKED")
           for check in deployment_checks):
        blockers.append("DEPLOYMENT_ATTESTATION")
    return {
        "schema": SCHEMA,
        "repositoryCommit": expected_commit,
        "result": "READY_FOR_HUMAN_REVIEW" if not blockers else "BLOCKED_EVIDENCE",
        "automatedProductPass": False,
        "p0ExitSigned": False,
        "scope": "OFFLINE_HASH_AND_DECLARATION_VALIDATION_ONLY",
        "evidenceSlots": statuses,
        "deploymentChecks": deployment_checks,
        "signoffs": signoff_status,
        "blockers": blockers,
        "limits": ("Independent process identity, role provenance, browser authenticity "
                   "and reviewer signatures cannot be proven by local JSON alone."),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--evidence-root", type=Path)
    parser.add_argument("--expected-commit")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    if not args.manifest:
        print(json.dumps({"schema": SCHEMA, "result": "BLOCKED_EVIDENCE",
                          "requiredSlots": list(SCENES),
                          "automaticProductPass": False}, indent=2))
        return 2
    require(args.evidence_root and args.expected_commit and args.output,
            "Manifest review needs --evidence-root, --expected-commit and --output")
    require(args.output.resolve() != args.manifest.resolve(),
            "Exit report must never overwrite its input manifest")
    result = assess(read_json(args.manifest), args.evidence_root.resolve(),
                    args.expected_commit)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    # Deliberate non-zero to prohibit CI from mistaking even complete declarations
    # for a human-signed Golden E2E success.
    return 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print("P0 evidence handoff rejected: " + str(exc), file=sys.stderr)
        sys.exit(1)

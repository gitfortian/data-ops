#!/usr/bin/env python3
"""Real scoped HTTP negative-path acceptance for exact-version recovery.

Only runs against explicitly owned Golden Sample projects. Never seeds, modifies
or deletes raw audit or Usage. Every probe must leave application DB evidence
unchanged; a valid control-Project POST has an empty source window by design.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys

from bootstrap import Api, MARKER, PROJECT_NAME, current_commit, required_env
from historical_recovery import (
    PendingEvidence, RECOVERY, connect_readonly, positive_id, read_source,
    require, validate_page, verify_project,
)

SNAPSHOT_MAX_ROWS = 10_000
INVALID_CURSORS = ("0", "01", "-1", "1.0", "9223372036854775808")


def usage_snapshot(db, project_id, control_project_id):
    """Capture all Usage in both owned projects, including other versions/products."""
    with db.cursor() as cursor:
        cursor.execute("""
            SELECT id, project_id, product_key, source_version_identity,
                   source_identity, consumption_mode, outcome,
                   provider, provider_evidence_ref, deduplication_id, normalized_at
              FROM yak_ops_consumption_usage_evidence
             WHERE project_id IN (%s, %s)
             ORDER BY id DESC LIMIT %s
        """, (project_id, control_project_id, SNAPSHOT_MAX_ROWS + 1))
        rows = list(cursor.fetchall())
    if len(rows) > SNAPSHOT_MAX_ROWS:
        raise PendingEvidence("PENDING: owned projects exceed the bounded Usage snapshot budget")
    return rows


def source_snapshot(db, kind, project_id, source_id, version_id):
    """Capture the exact retained source window with an explicit upper bound."""
    rows = read_source(
        db, kind, project_id, source_id, version_id, SNAPSHOT_MAX_ROWS + 1)
    if len(rows) > SNAPSHOT_MAX_ROWS:
        raise PendingEvidence("PENDING: retained source audit exceeds snapshot budget")
    return rows


def assert_denied(response, scenario):
    """A failed request must not be a successful 200 envelope or a 5xx outage."""
    status = response.status_code
    require(isinstance(status, int), f"{scenario}: missing HTTP status")
    if status == 200:
        try:
            body = response.json()
        except (ValueError, TypeError):
            raise ValueError(f"{scenario}: HTTP 200 has no structured failure envelope") from None
        require(isinstance(body, dict) and body.get("code") is not None
                and str(body.get("code")) != "200",
                f"{scenario}: recovery unexpectedly succeeded")
        try:
            code = int(body["code"])
        except (ValueError, TypeError):
            raise ValueError(f"{scenario}: nonnumeric application failure code") from None
        require(code < 50000 and not 500 <= code < 600,
                f"{scenario}: application internal error cannot count as denial")
        return f"APP_DENIED_{code}"
    require(status in (400, 401, 403, 404, 405, 422),
            f"{scenario}: HTTP status is not an expected 4xx denial")
    return f"HTTP_{status}"


def request_raw(transport, method, base_url, path, params, project_id=None):
    headers = {}
    if project_id is not None:
        headers["X-YAK-SECURITY-PROJECT-ID"] = str(project_id)
    return transport.request(method, base_url.rstrip("/") + path, params=params,
                             headers=headers, timeout=30, allow_redirects=False)


def assert_unchanged(db, project_id, control_id, before_usage, source_states):
    require(usage_snapshot(db, project_id, control_id) == before_usage,
            "Rejected/foreign request changed persisted Usage in an owned Project")
    for (kind, p, source_id, version_id), rows in source_states.items():
        require(source_snapshot(db, kind, p, source_id, version_id) == rows,
                "Rejected/foreign request changed the source-owned audit")


def verify_negative_kind(api, db, kind, sample, project_id, control_id,
                         anonymous_transport, restricted_api=None):
    cfg = RECOVERY[kind]
    source_id = positive_id(sample["id"])
    version_id = positive_id(sample[cfg["version"]])
    product_key = f"{kind}:{source_id}"
    source_states = {
        (kind, project_id, source_id, version_id):
            source_snapshot(db, kind, project_id, source_id, version_id),
        (kind, control_id, source_id, version_id):
            source_snapshot(db, kind, control_id, source_id, version_id),
    }
    if not source_states[(kind, project_id, source_id, version_id)]:
        raise PendingEvidence("PENDING: owned sample has no retained successful exact-version audit")
    if source_states[(kind, control_id, source_id, version_id)]:
        raise PendingEvidence("PENDING: control Project has same source audit identity")
    before_usage = usage_snapshot(db, project_id, control_id)
    params = {"productKey": product_key, "sourceVersionIdentity": str(version_id), "limit": 200}
    states = {}
    base = api.base_url

    # Prove the endpoint is deployed and the control Project has no matching source.
    original_project = api.project_id
    try:
        api.project_id = str(control_id)
        foreign = api.request("POST", cfg["path"], params=params)
        validate_page(kind, foreign, product_key, version_id, None, [], 200)
        states["controlProjectNoSource"] = "PASSED"
    finally:
        api.project_id = original_project
    assert_unchanged(db, project_id, control_id, before_usage, source_states)

    # Anonymous public-plane clients cannot trigger the authenticated recovery command.
    states["anonymousRejected"] = assert_denied(
        request_raw(anonymous_transport, "POST", base, cfg["path"], params, project_id),
        "anonymous recovery")
    assert_unchanged(db, project_id, control_id, before_usage, source_states)

    # Logged-in callers MUST still supply trusted Project context.
    states["missingProjectRejected"] = assert_denied(
        request_raw(api.session, "POST", base, cfg["path"], params),
        "missing Project context")
    assert_unchanged(db, project_id, control_id, before_usage, source_states)

    # GET must never normalize Usage; only explicit POST is a recovery command.
    states["getDoesNotRecover"] = assert_denied(
        request_raw(api.session, "GET", base, cfg["path"], params, project_id),
        "GET recovery")
    assert_unchanged(db, project_id, control_id, before_usage, source_states)

    # Wrong source type, invalid version, invalid cursor MUST fail before source normalization.
    wrong = "DATA_SERVICE" if kind == "DATASET" else "DATASET"
    for tag, changed in (
        ("wrongProductType", {"productKey": f"{wrong}:{source_id}"}),
        ("noncanonicalRevision", {"sourceVersionIdentity": "01"}),
    ):
        states[tag] = assert_denied(
            request_raw(api.session, "POST", base, cfg["path"],
                        {**params, **changed}, project_id), tag)
        assert_unchanged(db, project_id, control_id, before_usage, source_states)

    for cursor in INVALID_CURSORS:
        tag = "invalidCursor_" + cursor
        states[tag] = assert_denied(
            request_raw(api.session, "POST", base, cfg["path"],
                        {**params, cfg["cursor"]: cursor}, project_id), tag)
        assert_unchanged(db, project_id, control_id, before_usage, source_states)

    if restricted_api is None:
        states["restrictedIdentity"] = "PENDING_NO_RESTRICTED_TEST_ACCOUNT"
    else:
        states["restrictedIdentity"] = assert_denied(
            request_raw(restricted_api.session, "POST", base, cfg["path"], params, project_id),
            "restricted test identity")
        assert_unchanged(db, project_id, control_id, before_usage, source_states)
    return {"kind": kind, "productKey": product_key,
            "sourceVersionIdentity": str(version_id),
            "negativeCases": states, "usageAndSourceUnchanged": "PASSED",
            "evidence": "REAL_HTTP_AND_READ_ONLY_APPLICATION_DB"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    parser.add_argument("--kind", choices=["BOTH", "DATASET", "DATA_SERVICE"], default="BOTH")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({"mode": "PLAN", "checks": [
            "anonymous and missing-Project denial",
            "GET never initiates recovery",
            "wrong product, malformed revision and cursor denial",
            "empty cross-Project read with no Usage writes",
            "optional restricted-account denial",
            "source audit and both project Usage snapshots remain identical"],
            "required": ["isolated R1/R2 Golden projects", "retained SUCCESS audit (>=1)",
                         "SELECT-only application MySQL credentials",
                         "authorized console test account"],
            "notExecuted": ["HTTP", "DB", "source invocation or normalization"]}, indent=2))
        return
    require(args.physical_manifest and args.consumption_report and args.output,
            "--apply requires both manifests and a local output path")
    physical = json.loads(args.physical_manifest.read_text(encoding="utf-8"))
    consumption = json.loads(args.consumption_report.read_text(encoding="utf-8"))
    require(physical.get("marker") == MARKER and consumption.get("marker") == MARKER,
            "Only dedicated Golden Sample manifests are accepted")
    project_id = positive_id(physical["projectId"])
    control_id = positive_id(physical["controlProjectId"])
    require(project_id != control_id and positive_id(consumption["projectId"]) == project_id,
            "Golden and control Project identities must match the owned manifests")
    api = Api(os.environ.get("YAK_OPS_BASE_URL", physical["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    verify_project(api, project_id, PROJECT_NAME)
    verify_project(api, control_id, PROJECT_NAME + " 对照")
    api.project_id = str(project_id)

    restricted_name = os.environ.get("YAK_GOLDEN_RESTRICTED_USERNAME")
    restricted_password = os.environ.get("YAK_GOLDEN_RESTRICTED_PASSWORD")
    require(bool(restricted_name) == bool(restricted_password),
            "Restricted test credentials must be provided as a pair")
    restricted_api = None
    if restricted_name:
        restricted_api = Api(api.base_url, restricted_name, restricted_password)
        restricted_api.project_id = str(project_id)

    import requests
    kinds = ["DATASET", "DATA_SERVICE"] if args.kind == "BOTH" else [args.kind]
    with connect_readonly() as db:
        scenarios = [
            verify_negative_kind(
                api, db, kind, consumption["samples"][RECOVERY[kind]["sample"]],
                project_id, control_id, requests, restricted_api)
            for kind in kinds
        ]
    restricted_pending = any(
        scene["negativeCases"]["restrictedIdentity"].startswith("PENDING") for scene in scenarios)
    report = {
        "marker": MARKER, "sceneId": "J3-RECOVERY-NEGATIVE-HTTP-DB",
        "result": "REAL_HTTP_DB_NEGATIVE_PATHS_VERIFIED",
        "productAcceptance": "PARTIAL", "repositoryCommit": current_commit(),
        "deploymentCommit": None, "deploymentIdentity": "UNVERIFIED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "scenarios": scenarios,
        "remaining": (["restricted-role denial requires isolated restricted identity"]
                      if restricted_pending else [])
                     + ["real >200 retained source history (separate R3)",
                        "provider storage fault and GAP retry (separate fixture)",
                        "verified deployed artifact identity", "browser/UI role matrix"],
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
        print("Golden historical recovery denials failed: "
              + (str(error) if isinstance(error, ValueError)
                 else type(error).__name__ + "; inspect protected application logs"),
              file=sys.stderr)
        sys.exit(1)

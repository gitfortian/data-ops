#!/usr/bin/env python3
"""Explicit real-environment validation of retained 200+ Dataset/Data Service audit recovery.

This runner never creates/deletes source audits or directly writes normalized Usage.
It requires already-generated synthetic successful source audits and a SELECT-only
application DB credential. The sole business mutation is the opt-in recovery POST.
"""
import argparse
import json
import os
from pathlib import Path
import sys
from datetime import datetime, timezone

from bootstrap import Api, MARKER, PROJECT_NAME, required_env, current_commit

PAGE_LIMIT = 200
MAX_PAGES = 50
RECOVERY = {
    "DATASET": {
        "path": "/api/v1/consumption/impact/dataset-version-recovery",
        "cursor": "beforeAuditId",
        "next": "nextBeforeAuditId",
        "requested": "requestedBeforeAuditId",
        "provider": "DATASET_QUERY_PERFORMANCE",
        "mode": "QUERY",
        "sample": "dataset",
        "version": "versionId",
        "source_sql": """
            SELECT id, query_id, subject_type, subject_source_domain, subject_source_identity
              FROM yak_dataset_query_performance
             WHERE project_id=%s AND dataset_id=%s AND dataset_version_id=%s AND status='SUCCESS'
             ORDER BY id DESC LIMIT %s
        """,
    },
    "DATA_SERVICE": {
        "path": "/api/v1/consumption/impact/data-service-revision-recovery",
        "cursor": "beforeInvocationId",
        "next": "nextBeforeInvocationId",
        "requested": "requestedBeforeInvocationId",
        "provider": "DATA_SERVICE_INVOCATION",
        "mode": "API_INVOKE",
        "sample": "dataService",
        "version": "revisionId",
        "source_sql": """
            SELECT id, consumer_id
              FROM yak_ops_data_service_call_log
             WHERE project_id=%s AND api_id=%s AND source_revision_id=%s AND success=1
             ORDER BY id DESC LIMIT %s
        """,
    },
}


class PendingEvidence(ValueError):
    """Fixture or deployment provenance is insufficient for a real E2E claim."""


def require(condition, message):
    if not condition:
        raise ValueError(message)


def positive_id(raw):
    value = str(raw)
    require(value.isascii() and value.isdecimal() and value[0] != "0"
            and str(int(value)) == value and 0 < int(value) <= 9223372036854775807,
            "Expected canonical positive signed BIGINT identity")
    return int(value)


def transport_cursor(kind, value):
    if value is None:
        return None
    value = positive_id(value)
    # Both recovery HTTP contracts transport signed BIGINT audit IDs as strings.
    return str(value)


def read_source(db, kind, project_id, source_id, version_id, limit):
    with db.cursor() as cursor:
        cursor.execute(RECOVERY[kind]["source_sql"],
                       (project_id, source_id, version_id, limit))
        return list(cursor.fetchall())


def read_usage(db, kind, project_id, product_key, version_id, limit):
    with db.cursor() as cursor:
        cursor.execute("""
            SELECT id, provider_evidence_ref, source_identity, consumption_mode,
                   outcome, deduplication_id
              FROM yak_ops_consumption_usage_evidence
             WHERE project_id=%s AND product_key=%s
               AND source_version_identity=%s AND provider=%s
             ORDER BY id DESC LIMIT %s
        """, (project_id, product_key, str(version_id), RECOVERY[kind]["provider"], limit))
        return list(cursor.fetchall())


def source_refs(kind, source):
    refs = {}
    previous = None
    for row in source:
        audit_id = positive_id(row["id"])
        require(previous is None or audit_id < previous,
                "Source page IDs are not in strictly descending durable audit order")
        previous = audit_id
        if kind == "DATASET":
            query_id = row.get("query_id")
            require(isinstance(query_id, str) and query_id.strip()
                    and row.get("subject_type") == "USER"
                    and row.get("subject_source_domain")
                    and row.get("subject_source_identity"),
                    "Historical Dataset SUCCESS audit is not attributable to a USER")
            ref = "query:" + query_id
            identity = str(row["subject_source_identity"])
        else:
            require(row.get("consumer_id") is not None
                    and positive_id(row["consumer_id"]) > 0,
                    "Historical Data Service SUCCESS audit has no managed Consumer")
            ref = "invocation:" + str(audit_id)
            identity = str(row["consumer_id"])
        require(ref not in refs, "Source SUCCESS audit has duplicate provider evidence identity")
        refs[ref] = identity
    return refs


def indexed_usage(rows, expected, mode):
    found = {}
    dedup = set()
    for row in rows:
        ref = row["provider_evidence_ref"]
        if ref not in expected:
            continue  # Prior evidence for source audits already removed by retention.
        require(ref not in found, "A source success audit produced duplicate normalized Usage")
        require(row["source_identity"] == expected[ref]
                and row["consumption_mode"] == mode and row["outcome"] == "SUCCESS",
                "Normalized Usage disagrees with the source-owned Consumer / mode / success")
        require(row["deduplication_id"] not in dedup,
                "Normalized Usage has duplicate deduplication identity")
        dedup.add(row["deduplication_id"])
        found[ref] = row["id"]
    return found


def preflight(kind, source, before_rows, page_size=PAGE_LIMIT):
    if len(source) <= page_size:
        raise PendingEvidence("PENDING: retained exact-version success audits do not exceed one page")
    expected = source_refs(kind, source)
    before = indexed_usage(before_rows, expected, RECOVERY[kind]["mode"])
    old_refs = list(expected)[page_size:]
    missing_old = [ref for ref in old_refs if ref not in before]
    if not missing_old:
        raise PendingEvidence(
            "PENDING: no unnormalized historical SUCCESS evidence beyond the first 200 audits")
    return expected, before, missing_old


def validate_page(kind, response, product_key, version_id, before_id, source_slice, limit):
    cfg = RECOVERY[kind]
    require(isinstance(response, dict), "Recovery endpoint returned no structured page")
    require(response.get("productKey") == product_key
            and str(response.get("sourceVersionIdentity")) == str(version_id),
            "Recovery endpoint changed stable Product / immutable Revision")
    require(response.get("requestedLimit") == limit
            and response.get("visitedAuditCount") == len(source_slice),
            "Recovery page audit count differs from the real source DB window")
    require(response.get("normalizedOrAlreadyPresentCount") == len(source_slice)
            and response.get("normalizationGapCount") == 0
            and response.get("normalizationUnavailableCount") == 0
            and response.get("retryRequired") is False,
            "Recovery page contains a GAP, unavailable or unnormalized success")
    require(response.get(cfg["requested"]) == transport_cursor(kind, before_id),
            "Recovery response lost or changed the exclusive request cursor")
    full = len(source_slice) == limit
    expected_next = transport_cursor(kind, source_slice[-1]["id"]) if full else None
    require(response.get(cfg["next"]) == expected_next,
            "Recovery next cursor does not match the oldest persisted ID visited")
    require(response.get("retainedAuditExhausted") is (not full),
            "Recovery exhaustion flag does not match the retained audit window")


def recover_pages(api, kind, product_key, version_id, source, limit=PAGE_LIMIT, max_pages=MAX_PAGES):
    cfg = RECOVERY[kind]
    before = None
    pages = 0
    visited = 0
    # A full final page needs one additional empty request to prove retained exhaustion.
    while True:
        require(pages <= max_pages, "Recovery exceeded explicitly bounded maximum page count")
        expected = source[visited:visited + limit]
        params = {"productKey": product_key, "sourceVersionIdentity": str(version_id),
                  "limit": limit}
        if before is not None:
            params[cfg["cursor"]] = transport_cursor(kind, before)
        response = api.request("POST", cfg["path"], params=params)
        validate_page(kind, response, product_key, version_id, before, expected, limit)
        pages += 1
        visited += len(expected)
        if len(expected) < limit:
            break
        before = positive_id(expected[-1]["id"])
    require(visited == len(source), "Recovery skipped or added persisted source audit rows")
    return {"pages": pages, "visited": visited}


def verify_project(api, project_id, project_name):
    project = api.request("GET", f"/yak-security/api/v1/project/{project_id}",
                          scoped=False)
    require(isinstance(project, dict) and project.get("projectName") == project_name
            and project.get("description") == MARKER,
            "Recovery project is not an owned isolated Golden Sample Project")


def verify_kind(api, db, kind, sample, project_id, control_project_id, max_pages):
    cfg = RECOVERY[kind]
    source_id = positive_id(sample["id"])
    version_id = positive_id(sample[cfg["version"]])
    product_key = f"{kind}:{source_id}"
    cap = PAGE_LIMIT * max_pages
    source = read_source(db, kind, project_id, source_id, version_id, cap + 1)
    if len(source) > cap:
        raise PendingEvidence("PENDING: retained source audit exceeds the explicit bounded page budget")
    if read_source(db, kind, control_project_id, source_id, version_id, 1):
        raise PendingEvidence("PENDING: control Project contains colliding source audit")
    original = read_usage(db, kind, project_id, product_key, version_id, cap + 1)
    control_before = read_usage(db, kind, control_project_id, product_key, version_id, cap + 1)
    require(not control_before, "Control Project has unexpected matching normalized evidence")
    expected, before, missing_old = preflight(kind, source, original)
    require(len(original) <= cap, "Normalized Usage evidence exceeds snapshot budget")

    outcome = recover_pages(api, kind, product_key, version_id, source, max_pages=max_pages)
    after_rows = read_usage(db, kind, project_id, product_key, version_id, cap + 1)
    after = indexed_usage(after_rows, expected, cfg["mode"])
    require(len(after) == len(expected)
            and all(ref in after for ref in missing_old),
            "Recovery did not persist every retained source SUCCESS including older missing Usage")
    require(all(ref in after and before[ref] == after[ref] for ref in before),
            "Recovery rewrote or removed existing idempotent Usage identity")
    require(read_source(db, kind, project_id, source_id, version_id, cap + 1) == source,
            "Source audit changed during recovery; result cannot prove stable retained coverage")

    # Replay page one and require exactly the same persisted normalized row identities.
    params = {"productKey": product_key, "sourceVersionIdentity": str(version_id),
              "limit": PAGE_LIMIT}
    repeat = api.request("POST", cfg["path"], params=params)
    validate_page(kind, repeat, product_key, version_id, None, source[:PAGE_LIMIT], PAGE_LIMIT)
    replay = indexed_usage(
        read_usage(db, kind, project_id, product_key, version_id, cap + 1),
        expected, cfg["mode"])
    require(replay == after, "Replaying a successful page added/changed normalized Usage")

    # The other authorized Project may read, but must have no source-owned audit to project.
    owning_project = api.project_id
    try:
        api.project_id = str(control_project_id)
        foreign = api.request("POST", cfg["path"], params=params)
        validate_page(kind, foreign, product_key, version_id, None, [], PAGE_LIMIT)
    finally:
        api.project_id = owning_project
    require(read_usage(db, kind, control_project_id, product_key, version_id, cap + 1)
            == control_before, "Recovery leaked normalized Usage into another Project")

    return {"kind": kind, "productKey": product_key, "sourceVersionIdentity": str(version_id),
            "retainedSuccessfulAudits": len(source),
            "olderMissingUsageRecovered": len(missing_old),
            "normalizedBefore": len(before), "normalizedAfter": len(after),
            "recoveryPages": outcome["pages"], "pageReplay": "PASSED",
            "crossProjectIsolation": "PASSED", "sourceAuditUnchanged": "PASSED",
            "evidence": "REAL_READONLY_DB_AND_SCOPED_POST"}


def connect_readonly():
    import pymysql
    # Use a SELECT-only MySQL account. No migration, INSERT, UPDATE or DELETE is executed here.
    return pymysql.connect(
        host=required_env("YAK_GOLDEN_APP_MYSQL_HOST"),
        port=int(os.environ.get("YAK_GOLDEN_APP_MYSQL_PORT", "3306")),
        user=required_env("YAK_GOLDEN_APP_MYSQL_USERNAME"),
        password=required_env("YAK_GOLDEN_APP_MYSQL_PASSWORD"),
        database=required_env("YAK_GOLDEN_APP_MYSQL_DATABASE"),
        charset="utf8mb4", connect_timeout=10, read_timeout=30,
        cursorclass=pymysql.cursors.DictCursor, autocommit=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true",
                        help="Run real recovery POST and read-only source/Usage DB evidence")
    parser.add_argument("--physical-manifest", type=Path)
    parser.add_argument("--consumption-report", type=Path)
    parser.add_argument("--kind", choices=["BOTH", "DATASET", "DATA_SERVICE"], default="BOTH")
    parser.add_argument("--max-pages", type=int, default=10)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({"mode": "PLAN", "requires": [
            "owned Golden Project and an empty owned control Project",
            "real retained source SUCCESS audit rows >200 per requested immutable version",
            "at least one missing normalized Usage beyond the first 200 source rows",
            "SELECT-only application MySQL account and real authenticated app"],
            "writes": ["bounded idempotent recovery POST only"],
            "notExecuted": ["real API", "source SQL", "normalized Usage SQL"]}, indent=2))
        return
    require(args.physical_manifest and args.consumption_report and args.output,
            "--apply requires both manifests and a local output path")
    require(1 <= args.max_pages <= MAX_PAGES, "--max-pages must be between 1 and 50")
    physical = json.loads(args.physical_manifest.read_text(encoding="utf-8"))
    consumption = json.loads(args.consumption_report.read_text(encoding="utf-8"))
    require(physical.get("marker") == MARKER and consumption.get("marker") == MARKER,
            "Only dedicated Golden Sample manifests are accepted")
    project_id = positive_id(physical["projectId"])
    control_id = positive_id(physical["controlProjectId"])
    require(project_id != control_id and positive_id(consumption["projectId"]) == project_id,
            "Cross-project manifest mismatch or missing isolated control Project")
    api = Api(os.environ.get("YAK_OPS_BASE_URL", physical["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    verify_project(api, project_id, PROJECT_NAME)
    verify_project(api, control_id, PROJECT_NAME + " 对照")
    api.project_id = str(project_id)
    kinds = ["DATASET", "DATA_SERVICE"] if args.kind == "BOTH" else [args.kind]
    with connect_readonly() as db:
        verified = [verify_kind(api, db, kind, consumption["samples"][RECOVERY[kind]["sample"]],
                                project_id, control_id, args.max_pages) for kind in kinds]
    report = {
        "marker": MARKER, "sceneId": "J3-EXACT-REVISION-RETAINED-OVER-200",
        "result": "REAL_API_DB_RECOVERY_VERIFIED", "productAcceptance": "PARTIAL",
        "repositoryCommit": current_commit(),
        "deploymentCommit": None, "deploymentIdentity": "UNVERIFIED",
        "projectId": str(project_id), "controlProjectId": str(control_id),
        "capturedAt": datetime.now(timezone.utc).isoformat(), "scenarios": verified,
        "remaining": ["verified deployed artifact identity", "restricted-role/browser matrix",
                      "raw audit removed by retention", "all-time Usage totals"],
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
        print("Golden historical recovery failed: "
              + (str(error) if isinstance(error, ValueError)
                 else type(error).__name__ + "; inspect protected application logs"),
              file=sys.stderr)
        sys.exit(1)

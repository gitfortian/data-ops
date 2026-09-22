#!/usr/bin/env bash

# LEGACY EVIDENCE CHECK
#
# 该脚本只用于检查历史 docs/v1/01 快照与当前 Maven 模块清单是否发生漂移。
# docs/v1 已被 DOCUMENT_GOVERNANCE 定义为 Evidence，不再拥有 Product Truth。
#
# 本脚本：
#   - 不属于 Product Guard；
#   - 不应作为新增/删除产品能力的审批依据；
#   - 不应为了让脚本通过而反向修改当前 Product Truth。
#
# 用法：scripts/backbone/check-backbone-modules.sh

set -Eeuo pipefail

printf 'LEGACY: docs/v1/01 is historical evidence, not current Product Truth.\n' >&2

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BUSINESS_POM="yak-ops-business/pom.xml"
BACKBONE_DOC="docs/v1/01-产品主心骨.md"

if [[ ! -f "$BUSINESS_POM" || ! -f "$BACKBONE_DOC" ]]; then
    printf 'SKIP: legacy snapshot input missing.\n'
    exit 0
fi

code_modules="$(
    grep -o '<module>[^<]*</module>' "$BUSINESS_POM"         | sed 's/<[^>]*>//g; s/yak-ops-business-//'         | awk '{ if ($0 == "sync") { print "sync-offline"; print "sync-realtime" } else print }'         | sort -u
)"

doc_modules="$(
    sed -n '/^## 二、/,/^## 三、/p' "$BACKBONE_DOC"         | grep '^| '         | awk -F'|' 'NF > 2 { gsub(/^[ \t]+|[ \t]+$/, "", $2); print $2 }'         | awk '{ for (i = NF; i >= 1; i--) if ($i ~ /^[a-z][a-z0-9-]*$/) { print $i; break } }'         | sort -u
)"

missing_in_doc="$(comm -23 <(printf '%s\n' "$code_modules") <(printf '%s\n' "$doc_modules") || true)"
missing_in_code="$(comm -13 <(printf '%s\n' "$code_modules") <(printf '%s\n' "$doc_modules") || true)"

if [[ -z "$missing_in_doc" && -z "$missing_in_code" ]]; then
    printf 'PASS: legacy docs/v1/01 snapshot still matches current module names.\n'
    exit 0
fi

printf 'INFO: legacy docs/v1/01 has drifted from current engineering modules.\n' >&2
[[ -n "$missing_in_doc" ]] && printf '  code only: %s\n' $missing_in_doc >&2
[[ -n "$missing_in_code" ]] && printf '  legacy doc only: %s\n' $missing_in_code >&2
printf 'Do not update Product Truth merely to satisfy this legacy evidence check.\n' >&2
exit 0

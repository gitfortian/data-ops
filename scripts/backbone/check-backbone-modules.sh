#!/usr/bin/env bash

# 主心骨 CI 双向校验（docs/v1/01-产品主心骨.md 第 0 步）：
#   代码里的业务模块清单  ↔  01 文档第二节的模块表，双向必须一致。
#
# 背景：v0.3 曾因人工盘点把模块数写成三种口径（27/28/26+agent），
# 本脚本就是拦住这类"文档腐烂"的守门人。任何一侧多/少模块即失败。
#
# 用法：scripts/backbone/check-backbone-modules.sh
# 退出码：0 = 一致；1 = 不一致（stderr 输出双向差异清单）。

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BUSINESS_POM="yak-ops-business/pom.xml"
BACKBONE_DOC="docs/v1/01-产品主心骨.md"

if [[ ! -f "$BUSINESS_POM" ]]; then
    printf 'missing %s\n' "$BUSINESS_POM" >&2
    exit 1
fi
if [[ ! -f "$BACKBONE_DOC" ]]; then
    printf 'missing %s\n' "$BACKBONE_DOC" >&2
    exit 1
fi

# ── 代码侧：pom <module> 清单 ──────────────────────────────────────────
# yak-ops-business-sync 是父模块（含 offline/realtime 两个构建单元），
# 按 01 文档第二节的口径展开，否则两侧永远差一条。
code_modules="$(
    grep -o '<module>[^<]*</module>' "$BUSINESS_POM" \
        | sed 's/<[^>]*>//g; s/yak-ops-business-//' \
        | awk '{ if ($0 == "sync") { print "sync-offline"; print "sync-realtime" } else print }' \
        | sort -u
)"

# ── 文档侧：01 第二节模块表的第一列英文标识 ───────────────────────────
# 表格行形如 "| 数据源 datasource | 管所有外部数据库的连接… |"，
# 取第一列最后一个是英文的 token 作为模块标识。
doc_modules="$(
    sed -n '/^## 二、/,/^## 三、/p' "$BACKBONE_DOC" \
        | grep '^| ' \
        | awk -F'|' 'NF > 2 { gsub(/^[ \t]+|[ \t]+$/, "", $2); print $2 }' \
        | awk '{ for (i = NF; i >= 1; i--) if ($i ~ /^[a-z][a-z0-9-]*$/) { print $i; break } }' \
        | sort -u
)"

code_count="$(printf '%s\n' "$code_modules" | wc -l | tr -d ' ')"
doc_count="$(printf '%s\n' "$doc_modules" | wc -l | tr -d ' ')"

printf '代码侧模块数（sync 展开后）: %s\n' "$code_count"
printf '文档侧模块数: %s\n' "$doc_count"

missing_in_doc="$(comm -23 <(printf '%s\n' "$code_modules") <(printf '%s\n' "$doc_modules") || true)"
missing_in_code="$(comm -13 <(printf '%s\n' "$code_modules") <(printf '%s\n' "$doc_modules") || true)"

status=0

if [[ -n "$missing_in_doc" ]]; then
    printf '\n以下模块在代码中但不在 01 文档第二节（文档漏记）:\n' >&2
    printf '  %s\n' $missing_in_doc >&2
    status=1
fi

if [[ -n "$missing_in_code" ]]; then
    printf '\n以下模块在 01 文档中但不在代码里（文档多记/已删模块）:\n' >&2
    printf '  %s\n' $missing_in_code >&2
    status=1
fi

if [[ "$status" -eq 0 ]]; then
    printf 'PASS: 模块清单双向一致\n'
else
    printf 'FAIL: 模块清单不一致，请同步 docs/v1/01-产品主心骨.md 第二节或 pom\n' >&2
fi

exit "$status"

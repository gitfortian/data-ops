#!/usr/bin/env python3
"""把每个业务模块的 Flyway 版本化迁移合并为单文件 baseline。

背景
----
仓库里每个模块有自己独立的 Flyway bean、location 与历史表，所以同一版本号可以
在不同模块各自出现。本次把「一个模块多个 V* 文件」收敛成「一个模块一个
V1__<module>_baseline.sql」，Java 装配不动。

合并规则（与仓库既有 B2044 的先例一致，但生成器随代码提交，可复查）
----
1. 按数值版本号升序拼接该模块所有 V* 文件原文，块间插入 `-- Source:` 注释。
2. 裸 `CREATE TABLE x` 统一补成 `CREATE TABLE IF NOT EXISTS x`，让单文件可重复执行，
   也吸收同组内跨文件重复建表的情况。已验证每个 location 内没有同一张表被
   两个 V 文件创建，因此这一步是防御性的，不会掩盖列定义丢失。
3. 跳过所有 `B*__*baseline.sql`。它们从未被执行：29 个 Flyway bean 都设了
   `baselineOnMigrate(true)` + `baselineVersion("0")`，baseline 迁移只会被解析为
   IGNORED / BASELINE_IGNORED。
4. DML（INSERT/UPDATE）保持原样。升序拼接天然保住顺序依赖，例如 yak-semantic 的
   V2 种子必须先于 V12/V13 的修正，否则修正会永久失效。
5. 占位符（Security 的 `${appName}`、yak-quality 的 `${table}` 等）原样保留：
   每个模块仍用自己的 Flyway 实例与自己的 placeholder 策略，互不影响。
6. modeling 只取空库实际生效的 impact 组，删除 legacy 组目录。

用法
----
    python scripts/db/consolidate-flyway-migrations.py            # 生成
    python scripts/db/consolidate-flyway-migrations.py --dry-run  # 只打印计划
"""

import argparse
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# (输出目录, 输出文件名, 源目录列表)。源目录按顺序提供同组（modeling）内的版本号唯一。
MODULES = [
    (
        "data-ops-business/data-ops-business-agent/src/main/resources/db/migration/yak-agent",
        "V1__agent_baseline.sql",
        [("data-ops-business/data-ops-business-agent/src/main/resources/db/migration/yak-agent", ".")],
    ),
    (
        "data-ops-business/data-ops-business-consumption/src/main/resources/db/migration/yak-consumption",
        "V1__consumption_baseline.sql",
        [("data-ops-business/data-ops-business-consumption/src/main/resources/db/migration/yak-consumption", ".")],
    ),
    (
        "data-ops-business/data-ops-business-data-service/src/main/resources/db/migration/yak-data-service",
        "V1__data_service_baseline.sql",
        [("data-ops-business/data-ops-business-data-service/src/main/resources/db/migration/yak-data-service", ".")],
    ),
    (
        "data-ops-business/data-ops-business-dataset/src/main/resources/db/migration/yak-dataset",
        "V1__dataset_baseline.sql",
        [("data-ops-business/data-ops-business-dataset/src/main/resources/db/migration/yak-dataset", ".")],
    ),
    (
        "data-ops-business/data-ops-business-lifecycle/src/main/resources/db/migration/yak-lifecycle",
        "V1__lifecycle_baseline.sql",
        [("data-ops-business/data-ops-business-lifecycle/src/main/resources/db/migration/yak-lifecycle", ".")],
    ),
    (
        "data-ops-business/data-ops-business-lineage/src/main/resources/db/migration/yak-lineage",
        "V1__lineage_baseline.sql",
        [("data-ops-business/data-ops-business-lineage/src/main/resources/db/migration/yak-lineage", ".")],
    ),
    (
        "data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm",
        "V1__mdm_baseline.sql",
        [("data-ops-business/data-ops-business-mdm/src/main/resources/db/migration/yak-mdm", ".")],
    ),
    (
        "data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/yak-metadata",
        "V1__metadata_baseline.sql",
        [("data-ops-business/data-ops-business-metadata/src/main/resources/db/migration/yak-metadata", ".")],
    ),
    (
        "data-ops-business/data-ops-business-metric/src/main/resources/db/migration/yak-metric",
        "V1__metric_baseline.sql",
        [("data-ops-business/data-ops-business-metric/src/main/resources/db/migration/yak-metric", ".")],
    ),
    (
        # modeling 的 Flyway bean 会按历史表在两组目录里二选一;空库固定走 impact 组。
        "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling",
        "V1__modeling_baseline.sql",
        [
            ("data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling", "."),
            (
                "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact",
                "impact",
            ),
            (
                "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-snapshot",
                "impact-snapshot",
            ),
            (
                "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-foundation",
                "impact-foundation",
            ),
        ],
    ),
    (
        "data-ops-business/data-ops-business-quality/src/main/resources/db/migration/yak-quality",
        "V1__quality_baseline.sql",
        [("data-ops-business/data-ops-business-quality/src/main/resources/db/migration/yak-quality", ".")],
    ),
    (
        "data-ops-business/data-ops-business-security/src/main/resources/db/migration/yak-security",
        "V1__security_baseline.sql",
        [("data-ops-business/data-ops-business-security/src/main/resources/db/migration/yak-security", ".")],
    ),
    (
        "data-ops-business/data-ops-business-semantic/src/main/resources/db/migration/yak-semantic",
        "V1__semantic_baseline.sql",
        [("data-ops-business/data-ops-business-semantic/src/main/resources/db/migration/yak-semantic", ".")],
    ),
    (
        "data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/main/resources/db/migration/yak-offline-sync",
        "V1__offline_sync_baseline.sql",
        [
            (
                "data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/main/resources/db/migration/yak-offline-sync",
                ".",
            )
        ],
    ),
    (
        "data-ops-business/data-ops-business-workflow/src/main/resources/db/migration/yak-workflow",
        "V1__workflow_baseline.sql",
        [("data-ops-business/data-ops-business-workflow/src/main/resources/db/migration/yak-workflow", ".")],
    ),
    # 安全模块分布在两个 Maven module,各自保留一个文件;Flyway 在 classpath 层合并它们。
    (
        "data-ops-boot/src/main/resources/yak-security/db/migration",
        "V1__boot_security_baseline.sql",
        [("data-ops-boot/src/main/resources/yak-security/db/migration", "boot")],
    ),
    (
        "data-ops-framework/data-security/src/main/resources/yak-security/db/migration",
        "V1__security_framework_baseline.sql",
        [("data-ops-framework/data-security/src/main/resources/yak-security/db/migration", "framework")],
    ),
]

# 合并后被 yak-modeling 单文件吸收,整个目录应删除。
ABSORBED_DIRS = [
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-snapshot",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-impact-foundation",
]

# 只服务 legacy 老库的目录,新库永远不走,应删除。
LEGACY_DIRS = [
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-logical",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-logical-foundation",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-logical-snapshot",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-v22-foundation",
    "data-ops-business/data-ops-business-modeling/src/main/resources/db/migration/yak-modeling-history-v22-meta",
]

_BARE_CREATE = re.compile(r"^(\s*CREATE\s+TABLE\s+)(?!IF\s+NOT\s+EXISTS\b)", re.I)
_VERSION = re.compile(r"^V(\d+)__", re.I)


def read_text(path: Path) -> str:
    # 仓库里这些文件是 CRLF,统一成 LF 再拼,避免合并结果行尾混用。
    raw = path.read_bytes().decode("utf-8")
    return raw.replace("\r\n", "\n").replace("\r", "\n")


def version_of(name: str) -> int:
    match = _VERSION.match(name)
    return int(match.group(1)) if match else -1


def collect(sources, self_path: Path):
    """返回按 (版本号, 源目录顺序, 文件名) 排好序的 (路径, 相对位置标签)。"""
    entries = []
    for index, (rel_dir, tag) in enumerate(sources):
        directory = ROOT / rel_dir
        if not directory.is_dir():
            raise SystemExit(f"缺少源目录: {rel_dir}")
        for name in sorted(os.listdir(directory)):
            if not name.endswith(".sql"):
                continue
            if directory / name == self_path:
                continue  # 自己的产物不能当来源,否则重复执行会把上次输出再嵌一遍
            if not name.upper().startswith("V"):
                continue  # B* 基线迁移从不执行,跳过
            version = version_of(name)
            if version < 0:
                continue
            entries.append((version, index, name, directory / name, tag))
    entries.sort(key=lambda item: (item[0], item[1], item[2]))
    return entries


def ensure_idempotent_create(sql: str) -> tuple[str, int]:
    """把裸 CREATE TABLE 补成 IF NOT EXISTS,返回 (改后文本, 改了几处)。"""
    changed = 0
    out = []
    for line in sql.split("\n"):
        new = _BARE_CREATE.sub(lambda m: f"{m.group(1)}IF NOT EXISTS ", line)
        if new != line:
            changed += 1
        out.append(new)
    return "\n".join(out), changed


def build(output_path: Path, output_name: str, sources, dry_run: bool) -> None:
    entries = collect(sources, output_path)
    if not entries:
        raise SystemExit(f"{output_path} 没有任何 V* 迁移")

    chunks = [
        "-- 本文件由 scripts/db/consolidate-flyway-migrations.py 生成,请勿手改;"
        "要改结构请改脚本后重新生成。",
        f"-- 合并了 {len(entries)} 个版本化迁移(SQL 原文按版本号升序,未做逻辑改写)。",
        "-- 被合并的文件:",
    ]
    for _version, _index, name, _path, tag in entries:
        chunks.append(f"--   V{_version}__ [{tag}] {name}")

    total_table_fixes = 0
    for _version, _index, _name, path, tag in entries:
        sql = read_text(path).rstrip("\n")
        sql, table_fixes = ensure_idempotent_create(sql)
        total_table_fixes += table_fixes
        chunks.append("")
        chunks.append(f"-- Source: {path.relative_to(ROOT).as_posix()}")
        chunks.append(sql)

    chunks.append("")
    body = "\n".join(chunks)
    lines = body.split("\n")

    if dry_run:
        print(f"[dry-run] {output_path.relative_to(ROOT).as_posix()}: "
              f"{len(entries)} 个来源, {len(lines)} 行, 裸建表修正 {total_table_fixes} 处")
        for _version, _index, name, _path, tag in entries:
            print(f"    V{_version}__  {name}")
        return

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(body, encoding="utf-8", newline="")
    print(f"{output_path.relative_to(ROOT).as_posix()}: {len(entries)} 个来源, "
          f"{len(lines)} 行, 裸建表修正 {total_table_fixes} 处")


def cleanup(dry_run: bool) -> None:
    """删掉已被合并吸收的旧文件与目录。"""
    removed = 0
    for out_dir, out_name, _sources in MODULES:
        keep = os.path.basename(out_dir) and (ROOT / out_dir / out_name)
        directory = ROOT / out_dir
        for name in sorted(os.listdir(directory)):
            if not name.endswith(".sql") or name == out_name:
                continue
            target = directory / name
            if not dry_run:
                target.unlink()
            removed += 1
        assert keep.exists() or dry_run, f"合并结果缺失: {keep}"
    for rel in ABSORBED_DIRS + LEGACY_DIRS:
        directory = ROOT / rel
        if not directory.is_dir():
            continue
        for name in sorted(os.listdir(directory)):
            if name.endswith(".sql"):
                target = directory / name
                if not dry_run:
                    target.unlink()
                removed += 1
        # 目录内已无 .sql 时把空目录也删掉,避免留下无意义的层级。
        if not any(directory.iterdir()):
            if not dry_run:
                directory.rmdir()
    print(f"\n删除旧迁移文件/目录: {removed} 个 .sql"
          + ("(dry-run,未实际删除)" if dry_run else ""))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="只打印计划,不写文件")
    parser.add_argument("--no-cleanup", action="store_true", help="只生成,不删除旧文件")
    args = parser.parse_args()

    for out_dir, out_name, sources in MODULES:
        build(ROOT / out_dir / out_name, out_name, sources, args.dry_run)

    if args.no_cleanup:
        return
    cleanup(args.dry_run)


if __name__ == "__main__":
    main()

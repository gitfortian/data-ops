# Ticket 112：存储层——本模块自持的 7 张表与 PO/Mapper

**对应需求：** 元数据中心（存储） | **阶段：** P0 | **模块：** data-ops-business-metadata + data-ops-common

**What to build：** 元数据自持的 7 张表一次建齐（Flyway 可跑通），PO 落 common，Mapper 可用。P0 不写任何业务逻辑。

**Blocked by：** 110

**表清单来源**（plan §2.1）：B 案下本模块**自持 9 张、与 lineage 共管 1 张，合计 10 张**。元模型 2 张在 ticket 128，共管的 `yak_metadata_asset` 在 ticket 133，**本票是剩余 7 张**。

**验收清单**
- [ ] `V1__create_metadata_tables.sql`（落 `db/migration/yak-metadata`）建 7 张：
      `yak_md_asset_extension` / `yak_md_collect_job` / `yak_md_collect_run` / `yak_md_register_retry` / `yak_md_change` / `yak_md_label` / `yak_md_task`
- [ ] `yak_md_asset_extension`：**逐字照抄 plan §2.3 代码块末段**（PK `(asset_id, extension)`，指向别人的主键，`json_schema` 记产出版本）
- [ ] `yak_md_register_retry`：**逐字照抄 plan §3.2c**——`UNIQUE KEY uk_yak_md_retry_change (project_id, type_name, asset_key, source_updated_at)`，键在**变更**上不在 `status` 上（键含 status 会让一条实体 DONE 之后再也无法重新登记）；`create_time/update_time` 显式 NOT NULL 无默认，写入侧必须带值
- [ ] `yak_md_collect_job` / `yak_md_collect_run`：按 plan §3.1/§3.3/§3.4/§3.7 口径落 DDL——job 侧含作用域（数据源/库/表 pattern）、cron、开关、`provider_type`；run 侧含 `cnt_new/cnt_changed/cnt_unchanged/cnt_gone` 四计数 + `SUSPECT`/`FAILED` 状态 + dry_run 标记 + 游标水位。**物理采集与投影对账共用这两张**（plan §3.7"不新开第三张运行历史表"）
- [ ] `yak_md_change`：**append-only**，库层与代码层都不给 UPDATE/DELETE 路径（清理走 `changed_at` 冷数据归档，plan §2.6）
- [ ] `yak_md_label` / `yak_md_task`：DDL 逐字照抄 plan §6.1/§6.2（`yak_md_task` 含 `open_marker` + CHECK 的"只允许一条开放行"形状）
- [ ] PO 落 `data-ops-common/…/bean/po/metadata/`，枚举/常量落 `…/constant/metadata/`（plan §0.8）；7 个 `BaseMapper`
- [ ] **`yak_md_task` 三条库层用例先在本票内用真实 MySQL 临时表预演通过**（重复开放待办 → 1062；漏刷 `open_marker` → 3819；办结后同目标可再建 → OK；plan §10 测试 11）
- [ ] **禁用 `UNIQUE (…, nullable列)` 表达"只允许一条"**（plan §9 T17：NULL 彼此相异，方向正好写反；生成列方案在本仓库不可用，实测 `3109`）
- [ ] 无物理外键（plan §0.9）

**验证边界：** 迁移文件在本机 `yak_security` 用**临时库/临时表**预演；正式生效待用户重启 IntelliJ。

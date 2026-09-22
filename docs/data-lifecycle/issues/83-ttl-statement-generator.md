# Ticket 83：TTL 语句生成器（Doris / Paimon）

**对应需求：** 3.4 | **阶段：** P1 | **模块：** lifecycle

**What to build：** 给定（生效策略, 模型表信息）输出唯一一条可执行语句或 no-op 说明：DORIS/STARROCKS 方言 → `ALTER TABLE db.tbl SET("dynamic_partition.*")`；PAIMON → `ALTER TABLE tbl SET('partition.expiration-time'=...)`；destroy 空 → Doris `enable=false`、Paimon no-op（D4）；不支持的方言 → 只读语句（可复制不可下发，D2/§3.2）。纯函数、无 IO。

**Blocked by：** 82

**验收清单**
- [ ] `generate/TtlStatementGenerator`：`TtlStatement(storageType, script, writable, noOpReason)`；输入=PolicySpec+TtlModelSource+LayerTarget
- [ ] 映射表按 design.md §3.2 全实现：DAY/MONTH/YEAR × 有无热段 × 永久；end=3、prefix=p 常量默认（留高级参数）
- [ ] `GET /models/{id}/lifecycle` 响应内嵌 statement 字段（Tab 的 SQL 预览即本生成器输出）
- [ ] 全边界单测（黄金 SQL 字符串断言：与 requirement 3.4 示例逐字对齐）
- [ ] 无库名前缀时（Paimon catalog 场景）表名引用规则在单测中固化

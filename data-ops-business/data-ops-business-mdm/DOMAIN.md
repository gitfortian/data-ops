# MDM Domain

## 核心概念

### 主数据实体(Entity)

跨系统共享的核心业务对象(客户、商品、供应商等),是属性、来源、记录、变更、分发的归属根。项目内 `entity_code` 唯一,创建后不可改(稳定键,同 modeling 模型编码)。

### 主数据属性(Attribute)

实体的字段定义,角色分 PK(唯一标识)/ATTR(业务属性)/RELATION(关系)。**类型/单位/码值/安全引用 semantic 标准**(松散 ID/码集编码),不允许自由填写;`attr_code` 实体内唯一,创建后不可改。PK 角色实体内唯一。

### 主数据来源(Source)

实体 ↔ 数据源的绑定:识别(53)确认后生成,角色 MAIN(主来源)/AUXILIARY(辅助来源)。采集配置(字段映射/采集方式/频率)挂于来源(54)。字段映射 = 源表字段 → 实体属性。

### 主数据记录(Record)

一条主数据:跨系统唯一 `master_id`、`attributes`(JSON 属性值)、`source_ids`(JSON,各系统原始 ID 映射)、`status`(ACTIVE/MERGED/DELETED)、`version`。**核心口径(requirement.md 七 D3/D4)**:master_id 跨系统统一;source_ids 记录各系统原始 ID,是追溯与多源关联的键。

### 变更与审批(Change / Approval)

主数据变更需审批(requirement.md 七 D5):变更申请(CREATE/UPDATE/MERGE/DELETE,含变更内容对比)→ 审批流(一级数据管理员、二级数据治理负责人,级别可配置)→ 通过后生效(改 record + version 递增)/拒绝。审批是主数据特有能力(design.md 3.5 复用率 0%)。

### 分发(Distribution)

主数据分发回各业务系统(requirement.md 七 D6):目标系统、方式(API/MESSAGE/FILE)、频率;执行复用 data-service 能力,监控状态/失败率/最近分发时间。

### 清洗规则(Clean Rule)

实体级配置,三种类型:DEDUP(去重:字段匹配 + 相似度)、STANDARDIZE(标准化:引用标准码值/单位映射)、COMPLETE(补全:默认值/跨来源补全)。规则表达式存 JSON。

## 不变量

1. **编码唯一**:`(project_id, entity_code)`、`(project_id, entity_id, attr_code)` 唯一,DB 唯一键兜底;编码创建后不可改。
2. **项目空间归属**:全部业务行带 `project_id`,只取服务端可信上下文(`CurrentProject`),不建物理外键(与 modeling/semantic 一致)。
3. **master_id 跨系统唯一**:记录级唯一键 `(project_id, entity_id, master_id)`;增量采集按 source_ids 定位已有记录更新,不无脑新增。PK 属性是 master_id 身份材料,审批、合并与标准化/补全均不得改写 PK。
4. **记录状态语义**:仅 ACTIVE 记录对外可见(查询 API/分发);MERGED 为合并后的次记录(保留以追溯);DELETED 软删除;实体 DISABLED 时记录不得通过 MDM 服务 API 或已发布分发被消费。
5. **变更需审批**:未审批的变更不得生效;审批动作留痕(申请人/审批人/时间);版本号单调递增,变更可回溯。记录治理写入按期望版本 compare-and-set,竞争失败不得静默覆盖。
6. **字段来源与治理优先级**:来源重加工刷新未受治理保护的属性;获批 UPDATE、实际修改字段的清洗/合并保留该字段值,后续来源刷新不得覆盖。恢复来源需经 UPDATE 审批清除字段保护,并在之后的来源加工中生效。相同属性与来源 ID 均未变化的重复加工不增加版本;来源 ID 按数据源唯一键更新,不得因重跑变成数组或重复累积。
7. **复用边界**:MDM 不建执行引擎(采集走 sync)、不建质量引擎(走 quality)、不建 API 网关(走 data-service)、不建血缘引擎(走 lineage)。执行/调度/规则引擎归被依赖模块。
8. **删除 = 引用校验后物理删除**:实体删除前校验属性/来源引用;属性删除前校验采集映射/清洗规则与已有记录字段引用;删除靠审计留痕,不建回收站。
9. **统计服务端聚合**:总览/分析统计禁止无界 list() 后内存统计;独立容错,"查不到"≠0(home-overview-contract)。

> 本组记录正确性补充不改变既有跨域 Truth Owner。完整用户行为与验收约束见 `docs/product/features/F-007-mdm-record-correctness.md`。

## 对外引用契约(消费方视角)

- modeling 经 `api` 包消费主数据(数仓维表引用 master_id,规划,随后续 ticket);展示名由 MDM SPI 解析。
- 其他模块跳转进入 MDM 总览/详情时,经路由携带项目上下文,不直读本模块表。

# Semantic 模块设计(data-ops-business-semantic)

> 状态:v1.0(2026-09-14) | 决策来源:[m4-integration.md 第 8 节 决策 E](../model/m4-integration.md)
> 定位:本文是 **ticket 30 契约文件集的直接输入**。模块创建后,契约以模块内 `README/DOMAIN/ARCHITECTURE/DEPENDENCIES/REQUIREMENTS/REVIEW` 为准,本文降级为设计背景与索引。

## 1. 定位与边界

semantic 是**被依赖的全局上游**:定义"规矩"(数据标准)与"要什么"(业务过程字段集),以及"落在哪"(数仓分层配置)。建模(integration/开发/质量/服务/资产等未来消费方)按 SPI 消费,不感知其内部实现。

| | 内容 |
| --- | --- |
| **拥有** | 六类数据标准(命名/类型/码值/单位/口径/安全);业务域/业务过程/标准字段集(全局字段库+过程引用);业务过程↔源表关联;数仓分层配置;标准版本快照;引用/绕过统计 |
| **不拥有** | 模型/字段/主键/索引/分区/映射/血缘/版本(modeling);数据源连接与驱动(datasource);任何建模管道(决策 A:全系统一个编辑器/一套存储/一条血缘) |

## 2. 依赖规则(compile-time 强制)

1. **仅 modeling→semantic 单向**;semantic 不 import modeling(DEPENDENCIES.md 声明 + 依赖边界测试守护)。
2. **semantic→datasource 仅公共契约**(36 连通性校验、37 库/数据源引用),不违反单向约束。
3. **跨模块数据引用 = 松散 ID**,无物理外键;展示名经 SPI 批量解析——消费方不解析语义内容、不 join semantic 表。

| 引用方存储 | 引用 | 用途 |
| --- | --- | --- |
| modeling 列(`yak_modeling_model_column`) | std_type_id / std_naming_id / std_code_id / std_unit_id / std_caliber_id / std_security_id | 39 套用、44 派生 |
| modeling 模型 | process_id | 44 派生归属、47 主线视图 |
| modeling 映射(43) | process_field_id、layer(37 分层引用) | 派生自动记录 |
| modeling 血缘(23/45) | std_field_id | 标准字段级血缘 |

## 3. SPI 契约面(方法级)

全部接口放 semantic 模块 `api` 包(对齐 dataset 先例);只读接口无事务副作用;演进纪律 **只加不改**。

| SPI | 方法(语义签名) | 调用方(ticket) | 失败语义 |
| --- | --- | --- | --- |
| `StandardQueryApi` | `page(kind, query)` / `get(kind, id)` / `resolveBatch(refs): id→{code,name,…}` | 38/39/43/44 展示解析 | 查询失败降级为 ID 裸显,fail-open |
| `StandardRecommendApi` | `recommend(ctx{fieldName,dataType,role,layer}): candidates[{stdId,score,reason}]` | 39/41/48 | 推荐失败返回空,不阻断 |
| `StandardCaptureApi` | `capture(req{kind,code,value,source{modelId,columnId,operator}}): stdId` | 40(双入口) | 同名校验;失败阻断沉淀动作本身 |
| `StandardUsageApi` | `record(event{stdId,type:APPLY\|BYPASS,scene,modelId,operator})` | 38/39/41 埋点 | **异步容错**,失败仅记日志 |
| `ProcessApi` | `listDomains()` / `listProcesses(domainId)` / `getFieldSets(processId)` / `getField(fieldId)` | 44/47/48 | 只读;失败空态 |
| `LayerConfigApi` | `listLayers()` / `resolve(layerId): {database,datasourceId,namingStdId,partition,format,lifecycle}` | 38/44/48 | 只读;失败空态 |

## 4. 数据归属与 scoping(A8)

- 业务表**全部带 project_id(PROJECT_REQUIRED,控制器端点强制)**,与平台一致;"全局规范层"= 模块级全局。
- **例外**:平台预置模板表无 project_id(31,"初始化预置标准"幂等端点复制为项目行)。
- 表清单(六类标准 ×1 组 / 版本快照 / 业务域 / 业务过程 / 标准字段集 / process_field 引用 / 过程源表关联 / 分层配置 / 预置模板 / 引用统计):V1~Vn 自持迁移,详见各 ticket。

## 5. 接线清单(ticket 30)

| 项 | 约定 |
| --- | --- |
| Maven | 对齐 modeling 先例:parent 聚合 + root pom dependencyManagement + boot 依赖 |
| Flyway | `db/migration/yak-semantic`,V1 起编,模块自持 |
| 菜单 | yak-security 迁移 **≥V2019**(建模 V2018 已用);menuCode 建议 `semantic-standard` / `semantic-domain` / `semantic-process` / `semantic-layer` |
| 权限 | `SemanticPermissionCode`(common):semantic:read/create/update/delete;前端 securityMenuCodes/projectContext 同步 |
| 错误码 | **42001+ 段**(41001/41901/43001 已占用) |
| 前端 | `pages/semantic/` 路由;navigationMenuContract 测试迁移清单登记 |
| 审计 | BusinessAuditService 门面(fail-open,经 AuditTransactions 延迟到提交后) |

## 6. 闭环链路(模块泳道视角)

```text
[semantic] 标准(30~32) ─被引用→ 业务域/过程/字段集(33~35) ─关联→ 源表(36) ─定位→ 分层(37)
        ↓ SPI 消费
[modeling] 38 ODS 套用 → 40 沉淀(写回 semantic) → 39/41 套用推荐 → 44 派生(43 映射/45 血缘)
        ↓ 埋点上报                    ↓ 前端跳转
[semantic] 42 引用/绕过统计 ←── [modeling] 46 影响分析 / 47 主线视图
        ↓ AI
[agent] 48 建模助手技能(消费同一 SPI)
```

## 7. 风险与权衡

见 [m4-integration.md 第 8.4 节](../model/m4-integration.md):契约双份成本、SPI 只加不改、回退成本低(并回 modeling 只是包移动)、方案 B 触发条件、命名无冲突。

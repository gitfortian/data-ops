# Ticket 82：模型绑定与继承解析 + modeling 只读 SPI

**对应需求：** 3.3 | **阶段：** P1 | **模块：** lifecycle + modeling

**What to build：** 每个模型有确定的"生效 TTL 策略"：默认继承所在分层默认策略（无绑定行=继承）；可绑定任意策略覆盖、可解绑回继承；层无策略时以 `semantic_layer.lifecycle_days` 合成只读兜底（D1）。`GET /models/{id}/lifecycle` 返回策略、来源（INHERIT/OVERRIDE/LEGACY_LAYER/NONE）、状态、语句预览入口标记。

**Blocked by：** 80, 81

**硬性约束：** modeling 侧只**新增** `ModelTtlQueryApi`，不改 `ModelQueryApi` 既有签名；lifecycle 不得直读 modeling 表。

**验收清单**
- [ ] modeling 新增 `api/ModelTtlQueryApi`：`TtlModelSource(Long id, String code, String name, String layerCode, String dialect, String tableName, String partitionType, String partitionColumnsJson)`（tableName 兜底 model_code，复用 `ModelStructureRepository.findTableName`），实现+注册；契约文档更新
- [ ] lifecycle `ModelTtlBindingService`：resolve（继承/覆盖/兜底合成）+ bind + unbind（幂等）
- [ ] `PUT/DELETE /api/v1/lifecycle/models/{modelId}/lifecycle/binding`（校验策略存在、模型存在）
- [ ] 解析链路：layerCode→`LayerConfigApi.resolveByCode`→库名/数据源；缺失时 previewable=false + 原因文案
- [ ] 单测：继承优先序（覆盖>层默认>旧字段兜底>UNSET）、解绑回退、跨项目隔离
- [ ] 编译绿 + 相关模块单测绿

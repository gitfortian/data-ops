# Ticket 84：TTL 分区预览

**对应需求：** 3.5 | **阶段：** P1 | **模块：** lifecycle

**What to build：** 用户对单个/批量模型发起预览：对 Doris 系目标执行 `SHOW PARTITIONS FROM db.tbl` 真实枚举，按三段归类（热/冷/将被删除），列出将删分区名与预计首次清理时间，返回 5 分钟时效 confirmToken；能力不足的数据源降级为"边界推算"并标 `estimated=true`（D6）；非时间分区表返回 previewable=false+人话原因（47006），不抛异常。

**Blocked by：** 83

**硬性约束：** 查询仅经 `TtlSqlGateway`（包 `DataSourceExecutionProvider`）；分区名解析容错（p20260918/20260918/dt=2026-09-18 等常见格式）。

**验收清单**
- [ ] `dispatch/TtlSqlGateway` 端口 + adapter（open→execute→rows，超时 30s，maxRows 保护）
- [ ] `preview/TtlPreviewService`：分区名→LocalDate 解析器（按粒度三分支）+ 归类 + 计数
- [ ] `POST /api/v1/lifecycle/models/lifecycle/preview` {modelIds[]} → 逐模型 PreviewView
- [ ] confirmToken：HMAC(modelIds+policyHash+时间窗) 校验工具，85 票消费
- [ ] 单测：解析器边界、归类算术、降级路径（gateway 返回异常→estimated）

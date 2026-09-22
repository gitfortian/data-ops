# 09: 列表筛选欠账(域/负责人筛选 + calRule 从口径带出)

**对应需求:** 指标中心盘点 §3.1/§3.3/§6(P2)| 阶段: P2

**What to build:** 旧-工单 46/47 尾部欠账三件:①列表"按业务域筛选"——后端 `MetricRepository.page(domainId,…)` 已支持、**纯缺 UI**,放个树/下拉即可;②"按负责人筛选"——`MetricPO.owner` 字段已存在,但 page 查询无 owner 条件、前端无输入,前后端各补一步;③编辑表单 calRule/statDimensions **无输入项**(后端字段/接口齐备),且选中口径标准后应"从标准带出 cal_rule"(旧-工单 46 要求);另 M-9 观察项:新建时负责人默认当前用户。

**模块归属:** metric(manage 列表页 + MetricEditModal + repository 一处查询条件)

**Blocked by:** 无;statDimensions 展示端在 06,表单端在本单,注意字段口径一致

**Status:** 已完成(2026-09-22；页面实测随批次统一验收)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);calRule 带出为**预填可改**不锁死(与口径标准解耦现状一致);编辑态 toFormValues 透传逻辑保留,M-2"静默吞错"修复成果不回退;域筛选交互遵守全仓业务域树口径(f65d4a6c1/13facf419 后)。

- [x] 列表:业务域筛选 UI(接现有 domainId 参数)
- [x] 后端 page 增 owner 条件 + 列表负责人筛选(默认当前用户快捷项)
- [x] 编辑弹窗:calRule/statDimensions Form.Item;选口径时拉标准 cal_rule 预填
- [x] 新建负责人默认当前用户(M-9 收口)
- [x] 列表加口径列展示(§3.3 问题尾差)

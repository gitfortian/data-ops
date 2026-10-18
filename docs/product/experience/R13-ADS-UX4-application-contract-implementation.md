# R13-C1｜ADS 应用输出合同持久化与验证实施说明

状态：IMPLEMENTATION CANDIDATE。独立分支 `feature/r13-ads-application-contract-20261018`。这是 ADS 正式实施的第一纵向切片，不声称 ADS 端到端交付。

## 本切片目标

将已签收 ADS 高保真中的核心设计事实持久化在 Modeling 原域：应用身份、消费类型、目标粒度、精确上游版本、输出字段、业务键、固定过滤/排序、Metric 引用、刷新目标和版本化草稿。ADS 设计合同与物理结构、Metric 定义、Runtime、Dataset/Service 消费授权分别独立。

## 必须守住的边界

- 只接受当前项目内、同业务过程、已发布的 DWS（默认）或用户显式选择的已发布 DWD；不读取上游活草稿、不自动发布、不自动切层。
- 固定保存 `upstreamModelId + upstreamVersionId + versionNo + checksum`；源发布版本指针改变后报告漂移，必须用户显式重新确认来源与输出字段，不静默升级 latest。
- 输出字段必须存在于精确上游版本快照和 ADS 已保存物理结构；来源列与目标列都必须绑定正式标准字段，且标准字段 ID 一致。业务键必须是输出字段的非空子集；字段名重复、空键、非法过滤/排序文本、缺应用身份、跨过程/跨项目来源必须可解释拒绝。
- 设计保存/发布不运行 SQL、不创建 DataDev TaskRevision、不授予 Dataset/Service 权限、不声称真实数据通过。V1 真实只读预演与 V2 运行后对账继续 NOT_CONNECTED / NOT_EXECUTED，直至各自 Owner 授权。
- 迁移必须同时覆盖 MySQL 与 PostgreSQL，修订历史必须原子持久化；重试同一内容幂等，不产生虚假重复 revision。

## 黄金验收

1. 订单经营日报：已发布 DWS 日×地区版本，输出业务日/地区/订单数/金额，业务键 `order_day,city_id`，固定样本仅用于交互，不证明真实结果。
2. 订单明细 API：显式改用已发布 DWD 明细版本，保留真实明细业务键，显示切换差异并要求重新确认输出字段；不能套用 DWS 汇总指标。
3. 来源 V2→V3、MetricVersion 变化、目标物理结构变化后，当前合同显示 STALE；不自动更换 pin。
4. 重复业务键、输出字段不存在/重复、无效 appCode、跨过程/未发布来源应被服务端阻断。
5. 权限不足/敏感字段不允许泄露；应用设计发布不代表服务可调用。
6. MySQL/PostgreSQL migration smoke、服务单测、Controller 权限/项目隔离测试、前端 Jest/type-baseline/release build 全绿。

## 切片交付顺序

C1：应用合同/版本修订/服务端静态校验 API + migration + 测试。  
C2：ADS 六步工作台接入合同 API，完成草稿保存/恢复/版本漂移、输出校验、同口径差异解释。  
C3：只读交接状态接续 Dataset/Service/DataDev 原域（只读，不伪造授权/执行）。  
C4：经 DR-07 #557 与相关 Truth Owner 授权后，单独规划真实预演/运行后对账；不可从 C1-C3 自动获得权限。

本 PR 如只交付 C1，标题和验收必须明确为 ADS 应用合同基础，不得宣称 ADS R13-C 全部闭环。


## C1 高保证补强（2026-10-18）

针对独立审核发现的覆盖风险，本 C1 分支继续增加以下服务端约束，必须通过 CI 才能宣称成立：

1. 合同保存请求新增 \`expectedRevision\`；新合同为 \`0\`，已有合同传当前修订号。写入前锁定 ADS Model 聚合行并原子检查现有修订，冲突拒绝，不能用 UI 的读取检查冒充乐观并发控制。
2. 内容哈希完全相同仍然幂等回读，不创建虚假的新修订；内容变化才需进行 \`expectedRevision\` 对比。
3. 输出列除存在于 ADS 当前物理结构外，必须同时绑定其正式 \`stdFieldId\`，且请求里的 \`standardFieldId\` 与物理结构一致。只有前端提示不算标准门禁。
4. 空业务键和非法 SHA-256 输入返回可解释的静态 blockers，避免空值异常把正常阻断变成 500。
5. 来源字段缺少标准绑定、来源/目标标准 ID 不一致时明确阻断；只要存在任一 blocker，验证结果就不返回可保存的 Contract 对象。

**仍不在 C1 范围**：消费域的字段级授权、Metric 正式引用核验、真实预演、加工运行及对账；这些不得由 C1 的静态 PASS 推断成功。C2 前端需显式传入 \`expectedRevision\`。

**待收口证据**：本分支最新 Maven/架构 CI、MySQL 实际迁移及 PostgreSQL 实际迁移的独立记录、Controller 项目权限负例、至少一条完整正例（首次保存→回读→重复幂等→新修订）与故障/并发负例。上述未完成前保持 Draft。

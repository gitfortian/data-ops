# R13-C1｜ADS 应用输出合同持久化与验证实施说明

状态：IMPLEMENTATION CANDIDATE。独立分支 `feature/r13-ads-application-contract-20261018`。这是 ADS 正式实施的第一纵向切片，不声称 ADS 端到端交付。

## 本切片目标

将已签收 ADS 高保真中的核心设计事实持久化在 Modeling 原域：应用身份、消费类型、目标粒度、精确上游版本、输出字段、业务键、固定过滤/排序、Metric 引用、刷新目标和版本化草稿。ADS 设计合同与物理结构、Metric 定义、Runtime、Dataset/Service 消费授权分别独立。

## 必须守住的边界

- 只接受当前项目内、同业务过程、已发布的 DWS（默认）或用户显式选择的已发布 DWD；不读取上游活草稿、不自动发布、不自动切层。
- 固定保存 `upstreamModelId + upstreamVersionId + versionNo + checksum`；源发布版本指针改变后报告漂移，必须用户显式重新确认来源与输出字段，不静默升级 latest。
- 输出字段必须存在于精确上游版本快照，业务键必须是输出字段的非空子集；字段名重复、空键、非法过滤/排序文本、缺应用身份、跨过程/跨项目来源必须可解释拒绝。
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

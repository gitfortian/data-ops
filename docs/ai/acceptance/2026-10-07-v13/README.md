# V13 工程验证与真实验收

范围：[F-021](../../../product/features/F-021-agent-governance-evidence-review.md)，交付 [IMPLEMENTATION_V13](../../IMPLEMENTATION_V13.md)。最终 PR/CI 链接提交前补充，本版须在 V12 后合并。

## 工程回归

定向覆盖五态与未知更新时间、筛选/回答切换、安全文本、共享卡片、重复/多块/非法回链、Dataset 合法来源、唯一 OK 事实与重复字段。完整前端、类型基线、生产构建/manifest、Product/Architecture 和 Node 工程测试作为合并条件。

最终本地：前端 132 suites / 694 tests 通过；类型门禁 139 既有诊断无新增；生产构建/manifest 及 Java/前端边界通过。Maven Agent 与全部上游 test 为 BUILD SUCCESS；本地数据库环境缺失的集成测试仍按原条件跳过，真实 MySQL 留 CI 核验。Node 工程回归 14 例沿用 V12，无脚本变更。

证据时间问题先以真实 AgentScope/官方 StateStore 的最终消息测试复现（observedAt 非字符串导致断言失败），修复后同测试及完整 Agent 回归通过；最终输出与官方历史仍逐字一致。历史 epoch-seconds 兼容、拒绝猜测毫秒/非法类型分别有前端回归，未回写历史。

## 真实验收

EV01～EV06、真实模型/登录态源权限与专家语义核对全部 **PENDING**。当前仅工程实现，旧待办保留，Feature 保持 IMPLEMENTING。真实执行须记录部署/模型、用户/项目、原对象/回答/轮 ID、来源与读取时点、原页权限/事实及观察结果。

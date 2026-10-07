# V12 工程验证与真实验收

范围：[F-020](../../../product/features/F-020-agent-suggestion-lifecycle.md)，交付说明 [IMPLEMENTATION_V12](../../IMPLEMENTATION_V12.md)。工程验证结果及 PR 链接在提交前更新，最终发行 CI 以 PR 记录为准。

本版 [PR #329](https://github.com/gitfortian/data-ops/pull/329) 记录最终头提交、Product/Architecture、完整后端/MySQL、前端与发行包 CI；真实验收状态不随合并改变。

## 工程回归

候选面板定向测试覆盖完成前不采纳、唯一原轮关联、FAILED 固定文案、同轮反问、活动/刷新、断线保留输入、精确停止/丢应答/待答竞态、提交双击/晚回执、切目标/晚历史、原去重/人工采纳。完整前端、类型基线、构建与 Product/Architecture 护栏作为本版合并条件。

本地结果：完整前端 131 suites / 676 tests 通过，其中候选面板 17 例；类型门禁通过，保留原 139 项诊断，无新增；生产构建/manifest 通过；Product 基线 22 Features、Java/前端边界及 Node 工程回归 14 例通过。未改后端执行代码，完整后端/MySQL 与发行包由 PR CI 验证。Jest 完整测试退出码为 0，保留此前已有的一秒退出提示。

## 真实验收

SL01～SL08 全部 **PENDING**。按用户要求先完成代码与 CI；真实模型、登录态、源域保存/运行审计及专家语义核对独立待完成。旧 G/T/QP/RA/SC/AF/HE/NR/GP/LC 待办保留，Feature 保持 IMPLEMENTING。自动化不能证明准确率、真实授权或用户收益。

实际执行时逐例记录账号/项目、部署版本/模型、目标对象、会话/轮 ID、原页/trace/源审计引用、预期与观察结果，避免把测试 fixture 当业务事实。

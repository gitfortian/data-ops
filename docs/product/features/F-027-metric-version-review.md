# F-027 — 精确指标版本与治理证据核对

Status: IMPLEMENTING
Approval: 用户于 2026-10-08 授权下一批开发，明确包含指标解释深化及完整任务串联。
Product basis: ACCEPTED PD-003；APPROVED F-005；IMPLEMENTING F-025。

User：指标设计与审核人员。Problem：首版只能解释当前已保存草稿，无法核对发布版本与真实验证阻断。Capability：原指标详情选择当前或历史精确版本解释口径；并在同页核对原 Draft diff、验证/发布证据及已知影响。Journey：详情 → 选定版本 → 按需原 Skill 解释不可变快照 → 原版本对比/治理证据 → 回原编辑器修改 → 原验证/发布。Expected Outcome：减少版本误认及定位阻断耗时，真实收益待采集。

Truth Owner：Metric 拥有版本/验证/发布，Lineage/Consumption 各自拥有关系/运行事实。Metric 授权只读投影及原治理读取为 producer，原详情为 consumer；复用 F-025 SDK、Skill、预算和轮次，无新事实库、导航或业务命令。

解释目标显式区分 CURRENT 与 SNAPSHOT，旧目标缺模式保持 CURRENT。CURRENT 仍拒绝当前版本漂移；SNAPSHOT 允许已有历史精确版本，但必须核对项目、Metric READ、版本 ID、不可变快照 digest。SNAPSHOT 结果仅阅读，不可带入业务说明。当前治理证据与历史定义分开，不把现有发布指针/当前依赖状态重写进旧快照。

原治理面板的校验、发布、撤回及 diff 继续显式操作；提供精确发布版本解释入口。失败/撤权/项目或对象切换清除旧结果并阻止晚响应；provider 失败不表示零影响或验证通过。无额外源域读取权限与自动推理。

Domain Gap：原解释投影只接受当前版本，最小扩展授权历史快照读取；依赖方向不变。验收 MV01–MV06：当前漂移拒绝、历史精确解释、发布/Draft 分离、版本切换晚响应、撤权/损坏快照、原验证阻断及对比回链。真实登录态/模型/专家核对 PENDING。

# FE-A3｜业务优先物理建模工作台：入口、已验证业务上下文与创建草稿

日期：2026-10-14。基线：#513 全域 J1–J6 故事地图；#550 v0.8.2 设计候选；#559 FE-A；已合并 #566“冻结逻辑→物理模型只读审阅”。

## 任务价值与本轮范围

原模型列表以技术表为中心，新建向导名为“下一步”但实际立即调用 `createModelingModel`。从 `/modeling?processId=...` 进入时，仅初始化过程 ID 而未同步根业务域，异步业务域监听可能清空过程，首次用户需重新填配置或在未确认归属时创建错误模型。即使现有 Modeling Owner 已有冻结逻辑版本，创建向导也没有告诉使用者“有没有业务逻辑依据，下一步应该做什么”。

此次聚焦 **正式 React 产品的可用性/身份边界纵向切片**，不是做另一套原型：

1. 模型列表页展示“业务过程和粒度 → 逻辑实体与关系 → 物理模型及字段 → 来源映射和版本执行”的任务路径。第一段只在 Semantic Owner 实际核验 `processId` 后展示已选身份；其他步骤均表述为“单独核验”，不是冒充完成。
2. 正式新建 Drawer 先选业务根域与所属过程，再选现有数仓分层/目标方言、填写名称和编码；最后明确“创建物理草稿”执行真实 `createModelingModel`，**不会自动发布、编 SQL、绑定正式逻辑版本或创建 DataDev TaskRevision**。完成后直达该物理模型表结构 `tab=structure`。
3. 针对业务过程深链接，先由原页面 `getSemanticProcess(id)` 在当前 Project 核验，再从当前 Semantic Domain Tree 递归找到根业务域。只以**实际 Owner 目录**中的 `domainId/processId` 为准，不把 URI 的 ID 当权限。非本域的过程、未加载业务树/目标层、错误方言或无效项目均拒绝提交；用户显式切换域时才清空不符合新域的过程，不能让 Form.useWatch 副作用抹掉初始预填。
4. 非 ODS 建议先确认业务逻辑草稿/冻结版本。新增 `ModelCreationLogicalBasis` 按项目和正式过程读取 `listLogicalDrafts`、显式选择后进一步由 `getLogicalDraft` 和 `listLogicalVersions` 校验冻结版本；读取错误与合法“没有草稿”分开显示。此信息仅作**当前设计证据和跳转逻辑页**，不能随物理模型创建请求变成伪造的持久化绑定。
5. ODS 是贴源例外：提示用户通过 DataSource 已核验源表的直建入口进 ODS，手工新建 ODS 不会假装已经精确绑定源表。
6. Drawer 关闭或跨工作区跳转时，只在用户确实修改了向导表单后确认放弃。项目切换需清理向导和重新读取语义字典；尚未证实过程、领域配置期间不允许创建。
7. 对应权限来自现有 `modeling:create`/ `modeling:read`，服务端最终裁决。没有新增状态表、模型实体、DDL/RBAC、强制非 ODS 发布门禁或资源外写入。

## 业务用例与完成定义

- 订单案例：业务域“交易/订单”，过程 `order_create`，粒度“每笔订单”；订单明细是独立粒度。由已授权过程进入新建物理草稿后保留根业务域和 `order_create`，引导查看该过程的冻结逻辑草稿。重新选择其他业务域不得静默沿用订单过程。
- 空白项目：没有业务过程、冻结逻辑或物理模型时提供明确路径；不凭名称推断“已对应”。
- DWD/DIM：先建议业务逻辑依据，非 ODS 创建依然是草稿；需在模型详情“业务逻辑依据”真实重新读取并预检，正式映射仍待 #556 DR-06。
- ODS：从 DataSource 源表进入能保留精确来源及安全回链；手工新建不冒充自动带源。
- 只读/撤权：不显示可执行的创建操作，也不查询未经许可的逻辑目录；任何 URL/Project 切换会重新核验 Semantic Owner 数据。

## 自动化检查与限制

- `modelCreationScope.test.ts`：多层业务域、过程归属、无效域/过程、ODS 例外。
- `ModelCreationLogicalBasis.test.tsx`：ODS、只读、没有过程、项目 Owner 读取失败与空状态边界。
- `ModelCreateWizardDrawer.test.tsx`：过程级预填、根域和业务粒度、无效 URL、树目录尚未加载不得创建。
- CI：Product Guard、静态架构、受影响 Jest、TypeScript baseline；生产构建/后端合同按 CI 实际执行范围报告，不能提前宣称通过。

**不在本轮：** DR-06 LogicalVersion→PhysicalModel 正式映射的持久写协议；#557 DataDev TaskRevision 的发布溯源；字段自动映射/多源 Join/派生表达式新后端合同；v0.8.2 全站重绘和独立多角色真实 E2E。后续须在真实环境做 J1 黄金故事 + 320/390/768/1440 视口、键盘导航及高数据量目录压力测试。

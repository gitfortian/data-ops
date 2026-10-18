# R13-C2｜ADS 应用输出工作台正式接续（实施证据）

状态：**DRAFT / CI VERIFYING**。对应 #600、PR #601，前置合同服务端 PR #599。此文件只记录代码范围和待完成证据，不是生产签收。

## 1. 用户可见功能

沿用现有 Modeling 模型详情路由和页面风格，ADS 对象中嵌入六步输出工作台，不新增专业工具一级菜单：

1. **应用目标**：appCode、appName、owner、purpose、consumerType（Report/Dataset/API/Analysis）。
2. **精确来源**：从同项目同业务过程已发布 DWS（默认）选择；DWD 明细为主动切换，弹窗确认并丢弃不再适用的旧映射。冻结模型 ID、版本 ID、版本号、checksum；同时保存目标物理结构指纹。上游发布指针或目标结构指纹漂移均显示 stale/blocker，不能自动 latest 或静默接受新结构。
3. **输出设计**：已发布上游快照的列 → ADS 现有物理结构的列，输出列必须已有标准字段绑定；确定业务键和敏感等级标识。
4. **业务口径**：有限过滤和排序文本、独立 MetricVersion ID 引用及刷新意图；提示 Metric 原域尚未核验，刷新意图不是 Runtime SLA。
5. **验证保存**：POST 服务端 V0 静态验证，PUT 携带 expectedRevision 与用户已核对的 expectedTargetStructureFingerprint 进行原子修订/结构保护，GET 回读合同并比较 revision 与合同哈希；静态 PASS 不代表真实数据、权限或服务可用。
6. **历史交接**：GET 最多 50 条不可变修订记录，选择旧版只载入新草稿（不会倒写历史）；明确将样本验证与运行后对账标为 NOT_CONNECTED，外部消费授权标为 UNKNOWN；Modeling 自有的 DELIVERY_NOT_CONFIGURED 只描述设计交付配置，不能代表 Dataset/Service 原域确实不存在授权。

正常保存后检查 revision 与哈希回读，不以 PUT 成功提示替代数据库事实。重读时有未保存草稿的二次确认，整页刷新/关闭由 beforeunload 防丢失提示保护。保存请求在途期间锁定可编辑字段、来源切换、历史草稿复用和重新读取，避免回读覆盖用户正在编辑的内容；请求结束后恢复编辑。若 C1 服务不可访问，失败并保留错误，不展示固定样例数据伪造在线结果。

## 2. 服务/API 事实表

| 操作 | Modeling API | 效果 |
| --- | --- | --- |
| GET 当前 | \`/api/v1/modeling/models/{modelId}/ads-application\` | 回读最新合同/修订 |
| GET 历史 | \`.../history\` | 查询不可变 revision |
| POST 校验 | \`.../validate\` | 返回 blocker、warning 和静态指纹 |
| PUT 保存 | \`.../ads-application\` | 需要 expectedRevision；无真实数据执行 |

## 3. 测试与质量门禁

新增 \`adsApplication.test.ts\`、\`adsApplicationState.test.ts\`、\`AdsApplicationDesignPanel.test.tsx\`。已提交的测试覆盖端点、版本冻结与来源切层清空映射、历史草稿对象不共享引用、真实后端回读、漂移阻断、物理结构未保存和字段标准缺失拒绝、展示未连接/未执行/未授权事实。

完整 Jest、TypeScript 基线、前端发布构建、架构门禁和 Product Guard **以对应最新 head 的 Actions 检查结果为准**。不得用先前 commit 的绿色 CI 替代最终证据。

## 4. 尚存的产品和联动缺口

- #599 尚未合并到 main 时，API 不存在于正式运行主线；单独发布 C2 前端会出现可靠的错误状态，而不是可用闭环。**合并顺序必须 C1 → C2**。
- C1 仅保存完整且静态有效的合同，不支持 UX-D5 提出的「不完整应用需求草稿先暂存」；须明确独立后续存储契约，不能将本地状态称为服务端草稿。
- 前端在加载/重读和浏览器关闭时保护未保存的编辑；同 SPA 路由跳转的统一脏状态拦截，仍需与宿主导航管理联动核验。
- Metric 版本及口径核验、来源类型和安全授权策略、真实查询预演、运行/质量对账、Dataset/Service 交接由各自 Truth Owner 后续接入。当前标签不证明消费可用。
- 真实业务数据验收必须在已部署 C1 的项目环境下重走：首次保存 R1 → 同内容幂等 R1 → 变更 R2 → 历史回读 → DWS 漂移 → 显式 DWD 例外 → 未授权拒绝 → 非标准字段阻断。

## 5. 合并门槛

C1 的后端/迁移/权限测试通过并进入 main；C2 最新提交前端 Jest、类型基线、发布构建、Product Guard、Architecture Gate 全绿；功能正反例及消费者未连接状态得到明确验收。否则保持 Draft，不能以 HTML 高保真截图宣称生产可用。


## Rebased delivery checkpoint (2026-10-18)

- C1 PR #599 is merged to `main` as `9a7f0e03d27ef8406442e7253f6b3a83df59dd71`.
- The stale-base PR #601 is closed as superseded; PR #604 is the sole active C2 implementation candidate.
- The model detail mount was reapplied to the latest `main` file instead of replacing the full file with an older snapshot.
- The earlier C2 CI result is historical only. The rebased head must pass a fresh Product Guard, full frontend Jest/type-baseline/release build, and architecture gate before it can be marked Ready.

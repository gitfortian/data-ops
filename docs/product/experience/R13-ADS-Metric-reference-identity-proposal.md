# R13｜ADS Metric 引用身份与服务端核验设计提案

状态：**方案已获产品负责人批准（2026-10-18）；第一阶段已由 [PR #608](https://github.com/gitfortian/data-ops/pull/608) 合并。主线完整 CI 以 [Actions run #39104498173](https://github.com/gitfortian/data-ops/actions/runs/39104498173) 的实时结论为准**。  
关联：Issue #605、C3 Issue #602、C1 PR #599、C2 PR #604。

## 1. 问题与事实

当前 ADS 合同只有可选的 `metricVersionId`。UI 以数字输入接收该值，并明确提示 Metric 原域尚未核验；C1 的 `AdsApplicationDesignService.validateAndFreeze` 只把它复制进冻结合同，没有验证该 Metric 版本是否存在、是否已发布、是否已撤回或快照是否漂移。

Metric 原域已经有发布合同，身份字段包括：
- `metricId`
- `metricVersionId`
- `metricVersion`
- `snapshotDigest`
- `publicationEventId`

Metric 版本详情路由按 `metricId + versionNo` 查询；当前发布合同路由按 `metricId` 查询。活跃发布列表可用于展示目录，但不应成为“任意 metricVersionId 都能被验证”的唯一服务端机制。

模块依赖方向也很重要：Metric 模块依赖 Modeling（其 Controller 引用了 Modeling API），而 Modeling 不依赖 Metric。不得让 Modeling 直接依赖 Metric 模块来查版本，否则会引入反向依赖/循环风险。

## 2. 推荐的引用模型

新增可选的完整引用对象（命名待 API 评审）：

```ts
interface MetricPublicationPin {
  metricId: number;
  metricVersionId: number;
  metricVersion: number;
  snapshotDigest: string;
  publicationEventId: number;
}
```

ADS 合同将完整引用对象作为事实快照保存；`metricVersionId` 只作为旧合同兼容字段，不再作为新合同的唯一身份。合同哈希覆盖完整引用身份，历史修订保留当时冻结值。

选择该模型的原因：
- Metric ID + version ID/version 可以定位定义及历史版本；
- snapshot digest 用于识别同一版本的内容身份，避免只依赖可变的当前指针；
- publication event ID 让“当时基于哪次发布证据选择”可追溯；
- 当前是否仍为活跃发布是动态证据，不应与合同冻结身份混为一谈。

## 3. 服务端核验边界

**推荐评估基于 data-ops-spi 的只读 Provider 契约**，由 Metric 模块提供实现、Modeling 调用 SPI，不直接引入 Modeling → Metric Maven 依赖。此方案需先检查现有 SPI 注册、可选 Provider 与权限上下文机制，不能仅按本提案直接编码。

Provider 输入至少包含当前 project context 与完整引用；输出应为最小化、明确的状态：
- `VERIFIED_ACTIVE`
- `WITHDRAWN` / `NOT_CURRENTLY_PUBLISHED`
- `VERSION_MISMATCH`
- `DIGEST_MISMATCH`
- `NOT_FOUND`（只在调用者有权确认且不会泄露其他项目存在性时）
- `FORBIDDEN`
- `UNAVAILABLE`

核验要求：
1. 仅在当前项目范围内解析 Metric 与发布事件；跨项目引用不能泄露对象存在性。
2. 读取 Metric 的用户权限必须按现有安全机制核实，不能因为 Modeling 的 UPDATE 权限自动获得 Metric 的 READ 权限。
3. `validate` 与 `save` 都必须通过同一服务端核验边界；前端选择器不是安全门禁。
4. 新合同保存要求版本身份、快照摘要和发布事件匹配，并符合“新合同只引用允许的新发布”策略。
5. 已保存合同后来遇到撤回或新版本发布时，不改写历史合同；读取时展示动态状态（如 `STALE` / `NOT_CURRENTLY_PUBLISHED`），由用户显式选新发布并保存新修订。
6. Provider 不可用、权限不足或响应不兼容时失败关闭，不把错误转换成“指标不存在”或校验通过。
7. 不在 Modeling 中发布、撤回、编辑 Metric，也不在保存过程中自动切换到最新版本。

## 4. 前端工作台调整

- 用只读的“已发布指标”选择器替换裸数字输入；选择项展示指标编码/名称、版本、摘要短指纹、发布时间。
- 选择器数据由 Metric 原域只读 API/受治理的目录 Provider 提供。
- 选择后在当前草稿中保存完整 pin；显示“当前引用版本已核验 / 已撤回 / 版本或摘要不一致 / 无法核验”。
- 当前合同为旧格式且只有 `metricVersionId` 时，展示 `UNKNOWN · 旧合同缺少完整 Metric 身份`，不尝试通过全局 ID 猜测补齐；用户需要显式选择新版本并创建新合同修订。
- 未选择 Metric 时显示 `NOT_CONFIGURED`，而不是错误或通过。
- 所有 Metric 读失败、403、超时、Provider 未接入均分别表达；不得自动回退到 mock 列表。

## 5. 备选方案比较

| 方案 | 说明 | 结论 |
|---|---|---|
| A. 继续只存 metricVersionId，并从 active publications 列表匹配 | 不能稳定定位已撤回/不可见版本；全量扫描且缺少完整快照 pin | 不推荐 |
| B. ADS 保存完整 Metric 发布 pin，通过 SPI/provider 在服务端核验 | 保留模块依赖方向、支持项目隔离和服务端门禁，合同历史可重建 | **推荐评审** |
| C. Modeling 直接依赖 Metric 模块/Service | 破坏现有依赖方向，可能引入循环依赖 | 禁止 |
| D. 只在前端读取 Metric，后端照常接受任意 ID | 可改善提示，但可绕过，无法保证持久化合同正确性 | 不满足高保证 |

## 6. 向后兼容与迁移

- 历史 JSON 中只有 `metricVersionId` 的合同必须可读；不静默修改既有 revision 或哈希。
- 旧合同显示 `LEGACY_INCOMPLETE / UNKNOWN`，用户显式重选 Metric 后保存新修订。
- 因 C1 按合同 JSON 与 hash 存储，初步预计不需要新增关系表列；仍需核验 JSON schema、hash canonicalization、旧版本反序列化与数据库长度约束后才能定案。
- 若选择新的顶层字段名，需同时更新后端 Request/Contract/ValidationResult、前端 API 类型、state adapter、组件、历史回读和契约测试。

## 7. 验收矩阵

- 当前项目有效发布、旧历史版本、撤回发布、同版本摘要不一致、版本 ID 不匹配。
- Metric 不存在、跨项目引用、当前用户无 Metric READ、Provider 超时/不可用。
- 旧合同仅有 metricVersionId、无 Metric 引用、用户重选新版本并保存新 revision。
- 保存并发冲突、同内容幂等、历史合同哈希稳定。
- Architecture Checks 不出现模块循环；Product Guard、完整后端/前端测试和分发构建通过。

## 8. 已批准决策与实施边界

产品负责人于 2026-10-18 批准：
1. 新 ADS 合同冻结完整 Metric publication pin：metricId + metricVersionId + metricVersion + snapshotDigest + publicationEventId。
2. Metric 模块通过 data-ops-spi 提供只读核验；Modeling 不直接依赖 Metric 模块，避免反向 Maven 依赖/循环。
3. 新合同只允许当前项目内、当前有效、身份与摘要完全匹配且 Metric 启用的发布；撤回/被替换版本不能用于新修订。
4. 历史合同保留原始 pin、revision 与 hash，不自动补齐旧引用；旧合同仅有 metricVersionId 时显示身份不完整，用户显式重选或清除后创建新修订。
5. Metric READ 与项目访问必须在 provider 内核验；权限不足、跨项目、服务不可用均失败关闭，不泄露跨项目对象是否存在。
6. PR #608 已合并第一阶段：SPI/provider、服务端 validate/save 门禁、最小已发布引用 API、工作台选择器和回归测试。后续 C3 只读交接证据卡仍独立实施，不代替 Metric 原域管理；DataDev/Runtime 的稳定关联设计单独跟踪于 Issue #609。

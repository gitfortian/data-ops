# Datasource Review

> 本文件定义如何 Review。Reviewer / AI 是裁判，不是需求设计者；不得边 Review 边自行补需求。

## Review 前必读

```text
REQUIREMENTS.md
ARCHITECTURE.md
DEPENDENCIES.md
DOMAIN.md
../../CODE_STYLE.md
REVIEW.md
yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN.md
PR diff / tests
```

仓库统一工程风格以根目录 `CODE_STYLE.md` 为准；本文件只补充 Datasource 的领域、依赖、安全与兼容 Review 标准。

## 1. Requirement Alignment

检查是否属于已有能力，是否改变数据源生命周期、Catalog、SQL Execution、Plugin Contract、REST/DB compatibility。未定义的新能力报告：

```text
Requirement Gap
```

## 2. Architecture Compliance

重点检查：

- 新类是否放在表达真实能力的 package；
- 类名与 role 是否符合根目录 `CODE_STYLE.md`；
- 是否重新出现 `service/common/helper/utils/util/base` 模糊桶；
- 是否无理由新增 `ServiceImpl` 或 `@Service`；
- 新 package edge 是否在 `DEPENDENCIES.md`，是否形成 cycle；
- Controller 是否直连 DAO/PO/raw SPI/gateway.adapter；
- management/query/connection/catalog 是否越过 Repository/Gateway；
- `plugin` 是否反向依赖 `gateway`；
- `exception` 是否反向依赖 Controller class；
- `execution.adapter` 的 raw SPI 例外是否扩散到 Runtime/Policy/Aggregate。

违反长期 contract 报告 `Architecture Violation`，不要通过放宽矩阵掩盖。

## 3. Domain Compliance

检查：

- DTO/VO/PO/SPI model 是否冒充 Domain；
- `dbType` 不可变、ConnectionProfile -> `UNKNOWN` 是否保持；
- Aggregate 是否重新开放 public setter；
- Core Domain 是否引入 Spring/MyBatis/SPI；
- Gateway contract 是否泄漏 SPI/DTO/VO/PO；
- Catalog 是否重新接受业务 Map；
- Plugin Descriptor/Capability 是否与实现一致；
- SQL Runtime 是否复制第二套生命周期；
- SQL Audit Reader 是否重新接受/返回 HTTP DTO/VO；
- 是否通过隐藏 Map key/boolean/VO 字段绕过模型。

违反规则报告 `Domain Violation`；模型无法表达需求报告 `Domain Gap`。

## 4. Correctness

重点检查：

- 空值、边界值、类型/环境解析；
- name uniqueness、update/delete not-found；
- Secret merge/mask、嵌套 SSH Secret；
- 保存/未保存连接测试状态语义；
- Catalog TABLE/SQL 模式、历史 alias、变量、正则匹配；
- preview/count/describe 单条 SELECT；
- SPI typed Catalog mapping；
- SQL Policy 是否在打开数据源前拒绝非法请求；
- transaction 同 Session、rollback、cancel、timeout、SKIPPED；
- Plugin Descriptor -> Business -> HTTP VO shape；
- SQL Audit filter/result transport shape、`OTHER` bucket、duration 精度。

## 5. Compatibility

必须保持：

```text
REST API / JSON shape
yak_ops_data_source / Flyway
yak-ops-core SQL Execution contract
Datasource Plugin SPI v1
built-in plugin runtime behavior
Task Plugin SQL execution provider
```

未来 Plugin SPI breaking change 必须先提升 apiVersion 并提供迁移计划；禁止借此连带修改 REST/DB。

## 6. Safety

重点检查：

- Secret 明文输出；
- JDBC URL / error message 凭据泄漏；
- 掩码覆盖真实 stored secret；
- Capability 声明虚假能力；
- Catalog 绕过只读检查；
- read-only caller 绕过 SQL Policy；
- cancel 后继续 Statement；transaction 失败未 rollback；timeout 误归类。

## 7. Tests / Guardrails

每个 P0/P1 都回答“哪个测试应该挡住”。至少覆盖：

```text
DataSource aggregate lifecycle / no setter
ConnectionProfile / connection status
Secret codec + text masker
Core Domain boundary
Gateway Port no SPI/DTO/VO/PO
Top-level dependency matrix + no cycles
raw SPI / persistence / HTTP Map corridor
no broad bucket / no default @Service
repository-level code-style regressions
Catalog typed request / read-only policy
Descriptor capability contract
SQL aggregate lifecycle / transaction / cancellation / timeout
SQL Audit transport mapping
Controller permission/compatibility
```

## 8. 明确边界例外

```text
execution.adapter.BusinessDataSourceExecutionProvider
  -> outward Task Plugin SPI adapter

execution.audit
  -> observability read-side may read SqlExecutionAuditDao projection
```

例外只能存在于记录的位置，不能扩散。

## 9. 严重级别

```text
P0 Blocker
- Secret 泄漏
- 数据不可恢复破坏
- 明确安全问题

P1 Must Fix
- 业务结果错误
- Requirements/Domain/Architecture/Dependencies 明确违规
- package cycle / boundary bypass
- SPI/PO/HTTP Map 泄漏
- SQL lifecycle/transaction/cancel/timeout 错误
- 明确兼容性缺陷

P2 Suggestion
- 有明确收益的可维护性、性能或测试改进
- 不阻塞合并
```

纯命名、格式或个人偏好不算问题，除非造成真实歧义、违反仓库 `CODE_STYLE.md` 或导致结构回退。

## 10. 问题证据要求

每个有效问题包含：位置、级别、依据、触发场景、风险、修复方向、应命中的测试。没有触发场景和实际风险，不要凑问题。

## 固定输出格式

```text
# Review Result

Conclusion: PASS | CHANGES_REQUIRED

## P0 Blocker
无 / 问题列表

## P1 Must Fix
无 / 问题列表

## P2 Suggestion
无 / 问题列表

## Requirement Gap
无 / 说明

## Domain Gap
无 / 说明

## Architecture Gap
无 / 说明

## Missing Tests
无 / 说明
```

- 有 P0/P1 -> `CHANGES_REQUIRED`。
- 只有 P2 -> 可以 `PASS`。
- 没有真实问题 -> `PASS`，不要硬凑问题。

# P2 后端代码整洁化 · 全 Reactor 综合审计及收口清单

> Scope: #345 / **单一 P2 综合 PR**。文档性质：Engineering Evidence / Review，**不代表产品或架构迁移的新授权**。
>
> 基线：2026-10-09 `main@6ea85ebeb46c45cd1d9b92b1d80a5c3ea3875886`（PR 起点）。并行架构重构 Agent 可能继续更新 `main`，审计应以待合并 PR 的 HEAD 和正式 CI 为准。

## 1. 为什么 P2 只走一个综合 PR

P1 已在多个单域 PR 合并。用户要求 P2 **整体规划，一次性集中验收**，避免每个 Spring/Mapper 小类各自触发一次 20–60 分钟的 CI。由此本阶段不另建多条 P2 子 PR。

单 PR 同时承载：全仓 Java 审计器、跨域动态注册/独立发布保护、确实行为等价的机械清理、JUnit 回归与阶段剩余证据。**不为追求删文件数而删除不可证明无调用的 Controller、Bean、Mapper、Adapter。**

## 2. 盘点范围与证据模型

在 PR 起点 Git tree `truncated=false`，共观察 **3,324 个 `src/main/java/*.java` 和 912 个 `src/test/java/*.java`**（统计包含框架和独立兼容目录）；这是物理文件盘点，**不等于 3,324 个运行时可达对象，也不等于所有文件都在根 reactor 中**。

新增可执行 `scripts/architecture/backend-p2-reachability-audit.mjs`，读取 `git ls-files`、全部生产 Java 及仓库内 Java/XML/配置/SQL/测试等文本引用，输出：

- 原路径、候选主类型名与包限定名；
- 被其他跟踪文本文件提及的文件数及最多 3 条引用证据；
- 类型名冲突时 `AMBIGUOUS_NAME_REVIEW`；
- Spring/Mapper/REST/Entity/Configuration/外部 SPI/FQCN/配置或独立兼容发行线索：`IMPLICIT_ENTRYPOINT_KEEP_UNTIL_PROVEN`；
- 有直接文本调用：`REFERENCED_KEEP`；
- **零文本命中**：`UNRESOLVED_NO_TEXT_MATCH_REVIEW`，**不是死代码、不是可删除许可**。

运行：`node scripts/architecture/backend-p2-reachability-audit.mjs > /tmp/backend-p2-audit.json`。

这一审计是**保守、可重跑、非运行时证明**。它不解析 Java AST、不完全还原 Maven reactor、动态 SQL、条件注入、反射、外部包消费者与运行时插件。任何报告里标记为 0 引用的文件，仍必须在各 owner 补齐 Bean/SPI/route/配置/测试/外部兼容链才允许删除。输出永不自动执行删除。

## 3. 物理改动（唯一已证实行为完全等价的小类族）

| 实现 / Owner | 代码事实 | 动作与回归 |
|---|---|---|
| Modeling `DefaultImpactRelationshipAdapter`, `PersistenceBackedImpactRelationshipAdapter`, `LogicalEntityImpactRelationshipAdapter`, `LogicalModelImpactRelationshipAdapter`, `MappingImpactRelationshipAdapter` | 两个分支都无条件返回 `Collections.emptyList()`；无根据输入改变输出的路径 | 移除重复 `if (null) return empty` 分支，保留 5 个 public 类型 / method / implements / extension point。JUnit 同时验证 null 与有效 ID 仍返回空集 |
| Modeling `ImpactRelationshipPersistenceAdapter` 默认方法、`ImpactGovernanceService` 默认方法 | 两条相同返回路径，无状态/副作用 | 合并恒空实现；保留 interface/default ABI；JUnit 两种输入覆盖 |
| `DefaultImpactQueryService` 与 `ModelImpactResolver` | 具有真实的策略选择或影响关系返回，而非无条件空集合 | **保留** |
| `TestController` `/api/test/ping` | Spring `@RestController`，是对外 HTTP 路由，即使名称为 Test 也不构成死代码 | **保留** |
| Analysis/Dashboard 数据网关 Adapter | `@Component` + 实际 Dataset/Analysis 依赖转换 | **保留** |
| `data-ops-framework/legacy/data-job/**` | Boot 2/Java 8 独立部署兼容合同 | **保留** |
| Flyway、MyBatis Mapper / PO、Security / Project/RBAC、外部协议 | SQL history、运行装配和对外行为可能不可由静态调用检索证明 | **不移动、不删、不改合同** |

特别说明：Modeling 这些恒空对象不是完整产品实现，**本 PR 不尝试把它们改为真实数据库关系遍历**，那会改变功能范围。没有依据认定其可安全删除或合并成一种 public ABI。

## 4. P2 全域决策矩阵

| 类别 | 本 PR 可执行证据 | 本 PR 决策 |
|---|---|---|
| Reactor 模块与普通 Java 类型 | Git tracked Java、包名、主类型名、跨文件文本引用 | 全域审计与清单，不因静态 0 引用自动删 |
| Spring / Boot 自动发现 | `@Component`/`@Service`/`@Configuration`/`@Bean` 等和 `spring.factories`、`AutoConfiguration.imports` | 视为潜在入口，必须保留 |
| MyBatis / Hibernate / Flyway | Mapper、XML、PO、SQL 版本和注入语义 | 不动 schema/历史，不凭类名判断冗余 |
| 插件 SPI / ServiceLoader / 对外 SDK | `META-INF/services`、反射与独立发布目录 | 默认保留，须由 owner 核销 ABI |
| DTO / response / API route | Java textual inbound references + 反射/HTTP 例外 | 不改字段名/路由/序列化合同 |
| 事务、Project/RBAC、401/403/409 | 运行时交叉链高风险，存在并行架构 Agent 改动 | 整洁性不重构行为 |
| 确切等价双分支 | 相同常量返回且无其它副作用，JUnit 执行双输入 | 本批集中简化 7 个 Modeling 类型 |

## 5. 本阶段验收与剩余不确定性

- `node --test scripts/architecture/backend-p2-reachability-audit.test.mjs`：全域源/测试清单下限、风险分类、Spring/Mapper/独立部署保护、7 个等价清理、报告非删除语义。
- `node --test scripts/architecture/*.test.mjs`：与既有 Framework Legacy、Bean、Flyway 和前端边界守卫一起运行。
- `mvn -pl data-ops-business/data-ops-business-modeling -am test` / CI 后端契约与相关 JUnit：空结果类型 null/non-null 均可用；不改变 Controller/Mapper 注册。
- 不要求做无证据的大规模 Java 类型删除；报告 `UNRESOLVED_NO_TEXT_MATCH_REVIEW` 需要独立运行/依赖证据，**不能作为本 PR 已核销的可删除项**。
- Architecture Checks、Product Guard 和 Impact planner 选择的 CI 全部成功才可手动合并；发布行为与真实环境运行验收不是本 PR 的虚构结论。

**阶段结论**：P2 的全域工具化检查与可机械证明的代码简化合在一个 PR；其余高风险候选记录为“保留/未证明”，不拆 P2 小 PR 硬删。未来若明确开展独立的深度可达性/真实环境证据专项，应另行批准范围，而非声称本次代码删除了 100% 无用符号。

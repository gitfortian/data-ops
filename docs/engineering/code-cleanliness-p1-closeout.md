# #345 工程整洁性治理 · P1 证据收口与 P2/P3 交接

> Class: Evidence / Review。此文**不是 Product Truth、发布命令或新的架构迁移授权**。
>
> 基线：2026-10-09 `gitfortian/data-ops`，各 PR 最终状态按其 GitHub merge 记录核对，禁止推断未知路径无调用。

## 1. 已核销批次（合并证据）

| PR | Scope / 代码证据 | 当前历史状态 |
|---|---|---|
| [#458](https://github.com/gitfortian/data-ops/pull/458) | Data Development / Dataset / Data Service 编辑器兼容边界治理 | merged |
| [#464](https://github.com/gitfortian/data-ops/pull/464) | Realtime Sync 页面 `api.ts` / service `legacy.ts` 集中退役与运行合同守卫 | merged |
| [#468](https://github.com/gitfortian/data-ops/pull/468) | 数据目录、血缘、资源、Dashboard 等页面级薄 Service 桥退役与 import 守卫 | merged |
| [#470](https://github.com/gitfortian/data-ops/pull/470) | Offline Sync 实例历史、日志、指标、调度和批量启停 data-only 迁移；旧 task facade 移除 | merged |
| [#472](https://github.com/gitfortian/data-ops/pull/472) | Offline Sync Single/Multi Guide 定义编辑接口与状态化保存迁移；最后 definition facade 退役 | merged |

**边界说明**：这只是以上已核实模块的兼容层收口，不表示仓库中所有 `legacy` 文件、所有重复实现或 Java 符号全部无调用。前端 `services/**/index.ts` 公共出口、`data-ops-framework/legacy/data-job/**` 等独立发布合同不得推断可删除。

## 2. 本批生成物与文档权威治理

- `data-ops-ui/tsc-output.txt` 是一次性 TypeScript 错误输出，受源码行号和当时编译器版本影响，不能重复用于当前的 CI 断言。已核查前端 `package.json` 的 `check:types` / `tsc` 和 `data-ops-ui/scripts/check-type-baseline.mjs`：**真实基线是 `scripts/type-baseline.json`**；现行 Architecture CI 调用校验脚本生成实时诊断。清理静态输出，明确 `.gitignore` 防止回流；不减轻 TypeScript debt gate 的任何阈值。
- `docs/README.md` 提供基于 Document Governance 的领域化路由。避免日期快照、Review、AI 提案覆盖 Product/Domain/Architecture 的明确主权。
- `.zcode/plans/*.md` 3 份旧工作说明**保留**用于追溯，不作为自动执行命令。特别是“173→30 Flyway migration”提案涉及历史 checksum 和升级兼容，不能因归档而实施其删除步骤。
- 保护脚本 `scripts/architecture/engineering-evidence-hygiene.test.mjs` 防止旧诊断文本回流、文档权威索引丢失，确认真正的类型基线校验与历史 Flyway 守卫仍在。

## 3. 仍未核销的 P2：后端符号级整洁审计

以下事项在未证明下**不删除**：

1. Spring 注入、`@Component`/`@Configuration`/`@Bean`、SPI、ServiceLoader、反射、MyBatis Mapper XML、配置扫描的隐式调用；
2. 非 reactor 的独立兼容发行物、数据库与 Flyway history、外部 API URL；
3. 根据路径同名/文件大小判定的 DTO/Service/Adapter “重复”，需逐 owner 检查入边与消费者；
4. 真正的异常路径/事务边界/项目隔离与 403、409、预检/Publish、exact revision 的回归合同。

后续应以 domain owner 为组形成较大 PR，每组先列 `file → references → bean/spi/config/test → compatibility → remove/keep` 表，再变更源码；若缺任何证据，该候选保留。

## 4. P3 留存和交接标准

- 当前 `docs/product/DOCUMENT_GOVERNANCE.md` 的 Product Truth / Active Change Contract / Domain / Architecture / Operational / Evidence / Historical 分类仍为权威。
- `docs/release/**`、`docs/product/**`、Flyway SQL、模块 `DOMAIN.md`/`REQUIREMENTS.md`/`ARCHITECTURE.md`/`DEPENDENCIES.md` 只在 owner 认可和证据闭环后修改。
- 对旧阶段文件只增加目录索引与可验证来源，不按日期大搬家、合并历史审计材料，也不把 Review 推广成“已验收”。

**验收方式**：Architecture Checks 的静态合同与新增 Node 测试；Product Guard；相关 CI 按 impact planner。PR 检查未通过前不得声称完全核销；负责人手动合并。

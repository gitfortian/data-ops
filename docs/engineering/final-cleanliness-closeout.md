# #345 全仓库整洁性治理 · P3 Final 综合核销与结项交接

> **Class: Engineering Evidence / Review** · Change Type: TECHNICAL · Product Behavior Changed: No.
> 基线：2026-10-09 最新 `main`；本文件是整洁性验收证据，不是 Product Truth、架构迁移授权、发布批准或“所有代码已无冗余”的声明。

## 1. 阶段合并事实

| 阶段 | 已落地的事实与 PR | 不扩大推断 |
|---|---|---|
| P0 | `abc`、`test_placeholder_should_not_create`、`pelican-bicycle.html`、`.zcode/tmp/jest.config.cjs` 已移出 tracked tree；`.gitignore` 防回流 | 不能按名称随意删除其它临时文件 |
| P1 | [#458](https://github.com/gitfortian/data-ops/pull/458)、[#464](https://github.com/gitfortian/data-ops/pull/464)、[#468](https://github.com/gitfortian/data-ops/pull/468)、[#470](https://github.com/gitfortian/data-ops/pull/470)、[#472](https://github.com/gitfortian/data-ops/pull/472) 已合并，详见 [P1 记录](code-cleanliness-p1-closeout.md) | 只代表各批次明确纳入的兼容层 |
| P2 | [#477](https://github.com/gitfortian/data-ops/pull/477) 已合并：全 reactor 保守引用审计、7 个 Modeling 恒空分支的行为等价简化、JUnit | 零文本引用不等于 Spring/Mapper/SPI 类型可删 |
| P3 文档 | [#481](https://github.com/gitfortian/data-ops/pull/481) 已合并：Metric 死链、Framework 命令、历史资料索引、回流测试 | Evidence/Historical 不升级为 Product Truth |
| P3 注释 | [#484](https://github.com/gitfortian/data-ops/pull/484) 已合并：TODO/FIXME/XXX/HACK 生产注释扫描与新增标记检查 | 静态标记不是自动删除许可 |
| **本 PR** | 退役文件及历史脚本回流检查、保留物证据守卫、重复 import 只读审查、综合文档与测试 | 是 #345 授权范围内结项，不声称全库功能或真实环境验收 |

## 2. 按当前 Git Tree 核销

- `data/` 当前仅保留 `data/resources/projects/1/.asf.yaml`。早期 issue 中的 `data/architecture-*-edits.py` 等一次性源码重写脚本已不在跟踪树；不能再按旧盘点清理。`data/resources/**` 属于证据资源，必须保留。
- 前端 `data-development`、`data-service`、`data-source`、`realtime-sync` 的旧 `legacy.ts` 不在当前目录；现存 `api.ts` 与 `index.ts` 保留，并受既有 import/业务合同检查保护。
- `.zcode/plans` 三份历史提案继续作 provenance；涉及 173→30 个 Flyway 迁移的提案**不是执行授权**。
- `data-ops-framework/legacy/data-job/pom.xml` 保留 Boot 2 / Java 8 独立发行兼容；`docs/product/**`、`docs/release/**`、迁移历史守卫和 TypeScript 当前类型债务基线继续保留。
- 既有 `engineering-evidence-hygiene`、`backend-p2-reachability-audit`、`p3-document-entry-integrity`、`p3-source-comment-debt-audit` 和多个前端兼容退役测试全部保留。

以上是路径、已合并 PR 和静态工程边界证据，不意味着所有 TODO 已清零、所有 import 无重复或后端每个类型都动态可达。

## 3. 本综合 PR 的真实改动

新增 [最终整洁性审计器](../../scripts/architecture/final-cleanliness-review.mjs) 与 [自动测试](../../scripts/architecture/final-cleanliness-review.test.mjs)。

1. **已退役对象**：阻止 P0 占位、机器临时配置、旧 TypeScript 报告及四个前端 `legacy.ts` 回流。
2. **禁止误删的证据**：保护 `data/resources` 当前已知文件、三份历史计划、独立 Framework legacy、产品权威文档、发行规范、Flyway 验证脚本及 TypeScript 类型基线。
3. **历史脚本**：仅把 `data/` 根部一次性 Python/CJS、旧本机 JSON 与 `.zcode/tmp` 判为可疑回流，**不将 `data/resources/**` 当作删除对象。
4. **源码审查**：复用 P3 注释扫描，输出按 owner 分类的有 issue / 无追踪标记；仅识别**逐字符一致（忽略末尾分号）的单行静态 import** 重复，生成审阅候选，**不修改任何 import、源码或业务行为**。
5. **扫描边界**：不把测试、历史 Markdown、字符串或多行 import 当成安全删除证据；Spring/Mapper/SPI、反射、运行时调用和外部消费者必须由对应 owner 的独立行为测试或运行证据核销。

新测试覆盖退役物与保护路径、历史脚本分类、精确重复 import、注释 issue 归属、抽样截断、当前 Git tracked tree 和原有防回流合同。本批不另设 GitHub workflow，既有 `node --test scripts/architecture/*.test.mjs` 自动包含新测试。

## 4. 执行与剩余风险

```bash
node scripts/architecture/final-cleanliness-review.mjs
node --test scripts/architecture/final-cleanliness-review.test.mjs
node --test scripts/architecture/*.test.mjs
```

输出为 JSON；`passesGuard` 只评价文件级边界，`samples` 只包含待审查候选。不会执行修改，也不因无追踪 TODO 或 import 重复而自动失败。**候选必须结合真实调用、Bean/Mapper/SPI 注册、公开路由、序列化、外部 ABI 及测试确认后才能修改。**

| 未核销类别 | 本批决策 | 后续处理前提 |
|---|---|---|
| Java Bean / Controller / Mapper / DTO | 保留 | 明确运行装配、调用与兼容证明 |
| TODO/FIXME/XXX/HACK 注释 | 保留或只读记录 | 所属 owner 核对当前 Domain/Feature Contract |
| 相似命名、跨域包角色、疑似未使用 import | 不批量重命名/删除 | 可验证消费者、测试与源码语义 |
| 历史 proposal、独立 legacy 部署 | 保留并守卫 | 不能依据“legacy”“历史”名称判断可删 |
| AI 业务模块与并行 Security/Framework 架构重构 | 范围外 | 由对应 Agent 和 owner 单独管理 |

## 5. #345 最终关闭条件

- [x] 按现行树检查一次性脚本、四个旧兼容文件及保留物。
- [x] 将回流守卫、只读审查、自动回归和阶段交接放在**一个最终 PR**。
- [ ] Product Guard、Architecture Checks 及 impact planner 触发的相关 CI 全部通过。
- [ ] 用户人工 Review、手动合并最终 PR；不开 auto-merge。
- [ ] 合并后再核对并关闭 [#345](https://github.com/gitfortian/data-ops/issues/345)。提交 PR 时不提前关闭 Issue。

**最终交接原则**：结束的是已经授权的 P0–P3 整洁性治理专项；未来发现的新问题跟随所属业务、架构、发布 PR 修改。不为了结项数量强删安全性不明确的代码。

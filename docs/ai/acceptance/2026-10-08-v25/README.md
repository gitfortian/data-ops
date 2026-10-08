# V25 工程验收记录

日期：2026-10-08。Feature：[F-035](../../../product/features/F-035-agent-asset-impact-explanation.md)。基线：d8faa120（包含已合并 #382）。工程回归不替代真实验收，当前不标 SHIPPED。

## 场景证据

| 场景 | 自动化证据 |
| --- | --- |
| AI01 固定目标、purpose、原工具范围 | AssetImpactTargetTest；governance.test.ts / continuation.test.ts |
| AI02 项目 COUNT、根可见性、不加载图 | LineageDownstreamCountTest；AssetDiscoverServiceTest |
| AI03 三类 owner/五态、部分失败与覆盖 | AssetImpactEvidenceTest；AssetDiscoverServiceTest |
| AI04 白名单、超限与证据容量先行 | AssetImpactEvidenceTest |
| AI05 SDK 预读/核验/预算/HITL/最终历史 | AssetImpactRuntimeTest；原 Guard / ToolContract / TurnToolBudget 全集 |
| AI06 明确导航、切项目/路由/撤权/迟到与失败 | 原资产详情 index.test.tsx；Agent 提问准备/continuation 全集 |
| AI07 旧工具与场景 | Agent、Asset、Lineage 全部测试及 Agent 相关前端集合 |

## 真实验收

真实模型回答是否正确区分三类语义、专家评分、登录态撤权及源审计、完整 J2 和收益：PENDING，按用户要求后续统一验收。有效引用、脚本模型或单元测试均不证明语义质量和发布安全。

## 已执行工程检查

- Maven 31 模块 reactor：Agent 63 类 344 测试、Asset 20 类 160 测试、Lineage 12 类 47 测试；共 551，549 通过、2 项既有本地 MySQL 条件测试跳过，无失败。
- Lineage 最终泛型 captor 与文本约定另行复核，6 测试通过。
- TypeScript debt gate 通过，保持 139 项已有诊断，无新增。
- 架构/发布/CI 脚本 42 测试通过；Java 边界 77 reactor entries / 3210 production files、前端边界、62 个迁移基线、37 Feature 产品基线与 Product Guard 自测通过。

- 前端所选 35 suites / 409 tests：首次整组 34 suites / 408 tests 通过；新增入口测试仅因 jest-dom 不支持 toHaveValue 的非对称匹配断言失败，改用 toHaveProperty 后该 suite 63/63 重跑通过，未修改产品行为。资产入口与原 Agent 恢复/准备路径均通过。
- 新资产入口测试隔离 Ant Tabs 的样式渲染，保留真实读取、作用域与导航行为；jsdom 无法解析原页面工具类生成的焦点选择器。原 Agent 安装组件 smoke test 在集合内保留。

- 最终投影边界复核 14 测试通过，补充超大 hop 不能通过整数溢出被识别为一跳；去重后后端共 552 项，550 通过、2 项既有 MySQL 条件跳过。

# V20 原会话场景结果回看验证

日期：2026-10-08。合同：[F-031](../../../product/features/F-031-agent-scenario-history.md)。开发基线 main 72681340（#341 已合并），提交前集成 main 6379b3f0（#343 已合并）；本批生产实现提交 2d0da864。本记录仅为 Evidence。

## 验证范围

原会话通过既有 history/continuation 读取最新完成轮，以完整目标、明确 turnId 和唯一 assistant 引用核对四种场景交付。回看展示候选、原事实、待确认项与原页面入口，不带入表单或调用业务写入。指标定义草稿标题单独识别，历史快照明确版本，新建草稿返回指标列表手工继续。

## 已执行工程验证

- `node node_modules/jest/bin/jest.js src/services/agent src/components/ai src/pages/ai-agent --runInBand --silent`：26 套件、305 项全部通过。覆盖四场景、三类指标、空候选/截断/文本转义、重复（含空重复）/缺失/错轮/错范围、活动或失败轮、刷新读取失败、项目/权限/会话变化与迟到响应、SSE 完成后仍须刷新历史；保留原候选面板、会话/HITL/停止路径回归。
- `node scripts/check-type-baseline.mjs`：通过，139 项既有诊断，无新增；同步 main 后再次通过。
- `npm run build`：集成 main 后生产构建通过，生成前端构建清单。
- `node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs`：25 项通过；新增 main 的 `node --test scripts/ci/*.test.mjs`：15 项通过。
- 前后端架构边界通过（77 reactor entries、3191 production Java files，前端保留既有 1 条声明走廊）；Product Guard 自测与产品治理基线通过。

本批没有生产 Java、数据库、模型配置或 CI 协议变更，不把此前 Maven/真实环境结果冒充本批执行证据。PR 的远端检查另由既有 CI 执行。

## 真实验收

用户于 2026-10-08 明确要求暂不执行测试环境真实模型验收，留待后续统一完成。本批未运行真实模型、登录权限、源保存/验证/发布、J2 查询或人工评分；这些均保持 PENDING，F-031 与既有场景 Feature 保持 IMPLEMENTING。工程回归不代替真实用户旅程验收。

当前 continuation 只有最新轮的权威任务范围，因此旧轮即便带有可解析 JSON 也保留正文与核对提示，不推断旧范围。历史卡不证明当前 Skill、源定义或依赖仍有效，处理时须回到原页面重新核验。

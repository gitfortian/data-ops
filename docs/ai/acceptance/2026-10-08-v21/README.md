# V21 候选失效与停止恢复验证

日期：2026-10-08。开发基线 main be49c7fa，#348 已合并且对应 Product Guard / Architecture Checks 全部通过。生产实现提交 01758f3f；当前合同 F-020/F-023/F-024/F-025/F-028/F-031，本记录仅为 Evidence。

## 已执行工程验证

- `node node_modules/jest/bin/jest.js src/services/agent src/components/ai src/pages/ai-agent --runInBand --silent`：27 套件、328 项全部通过。覆盖检索词变化即时失效、旧轮刷新不恢复不同条件、活动原轮阻止新生成、重复停止/刷新互斥、停止丢应答及完成竞态、切目标/定义/权限失去后恢复、迟到提交/状态/历史/校验回调、缺失与待答状态、固定异常提示、指标准备失败后显式重试及需求保留。四场景原回归和会话回看同步通过。
- `node scripts/check-type-baseline.mjs`：通过，139 项既有诊断，无新增。
- 最终生命周期清理同步失效调整后，共用面板 19 项回归再次全部通过。
- `npm run build`：生产构建通过，生成前端构建清单；构建对应生产实现提交 01758f3f。
- `node --test scripts/architecture/*.test.mjs scripts/release/*.test.mjs scripts/ci/*.test.mjs`：40 项全部通过。
- 前后端架构边界通过：77 reactor entries、3193 production Java files；前端保留既有 1 条声明走廊。Product Guard 自测与产品治理基线通过。

PR 产品影响声明与产品表面检查通过，没有新增后端协议、生产 Java、数据库、源域写入或业务状态。Maven 与远端检查由既有 PR CI 按影响范围执行。

## 真实验收边界

用户已明确真实模型与测试环境验收后续统一做。本批没有运行真实模型、真实登录态、源保存/验证/发布、J2 消费查询或人工收益评分；这些仍 PENDING。固定文案、投影与并发回归不证明自然语言正确、源业务成功或实际收益，相关 Feature 继续 IMPLEMENTING。

原场景面板仍仅消费原轮与源域校验：检索条件变化保留原轮标识以供核对，停止请求不拥有终态；候选带入后仍须人工执行原保存。待答问题回原会话按原 pending 应答，没有新增场景 HITL 机制。

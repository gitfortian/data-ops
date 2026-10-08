# V17 — 指标版本口径解释与业务说明草稿

执行调研 §11 第五步的指标场景，合同 [F-025](../product/features/F-025-skill-metric-caliber.md)。原设计人员在指标编辑框依据当前已保存版本生成 AI 解读与业务说明，查看原始事实，带入 businessDesc 并手工保存回读。完整指标公式生成是后续合同，不作为本版交付。

复用 AgentScope 2.0.3 SkillFilter、DynamicSkillMiddleware、结构化 call、StateStore 和现有 Agent 持久化轮次/预算/取消/历史。新增的是第三个场景登记、Metric 的授权有界不可变快照投影及原编辑器接入；没有第二推理引擎或第二业务定义库。Agent 仅 gateway → metric.api。

每次准备、交付和采纳核对当前版本及既有快照 digest，白名单事实标签/值由源域装配。引用校验不证明自然语言正确；界面展示 AI 解读与原始事实供人工审核。事实超界失败，不截断公式。原保存继续 expectedVersion、定义校验、审计、版本记录；校验和发布独立。

本版以实际第三场景验证公共执行骨架。暂不启用 Harness、脚本资源、多 Agent 或后台自治；尚无需要这些能力的已批准场景和收益证据。验收见 [记录](acceptance/2026-10-08-v17/README.md)，真实模型验收沿用户确认 PENDING。

前端服务层复用同一份服务端交付包解码器和轮次面板，只有各域 schema 校验不同。历史交接包由服务器验证 SDK 对象后编码，Skill 不定义 fenced JSON 输出；页面不从 SSE/模型自由文本提取并写业务。

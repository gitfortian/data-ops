# V24 两次历史质量执行比较

当前产品合同：[F-034](../product/features/F-034-agent-quality-execution-comparison.md) IMPLEMENTING。基于已合并 V23 的 main `568d6d64` 开发，提交前快进到 main `64109d18`（资产 #374、消费验收 #372），无冲突；用户授权继续迭代，真实环境按既有要求延期。

质量负责人从原执行详情的历史运行列表选择“与本次比较”，进入原 Agent。页面显示所选两次身份，问题仅填入，需用户发送；没有合适历史时继续单次排查。基准是用户选择，不保证时间较早。项目/执行/权限变动隔离旧页面，请求代次防止迟到结果覆盖新读取，失败不沿用旧详情。

固定 `qualityBaselineExecutionNo + qualityExecutionNo` 沿用原目标、turn、HITL、预算及消息历史；无新 purpose/表/业务状态。工具没有模型参数，仅从服务器冻结目标读取。只允许比较和原核验/日期/反问/只读 Skill，不能读其他执行、当前监控、Dataset、Python、报告或治理写入。

Quality 先校验执行与监控读取权限，在当前项目确认两次终态、同监控与同历史 datasource/database/schema/table。数据库按规则记录 ID 只读21条，最多展示各20条，按唯一正数 ruleId 对齐；缺失索引只代表该侧可见范围未包含。`recordedDefinitionMatches` 仅比较保存的模板/类型/列/期望值，不能证明完整口径一致。SQL、参数、采样范围、失败样本及异常原文不公开；阈值调整后通过不能直接解释为改善。

字段128/256/512、每侧10000/整体24000 UTF-16 units 有界，超限拒绝。两侧和对齐使用原证据协议，各≤200标量核验；全部返回校验完成后登记，读取失败不发布半份可信差异。原最终回答守卫生成服务端回链、追加人工核对说明，并更新同一条官方历史。

验证记录见 [V24 工程验收](acceptance/2026-10-08-v24/README.md)，统一真实试点题目见 [比较清单](evaluation/quality-execution-comparison.md)。模拟 SDK 测试只验证协议和边界，不证明模型语义正确。真实模型、登录态撤权、源审计、完整 J2、专家评分和收益仍 PENDING；不标记 SHIPPED。

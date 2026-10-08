# Agent V25 — 有限资产影响说明

日期：2026-10-08。产品指令：[F-035](../product/features/F-035-agent-asset-impact-explanation.md)（IMPLEMENTING）。从已合并 V24 的主线 d8faa120 开始，真实模型与完整 J2 按用户安排延期。

## 用户路径与结果

资产负责人在已加载且授权的原详情点击“AI 影响说明”，进入原 Agent，看到固定资产与有限摘要范围。点击问题准备只填写输入，明确发送后才读源证据。回答分别解释一跳结构关系、已接入业务使用与页面访问，并通过原证据链接返回资产详情的使用分区及源域入口人工核对。普通资产解读、描述候选和质量任务保持独立。

## 事实与实现

- 原 GovernanceTarget 资产分支增加 ASSET_IMPACT；唯一读取工具 get_asset_impact_evidence 无模型参数，复用真实用户项目、CHAT_RUN、Asset READ 和原 USAGE 权限。原日期/核验/反问/只读 Skill 可用；其他对象、分区、Dataset、Python、报告与候选工具不开放。
- 复用 AssetGovernanceQueryApi.section(USAGE)，不增加 Asset 公共类型。三类摘要用类别专属白名单分别登记原证据：ASSET 页面活动、LINEAGE 结构关系、METRIC/CONSUMING_DOMAINS 业务摘要。源 owner 未知时不猜测业务 owner；FEDERATED 只表示未能读取原联合来源。子失败不抹去其他证据，不可读 payload 不进入模型。
- 模型只得到标量摘要，文本≤512、每类 JSON≤6000，超限该类 UNAVAILABLE；不传每日明细、任意属性、原异常、SQL、配置或样本。三项证据容量在读取前原子检查；源更新时间缺失保持 unknown。沿用原事实核验规则（只有 OK 证据可核验具体字段）。
- Asset 使用分区原来为关系数量加载全部一跳图。本版经原 LineageQueryService → LineageGraphReader → Repository/DAO 执行当前项目与 sourceAssetId 的 COUNT；先确认可见根，缺项目拒绝，无邻接节点/边物化。继续表示关系条数，超过原 Integer 摘要范围时保留来源不可用。
- 原 SDK 预读缓存只对本次调用有效，后续工具重新授权；预算、HITL 和恢复保持原 turn。只读文本通过原引用守护，移除模型自造 artifact/链接，追加固定范围及人工下一步，替换同一 StateStore 消息，历史和最终输出一致。
- 资产详情按项目、路由和权限集合重建读取作用域；旧请求失效。入口还检查已加载身份与路由匹配、Asset READ 与 CHAT_RUN，不自动发送。URL 与 continuation 均保留 purpose，坏上下文拒绝。

## 表达边界

Lineage 关系不是实际消费，一跳数量不是不同消费者数量；Metric 已记录引用不是 API 调用；Dataset 声明订阅与已归一化成功使用分开；页面访问不代表业务消费。scope/coverageNote、窗口、方向/hop 及来源状态必须保留。不作完整影响、零风险、因果或允许发布判断。跨来源不承诺原子快照。

## 验证

工程回归与实际限制见 [V25 验收记录](acceptance/2026-10-08-v25/README.md)。测试使用实际 SDK 与脚本模型，只证明工程契约；真实语义、撤权审计、完整 J2 及收益均 PENDING。

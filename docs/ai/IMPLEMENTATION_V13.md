# Agent 第十三版交付说明

实施权威：[F-021](../product/features/F-021-agent-governance-evidence-review.md)。范围见 [连续路线](./AGENT_V12_V14_ROADMAP.md)，工程/真实验证见 [记录](./acceptance/2026-10-07-v13/README.md)。本版在 V12 后合并，最终 CI 以本版 PR 为准。

## 使用路径

原聊天回答/历史及编辑器候选共用证据卡。先查看本段证据数量，再按已读取、来源为空、不适用、暂不可用、无权读取筛选；每条显示 ID、源域、对象引用、读取时点、源更新时间和核验值，点击原来源核对。

已读取表示读取成功，不表示业务健康/合规；来源为空也不能推断无风险/无下游。源更新时间未知保持未知，没有自动过期阈值。卡片属于原回答时点，不后台重新读取或自动重新推理。

实际 AgentScope 输出测试确认旧最终编码器将 Instant 写成 epoch-seconds 数值，与原前端字符串契约不一致。新消息统一 ISO 字符串，历史数值按已知旧单位转换为 ISO 毫秒显示，原历史内容不回写。任意数值/毫秒时间戳不猜测接受。

一个回答出现多个证据块或重复 ID 时不选第一份。无有效卡的证据块显示核对提示，普通无证据消息不增加提示。事实只关联唯一有效 OK ID，同一 ID/字段重复值不选其一。回链限定为服务端实际生成的 Asset、Quality、Dataset 原路径，原权限继续由源页裁决。

## 工程与边界

Domain Impact：既有 Evidence/消息消费，无新业务状态或 Domain Gap。Architecture/Dependency Impact：修改原前端 parser/共享组件与原最终消息 JSON 编码格式，无新增走廊/API/存储，源域与服务器引用校验不变。Domain Compliance：保留五态原义，无法证明关联不显示核验值；字段文本由 React 转义，拒绝任意导航及歧义引用。

自动化不证明模型语义准确、当前事实仍未变更或真实源授权；EV01～EV06 全部 PENDING，保持 IMPLEMENTING。

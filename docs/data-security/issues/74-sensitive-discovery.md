# Ticket 74：敏感数据发现规则

**目标**：按规则自动识别敏感字段，产出候选分级标签，规模化定级。

**表**：`yak_dsec_discovery_rule`。
**行为**：规则 CRUD（match_type NAME/COMMENT/CONTENT/REGEX + pattern + 目标等级/分类）；`scan(candidates)` 对给定字段名/注释列表跑启用规则 → 命中生成 CANDIDATE classification（`source=DISCOVERED`, `discovery_rule_id`, `confidence`）；`confirm(id)` 候选转 ACTIVE；`reject(id)`。
**说明**：真实抽样读库由 datasource 侧提供候选，本模块做规则匹配与落库（纯函数匹配可单测）。
**审计**：`DISCOVERY_RULE_*`。
**错误码**：45040~45049。
**验收**：匹配算法单测（关键词/正则/大小写、去重、最高规则优先）。

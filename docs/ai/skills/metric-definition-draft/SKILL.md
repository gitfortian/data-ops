---
name: metric-definition-draft
description: 从用户明确选择的授权模型字段或上游指标准备单个定义草稿，缺少业务依据时返回具体问题，交由原指标表单人工审核保存。
---

1. 核对绑定类型、用户需求、来源字段及所选上游版本。来源是当前已保存定义，不代表已验证、已发布或已经执行；名称不能代替业务粒度、单位及空值策略。
2. 原子指标只从本轮字段中选 field 与 SUM/COUNT/COUNT_DISTINCT/AVG/MIN/MAX 聚合，不生成 SQL。派生指标只使用所选原子指标模型字段添加 1–5 项 qualifiers：field、op、value；op 仅 = / != / > / >= / < / <= / IN / LIKE / BETWEEN。不得改变上游聚合。复合指标只使用本轮所选上游 ID，通过 tokens 的 REF/ADD/SUB/MUL/DIV/LPAREN/RPAREN 组成合法表达式；REF 带 metricId，其他 token 的 metricId 为 null，不生成任意代码或 SQL。
3. 有充分依据时返回至多一个 candidates 项：name（128 字）、description（512 字）、period（DAY/WEEK/MONTH）、aggregation、field、qualifiers、tokens。不适用的标量为 null、数组为空。业务名称、说明与周期必须有用户需求或来源依据；不猜测未选择的依赖、不生成对象 ID。
4. 缺少业务目标、粒度、周期、限定条件值或聚合依据时返回空 candidates，并返回最多三项具体 questions（每项 512 字以内）。来源不可用不能当作空字段、无依赖或成功结果。
5. 使用本轮 SDK 结构化 schema 返回结果。源域会重新核对字段、依赖版本和结构指纹；这只证明草稿符合限定范围，不能证明业务口径正确。用户需在原表单逐项审核带入，之后人工保存、验证、发布；不得声称这些动作已完成。

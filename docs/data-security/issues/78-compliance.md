# Ticket 78：合规规则与体检

**目标**：用规则对现有安全元数据持续体检，量化合规、暴露缺口。

**表**：`yak_dsec_compliance_rule`、`yak_dsec_compliance_finding`。
**规则类型**：
- `SENSITIVE_MUST_CLASSIFIED`：命中敏感关键词/等级的对象必须已定级（未定级→finding）。
- `SENSITIVE_MUST_MASKED`：rank≥门限的已定级对象必须有脱敏策略。
- `ACCESS_MUST_LOGGED`：敏感对象访问须有留痕。
- `CLASSIFY_COVERAGE`：定级覆盖率 < params.threshold → finding。
**行为**：规则 CRUD；`run(ruleId?)` 生成批次 `batch_id`、遍历落 finding、返回汇总 `{checked,passed,failed,gapList}`；`pageFindings(batchId?)`；`latestSummary()` 供总览。
**依赖数据**：读 classification / masking_policy / access_log（本模块内，直连本模块 repository，不外调）。
**错误码**：45080~45089。
**验收**：体检判定纯逻辑单测（各规则命中/通过分支）。

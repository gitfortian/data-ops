# V19 场景评测与 J2 核验

实施合同 [F-030](../../product/features/F-030-agent-scenario-evaluation.md)。这些脚本消费原业务 API 和已有证据，不导入 Skill、不建源数据、不自动保存/验证/发布/查询 Dataset。F-011 中 SDK 2.0.2 是历史实施记录；当前运行时已为 2.0.3，评测应以部署代码和实际 trace 为准。

## 场景执行

先由管理员在原入口配置已审阅的四个 Skill 和模型。按 `cases/skill-scenarios.json` 的 sourceConditions 在专用项目准备对象，核对实际账号权限；不要使用题集中的合成 ID 直接运行。沿用 [环境参数](./README.md)，绑定完整嵌套 target：

```json
{
  "sm01": {
    "account": "authorized",
    "projectId": 1,
    "target": {
      "purpose": "STANDARD_MATCH",
      "standardMatch": {
        "modelId": 101,
        "columnName": "customer_id",
        "dataType": "BIGINT",
        "businessDescription": "客户稳定标识",
        "keyword": "客户"
      }
    }
  }
}
```

这是映射格式示例。其余场景从题集中复制目标结构、替换真实 ID/字段/业务背景；必须保持原任务 purpose、历史 view、指标类型、新建/已有身份形态及上游个数。派生/复合依赖的真实类型和可读性仍需原域核验。按 authorized/denied 分别配置登录凭据与筛选案例，标签本身不证明权限。

```sh
node scripts/ai/run-evaluation.mjs --mode=real --suite=skill-scenarios --cases=sm01 --repeats=1 --out=.task-ai-evaluation/scenarios-real.json
```

| 案例 | 目标 | 自动化边界 |
|---|---|---|
| sm01–sm05 | 标准/说明、信息不足、拒绝、标准/Skill 漂移、批量失败/未知提交 | sm04/05 按 manualSteps 人工完成 |
| mm01–mm04 | 来源字段、歧义、拒绝、源 DDL 漂移 | mm04 人工完成 |
| me01–me05 | 当前解释、历史快照、拒绝、当前漂移、无业务归因证据 | me04 人工完成 |
| md01–md06 | 原子/派生/复合、模糊需求、拒绝、依赖漂移 | md06 人工完成 |

场景 real 执行在 SSE 终帧后读取 trace、continuation 与 history。只有同 session/turn/目标、COMPLETED、唯一关联 assistant 正文，才观测其中唯一结构化回执。服务器可输出 nullable union 成员，比较时规范化 null。当前/历史不能混用，不取历史最后一条或流文本作为候选。一次读取尚未持久化、拒绝、重复回执或上下文不符会保留 UNAVAILABLE/NO_RECEIPT，不重发推理；在原页面核对。

`RECEIPT_ENVELOPE_OBSERVED` 只表示回执头、数量、版本/指纹及目标可以关联，不验证每个候选内容或自然语言语义。候选有效性需原 validate、保存回读与源审计；事实支持由专家评分。不得根据“生成成功”计算正确率。trace 身份不匹配直接报错；未知/非法 token 保留 null，观测不完整标 PARTIAL。输入/历史 JSON 读取最多 2MB，超界报错不截断。

## J2 原页面操作与只读采集

按 [原 J2 验收步骤](../../product/acceptance/agent-batch-j2-2026-10-08.md) 手工完成。当前脚本核对一条 **DWD 模型 → 原子指标精确版本 → Dataset** 的来源链；派生/复合草稿另由 md02/03 验收，不借此声称全部指标类型全程通过。将 `examples/j2-subject.json` 复制到私有工作目录并替换真实稳定 ID、所采纳标准版本与精确来源字段。

```sh
node scripts/ai/read-j2-evidence.mjs --mode=real --subject=.task-ai-evaluation/j2-subject.json --out=.task-ai-evaluation/j2-real.json
```

复用 `AI_EVAL_BASE_URL`、`AI_EVAL_AUTH_HEADERS`、`AI_EVAL_COMMIT`，无默认地址/账号。脚本仅调用 11 个固定 GET，沿用当前 project header/权限，禁止 redirect；不会调用原 Phase5 runner 的验证/发布或 Phase4 runner 的查询命令。每次读取最多 15 秒，逐项缺失/403/provider 不可用保留 UNAVAILABLE；非事务快照，结果仅代表各 API 当次读取时刻。

核对保存字段 TYPE 引用与当前标准版本、来源映射、不可变指标版本中的 modelId、验证/active publication 的 metricVersionId/version/digest、精确 Dataset 声明引用、canonical productKey/project。当前指标草稿可以高于已发布版本，不能拿当前 modelId 替代历史快照。报告只留稳定身份、读结果哈希和阶段观测，不复制定义、SQL、字段背景、账号或 HTTP 错误正文。

`SOURCE_MATCHED` 说明当次源读取匹配；已有对象不能证明本次人工采纳/保存/点击路径。Consumption usageState 只表明聚合覆盖（READY/EMPTY/FORBIDDEN/UNAVAILABLE），不是指定 queryId 或精确指标版本的运行证明。原操作审计、精确查询证据和全程人工复核始终 PENDING；真实采集退出码 **2** 表示人工 E2E 未关闭，配置失败为 1，离线 NOT_RUN 为 0。禁止把退出 2 或已登记引用改写为通过。

## 质量与成本基线

```sh
node scripts/ai/summarize-evaluation.mjs --report=.task-ai-evaluation/scenarios-real.json --out=.task-ai-evaluation/summary.json
node scripts/ai/summarize-evaluation.mjs --report=.task-ai-evaluation/scenarios-real.json --reviews=.task-ai-evaluation/reviews.json --out=.task-ai-evaluation/reviewed-summary.json
```

第一次汇总给出精确 reportHash。人工评审 JSON 格式如下；哈希、turnId 必须复制同一次报告，`sourceAuditRefs` 只留在私有输入，汇总不复制姓名/引用正文：

```json
{
  "version": "F-030-review-v1",
  "reportHash": "从 summary 复制",
  "items": [{
    "caseId": "sm01", "repeat": 1,
    "turnId": "从报告复制", "outputHash": "从报告复制", "traceHash": "从报告复制",
    "reviewer": "评审者本地标识", "sourceAuditRefs": ["原页面校验/保存审计引用"],
    "scores": {"candidateValidity": null, "factSupport": null, "referenceMatch": null, "clarification": null, "taskCompletion": null},
    "manualEdits": null, "adopted": null, "saved": null
  }]
}
```

沿用 [量表](./rubrics.md)：0/1/2 与未评 null 分开；新增 candidateValidity 同样按错误/部分有效/全部有效记录，必须有源核验引用且确有候选。manualEdits 计本例带入后人工修改的表单字段数。adopted/saved 各为本例明确采纳/原保存的候选个数，不能超过观测候选，saved 不能超过 adopted；均需审计引用，引用有效性仍人工核验。多步人工案例不伪造单轮结果，应按原验收记录附证据，不能给 MANUAL_REQUIRED/NOT_RUN 添模型评分。

所有尝试保留总分母；耗时按结果分布并包括失败，token 记录已知样本/未知数量/部分 usage。生成、人工报告采纳、人工报告保存分别保留数量及报告样本量；缺失评分不作零分或通过。汇总绑定整个报告及原轮 output/trace 哈希，重复/错轮/离线评分被拒绝。汇总不自动给出安全通过率，外部审计仍 PENDING。没有完整 usage、版本化模型计价和人工真实对照，费用为 UNKNOWN，收益为 NOT_MEASURED。

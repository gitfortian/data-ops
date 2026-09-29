# Agent 工具契约清单（ToolSpec 示例，Phase 3-D 测试基建）

> 定位：**工具注册的单一事实对照表**。`ToolContractGuardTest` 在 CI 遍历本文件声明的每个工具，
> 与 `toolset` 包的 `@Tool/@ToolParam` 注解逐一核对（存在性、参数名集合、required 标记）；
> 新增工具必须同步登记此处，否则守护测试失败——契约漂移在 CI 被拦截，不靠人肉记忆。
> 本表当前行由源码机械提取校准（2026-08-29）。
>
> 契约项：工具名 / 参数名（`*` = required）/ 调用示例（模型视角）。

| 工具 | 参数（* = 必填） | 调用示例 |
| --- | --- | --- |
| `analyze_with_python` | `code*` | `analyze_with_python(code="df.corr()")` |
| `current_date_info` | `timezone` | `current_date_info()` |
| `get_dataset_fields` | `dataset_id*` | `get_dataset_fields(dataset_id="ds_7")` |
| `list_datasets` | — | `list_datasets()` |
| `request_clarification` | `question*`、`options` | `request_clarification(question="营收口径含税吗？", options=["含税", "不含税"])` |
| `run_dataset_query` | `dataset_id*`、`dimensions`、`metrics`、`filters`、`sorts`、`limit` | `run_dataset_query(dataset_id="ds_7", dimensions=["channel"], filters=["status:eq:PAID"], limit=200)` |
| `save_analysis_report` | `title*`、`markdown_text*` | `save_analysis_report(title="渠道销量周报", markdown_text="## 结论")` |

## 约束（守护测试断言的硬契约）

1. **名称一致**：本表工具名与 `@Tool(name=...)` 完全一致（双向：新增未登记/登记未实现都失败）；
2. **参数集合一致**：本表参数名集合 = 该工具全部 `@ToolParam(name=...)` 集合（顺序无关）；
3. **必填一致**：本表 `*` 标记 = 注解缺省 required 语义（未显式 `required=false` 即必填）；
4. **HITL 红线**：`request_clarification` 必须带 `@Tool(externalTool=true)`；
5. **示例可读**：每个工具必须至少一行调用示例（人工评审入口，CI 不解析内容）。

> 解析约束：`@ToolParam` 的 description 不得含 ASCII 右括号 `)`（中文括号不受限），
> 否则守护测试的参数区间解析失准。

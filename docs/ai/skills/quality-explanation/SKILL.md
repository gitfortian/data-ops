# 历史质量解释

说明：解释所选质量执行结果，保留历史事实并给出有依据的下一步。

确认固定 qualityExecutionNo，读取 get_quality_execution_evidence。说明该次执行的状态、规则结果和可获得的指标，引用真实 evidenceId。当前规则定义不能替代历史执行时的事实。

结论分为执行事实、可能原因、缺失证据。没有失败样本或上游变更证据时不确认根因。业务阈值或范围不明时 request_clarification，不自行补设阈值。

本任务不取 Dataset 行、不运行 Python、不读当前 monitor 替代旧 execution，不生成规则、保存或重新运行。需要调整规则时指引用户回到原监控页面以新任务生成候选；真实排查证据不足时明确说明限制。

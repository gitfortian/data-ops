export default {
  'pages.dataDevelopment.execution.failureReason': '失败原因',
  'pages.dataDevelopment.execution.failure.validation':
    '当前编辑器定义未通过运行前校验，请修正配置或内容后重新运行。',
  'pages.dataDevelopment.execution.failure.runtimeSubmit':
    '运行定义已进入 Data Development，但提交到任务运行时失败，可检查运行时状态后重试。',
  'pages.dataDevelopment.execution.failure.task':
    '任务运行时已接管本次执行，但任务自身执行失败。',
  'pages.dataDevelopment.execution.failure.timeout':
    '任务超过允许的运行时间，运行时已将本次执行收敛为超时。',
  'pages.dataDevelopment.execution.failure.runtimeNotAttached':
    '执行记录已创建，但运行时未在接管窗口内绑定本次执行，可能发生了提交中断或服务重启。',
  'pages.dataDevelopment.execution.failure.runtimeStateLost':
    'Data Development 仍保留执行记录，但共享运行时已无法提供该运行实例的状态；本次执行已停止显示为运行中。',
  'pages.dataDevelopment.execution.preflight.sqlContentRequired':
    'SQL 编辑器内容为空，请先输入要运行的 SQL。',
  'pages.dataDevelopment.execution.preflight.configInvalid':
    '当前运行配置不是有效的 JSON，请修正配置后再运行。',
  'pages.dataDevelopment.execution.preflight.sqlDataSourceRequired':
    'SQL 运行前需要先选择数据源。',
  'pages.dataDevelopment.execution.endTime': '结束时间',
  'pages.dataDevelopment.execution.result': '结果',
  'pages.dataDevelopment.execution.logs': '日志',
  'pages.dataDevelopment.execution.rawOutput': '原始 Output',
  'pages.dataDevelopment.execution.definitionSnapshot': '运行定义快照',
  'pages.dataDevelopment.execution.definitionSnapshotHint':
    '这里展示的是本次 Execution 创建时固化的编辑器定义。Retry 会复用这份 content / config / schemaVersion，不会读取当前编辑器内容。',
  'pages.dataDevelopment.execution.retryChain': 'Retry 链',
  'pages.dataDevelopment.execution.originalExecution': '原始运行',
  'pages.dataDevelopment.execution.retrySource': '重试来源',
  'pages.dataDevelopment.execution.noResult': '本次运行没有可结构化展示的结果。',
  'pages.dataDevelopment.execution.noLogs': '运行时没有持久化可展示的日志；可查看原始 Output 作为执行证据。',
  'pages.dataDevelopment.execution.noOutput': '本次运行没有持久化 Output。',
  'pages.dataDevelopment.execution.returnedRows': '返回 {count} 行',
  'pages.dataDevelopment.execution.affectedRows': '影响 {count} 行',
  'pages.dataDevelopment.execution.truncated': '结果已截断',
  'pages.dataDevelopment.execution.state.active':
    '本次执行仍在运行中，详情会自动刷新直到 durable execution 收敛到终态。',
  'pages.dataDevelopment.execution.state.cancelled':
    '本次执行已被取消。取消是独立终态，不等同于任务失败。',
  'pages.dataDevelopment.execution.cancel': '取消运行',
  'pages.dataDevelopment.execution.retry': '重试',
  'pages.dataDevelopment.execution.cancelConfirmTitle': '取消这次运行？',
  'pages.dataDevelopment.execution.cancelConfirmContent':
    '只取消当前 Execution，不修改编辑器内容或已发布版本。',
  'pages.dataDevelopment.execution.retryConfirmTitle': '按这次运行的定义重试？',
  'pages.dataDevelopment.execution.retryConfirmContent':
    'Retry 将复用该 Execution 持久化的 content、config 和 schemaVersion，并创建新的 durable Execution；不会读取当前编辑器内容。',
  'pages.dataDevelopment.execution.cancelledSuccess': '已提交取消并刷新执行状态。',
  'pages.dataDevelopment.execution.retriedSuccess': '已创建新的 Retry Execution。',
  'pages.dataDevelopment.execution.actionFailed': '执行操作失败，请刷新后重试。',
};

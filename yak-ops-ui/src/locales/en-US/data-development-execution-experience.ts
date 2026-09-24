export default {
  'pages.dataDevelopment.execution.failureReason': 'Failure reason',
  'pages.dataDevelopment.execution.failure.validation':
    'The current editor definition did not pass run validation. Fix the content or configuration and run it again.',
  'pages.dataDevelopment.execution.failure.runtimeSubmit':
    'Data Development created the execution, but submission to the task runtime failed. Check runtime availability before retrying.',
  'pages.dataDevelopment.execution.failure.task':
    'The task runtime accepted this execution, but the task itself failed.',
  'pages.dataDevelopment.execution.failure.timeout':
    'The task exceeded its allowed runtime and was finalized as timed out.',
  'pages.dataDevelopment.execution.failure.runtimeNotAttached':
    'The durable execution was created, but no runtime execution attached within the handoff window. Submission may have been interrupted or the service restarted.',
  'pages.dataDevelopment.execution.failure.runtimeStateLost':
    'Data Development still has the durable execution record, but the shared runtime can no longer provide this runtime state. The execution is no longer shown as running.',
  'pages.dataDevelopment.execution.preflight.sqlContentRequired':
    'The SQL editor is empty. Enter the SQL you want to run first.',
  'pages.dataDevelopment.execution.preflight.configInvalid':
    'The current run configuration is not valid JSON. Fix it before running.',
  'pages.dataDevelopment.execution.preflight.sqlDataSourceRequired':
    'Select a data source before running SQL.',
  'pages.dataDevelopment.execution.endTime': 'End time',
  'pages.dataDevelopment.execution.result': 'Result',
  'pages.dataDevelopment.execution.logs': 'Logs',
  'pages.dataDevelopment.execution.rawOutput': 'Raw output',
  'pages.dataDevelopment.execution.definitionSnapshot': 'Run definition snapshot',
  'pages.dataDevelopment.execution.definitionSnapshotHint':
    'This is the editor definition persisted when this Execution was created. Retry reuses this content, config and schemaVersion instead of reading the current editor.',
  'pages.dataDevelopment.execution.retryChain': 'Retry chain',
  'pages.dataDevelopment.execution.originalExecution': 'Original execution',
  'pages.dataDevelopment.execution.retrySource': 'Retry source',
  'pages.dataDevelopment.execution.noResult': 'This run has no structured result to display.',
  'pages.dataDevelopment.execution.noLogs': 'The runtime did not persist displayable logs. Raw output remains available as execution evidence.',
  'pages.dataDevelopment.execution.noOutput': 'This run has no persisted output.',
  'pages.dataDevelopment.execution.returnedRows': '{count} rows returned',
  'pages.dataDevelopment.execution.affectedRows': '{count} rows affected',
  'pages.dataDevelopment.execution.truncated': 'Result truncated',
  'pages.dataDevelopment.execution.state.active':
    'This execution is still active. Detail refreshes automatically until the durable execution converges to a terminal state.',
  'pages.dataDevelopment.execution.state.cancelled':
    'This execution was cancelled. Cancellation is a distinct terminal state and is not treated as task failure.',
  'pages.dataDevelopment.execution.cancel': 'Cancel run',
  'pages.dataDevelopment.execution.retry': 'Retry',
  'pages.dataDevelopment.execution.cancelConfirmTitle': 'Cancel this run?',
  'pages.dataDevelopment.execution.cancelConfirmContent':
    'Only this Execution is cancelled. Editor content and published revisions are not changed.',
  'pages.dataDevelopment.execution.retryConfirmTitle': 'Retry this persisted run definition?',
  'pages.dataDevelopment.execution.retryConfirmContent':
    'Retry reuses the content, config and schemaVersion persisted on this Execution and creates a new durable Execution. It does not read the current editor.',
  'pages.dataDevelopment.execution.cancelledSuccess': 'Cancellation submitted and execution state refreshed.',
  'pages.dataDevelopment.execution.retriedSuccess': 'A new Retry Execution was created.',
  'pages.dataDevelopment.execution.actionFailed': 'The execution action failed. Refresh and try again.',
};

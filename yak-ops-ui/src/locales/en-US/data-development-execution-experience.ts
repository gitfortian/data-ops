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
};

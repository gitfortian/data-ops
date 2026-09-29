package io.yak.ops.business.audit;

/** Stable business-level events. Keep this vocabulary intentionally small and technology agnostic. */
public enum AuditEventType {
  OPERATION_STARTED,
  AUTHORIZATION_DECISION,
  RESOURCE_CREATED,
  RESOURCE_UPDATED,
  RESOURCE_DELETED,
  TASK_SUBMITTED,
  TASK_QUEUED,
  WORKER_STARTED,
  TASK_SUCCEEDED,
  TASK_FAILED,
  TASK_CANCELED,
  OPERATION_SUCCEEDED,
  OPERATION_FAILED
}

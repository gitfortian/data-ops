package io.yak.ops.business.workflow.domain;

/** 工作流实例的启动来源。 */
public enum WorkflowTriggerType {
  MANUAL,
  SCHEDULE,
  BACKFILL,
  RERUN,
  API
}

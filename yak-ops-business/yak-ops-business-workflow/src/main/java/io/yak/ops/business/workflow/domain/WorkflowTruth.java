package io.yak.ops.business.workflow.domain;

/**
 * Names the durable owners of workflow facts. Runtime execution state is owned by Yak Workflow
 * Engine; Yak Ops owns business definition/version/trigger/backfill metadata around that runtime.
 */
public enum WorkflowTruth {
  DEFINITION,
  VERSION,
  EXECUTION_METADATA,
  ENGINE_EXECUTION,
  SCHEDULE,
  TRIGGER,
  BACKFILL
}

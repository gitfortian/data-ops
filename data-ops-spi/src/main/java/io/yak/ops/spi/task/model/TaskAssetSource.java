package io.yak.ops.spi.task.model;

/** Platform module that owns the source task before it is published to the task catalog. */
public enum TaskAssetSource {
  DATA_DEVELOPMENT,
  DATA_INTEGRATION,
  DATA_QUALITY
}

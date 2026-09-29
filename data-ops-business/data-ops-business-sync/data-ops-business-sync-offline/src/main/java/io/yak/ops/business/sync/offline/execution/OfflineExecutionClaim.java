package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;

/** Claim 阶段冻结后交给 Coordinator 的执行证据。 */
public final class OfflineExecutionClaim {

  private final OfflineJobDefinition definition;
  private final String logicalJobSpecJson;
  private final OfflineJobExecution execution;
  private final boolean reused;

  public OfflineExecutionClaim(
      OfflineJobDefinition definition,
      String logicalJobSpecJson,
      OfflineJobExecution execution) {
    this(definition, logicalJobSpecJson, execution, false);
  }

  public OfflineExecutionClaim(
      OfflineJobDefinition definition,
      String logicalJobSpecJson,
      OfflineJobExecution execution,
      boolean reused) {
    this.definition = definition;
    this.logicalJobSpecJson = logicalJobSpecJson;
    this.execution = execution;
    this.reused = reused;
  }

  public OfflineJobDefinition getDefinition() {
    return definition;
  }

  public String getLogicalJobSpecJson() {
    return logicalJobSpecJson;
  }

  public OfflineJobExecution getExecution() {
    return execution;
  }

  public boolean isReused() {
    return reused;
  }
}

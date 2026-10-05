package io.yak.ops.business.agent.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-invocation state, propagated explicitly rather than in ThreadLocal business state. */
public final class AgentExecutionContext {
  public static final String PROJECT_ID = "yak.projectId";
  private final GovernanceTarget target;
  private final GovernanceEvidenceLedger evidence = new GovernanceEvidenceLedger();
  private final Map<Long, DatasetSummary.DatasetFields> discoveries = new ConcurrentHashMap<>();

  public AgentExecutionContext(GovernanceTarget target) { this.target = target; }
  public GovernanceTarget target() { return target; }
  public GovernanceEvidenceLedger evidence() { return evidence; }
  public void remember(DatasetSummary.DatasetFields fields) { discoveries.put(fields.datasetId(), fields); }
  public DatasetSummary.DatasetFields discovery(long datasetId) { return discoveries.get(datasetId); }
  public DatasetSummary.DatasetFields requireDiscovery(long datasetId) {
    var fields = discoveries.get(datasetId);
    if (fields == null || fields.versionNo() == null) {
      throw new IllegalArgumentException("[DATASET_DISCOVERY_REQUIRED] 请先调用 get_dataset_fields 确认本轮版本和字段");
    }
    return fields;
  }
}

package io.yak.ops.business.agent.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-invocation state, propagated explicitly rather than in ThreadLocal business state. */
public final class AgentExecutionContext {
  public static final String PROJECT_ID = "yak.projectId";
  private final GovernanceTarget target;
  private volatile String qualityDefinition;
  private volatile GovernanceSuggestion suggestion;
  private final java.util.concurrent.atomic.AtomicInteger suggestionAttempts = new java.util.concurrent.atomic.AtomicInteger();
  public void beginSuggestionAttempt() {
    suggestion = null;
    if (suggestionAttempts.incrementAndGet() > 3) {
      throw new IllegalArgumentException("候选校验次数已达上限，请补充业务条件后重新发起");
    }
  }
  private volatile java.util.List<GovernanceVerifiedFact> verifiedFacts = java.util.List.of();
  public java.util.List<GovernanceVerifiedFact> verifiedFacts() { return verifiedFacts; }
  public void verifiedFacts(java.util.List<GovernanceVerifiedFact> facts) { verifiedFacts = java.util.List.copyOf(facts); }
  public String qualityDefinition() { return qualityDefinition; }
  public void qualityDefinition(String value) { qualityDefinition = value; }
  public GovernanceSuggestion suggestion() { return suggestion; }
  public void suggestion(GovernanceSuggestion value) { suggestion = value; }
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

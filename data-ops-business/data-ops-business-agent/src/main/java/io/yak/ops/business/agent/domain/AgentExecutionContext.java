package io.yak.ops.business.agent.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-invocation state, propagated explicitly rather than in ThreadLocal business state. */
public final class AgentExecutionContext {
  public static final String PROJECT_ID = "yak.projectId";
  private final GovernanceTarget target;
  private final AgentTaskToolPolicy toolPolicy;
  private ToolBudgetSnapshot toolBudget = new ToolBudgetSnapshot(32, 3, 0, Map.of());
  private java.util.function.Consumer<ToolBudgetSnapshot> budgetCheckpoint = snapshot -> {};
  private boolean stopped;

  public synchronized void configureBudget(ToolBudgetSnapshot snapshot,
      java.util.function.Consumer<ToolBudgetSnapshot> checkpoint) {
    this.toolBudget = snapshot;
    this.budgetCheckpoint = checkpoint;
  }

  public synchronized ToolBudgetSnapshot toolBudget() { return toolBudget; }
  public AgentTaskToolPolicy toolPolicy() { return toolPolicy; }
  public synchronized void stopTools() { stopped = true; }
  public synchronized void requireTool(String name) {
    if (stopped) throw new IllegalStateException("[TOOL_EXECUTION_STOPPED] 本次执行已停止");
    toolPolicy.require(name);
    if (toolBudget.failures().getOrDefault(name, 0) >= toolBudget.maxFailuresPerTool()) {
      throw new IllegalStateException("[TOOL_FAILURE_LIMIT] 此工具累计失败已达上限，请检查来源或补充条件后发起新任务");
    }
  }

  public synchronized void reserveTool(String name) {
    requireTool(name);
    if (toolBudget.usedCalls() >= toolBudget.maxCalls()) {
      throw new IllegalStateException("[TOOL_CALL_LIMIT] 本轮工具调用已达上限，请缩小问题范围后重新发起");
    }
    checkpoint(new ToolBudgetSnapshot(toolBudget.maxCalls(), toolBudget.maxFailuresPerTool(),
        toolBudget.usedCalls() + 1, toolBudget.failures()));
  }

  public synchronized void toolFailed(String name) {
    if (stopped) return; // A late callback cannot overwrite a newer turn's StateStore slot after cancellation.
    var failures = new java.util.HashMap<>(toolBudget.failures());
    failures.merge(name, 1, Integer::sum);
    checkpoint(new ToolBudgetSnapshot(toolBudget.maxCalls(), toolBudget.maxFailuresPerTool(),
        toolBudget.usedCalls(), failures));
  }

  private void checkpoint(ToolBudgetSnapshot next) {
    try {
      budgetCheckpoint.accept(next);
      toolBudget = next;
    } catch (RuntimeException failure) {
      stopped = true;
      throw new IllegalStateException("[TOOL_BUDGET_UNAVAILABLE] 无法保存执行预算，已停止后续工具调用");
    }
  }
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

  public AgentExecutionContext(GovernanceTarget target) { this(target, false); }

  public AgentExecutionContext(GovernanceTarget target, boolean sourceReadOnly) {
    this.target = target;
    this.toolPolicy = new AgentTaskToolPolicy(target, sourceReadOnly);
  }
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

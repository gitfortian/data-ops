package io.yak.ops.business.agent.domain;

import java.util.Set;

/** Execution scope is independent of source authorization and never comes from model instructions. */
public final class AgentTaskToolPolicy {
  public static final String VERSION = "F-025-v1";
  public static final Set<String> REGISTERED = Set.of("list_datasets", "get_dataset_fields",
      "run_dataset_query", "current_date_info", "analyze_with_python", "request_clarification",
      "save_analysis_report", "search_assets", "get_asset_evidence", "get_asset_section_evidence",
      "get_quality_execution_evidence", "get_quality_monitor_evidence", "propose_quality_rules",
      "propose_asset_description", "verify_governance_facts", "load_skill_through_path");
  private static final Set<String> AUXILIARY = Set.of("current_date_info", "request_clarification",
      "verify_governance_facts", "load_skill_through_path");
  private final GovernanceTarget target;

  public AgentTaskToolPolicy(GovernanceTarget target) { this.target = target; }

  public boolean allows(String tool) {
    if (target != null && target.metricExplanation() != null) {
      return "load_skill_through_path".equals(tool) || "generate_response".equals(tool)
          || "get_metric_caliber_context".equals(tool);
    }
    if (target != null && target.standardMatch() != null) {
      return "load_skill_through_path".equals(tool) || "generate_response".equals(tool)
          || "get_standard_match_context".equals(tool);
    }
    if (target != null && target.modelMapping() != null) {
      return "load_skill_through_path".equals(tool) || "generate_response".equals(tool)
          || "get_model_mapping_context".equals(tool);
    }
    if (tool == null || !REGISTERED.contains(tool)) return false;
    if (target == null) return !tool.startsWith("propose_");
    if (AUXILIARY.contains(tool)) return true;
    if (target.assetId() != null) {
      return "get_asset_evidence".equals(tool) || "get_asset_section_evidence".equals(tool)
          || ("ASSET_DESCRIPTION".equals(target.purpose()) && "propose_asset_description".equals(tool));
    }
    if (target.qualityExecutionNo() != null) return "get_quality_execution_evidence".equals(tool);
    return "get_quality_monitor_evidence".equals(tool) || "propose_quality_rules".equals(tool);
  }

  public void require(String tool) {
    if (tool == null || !allows(tool)) {
      throw new IllegalArgumentException("[TOOL_NOT_ALLOWED] 当前任务不允许此工具，请在对应原页面发起合适的任务");
    }
  }

  public void requireAsset(long assetId) {
    if (target != null && (target.assetId() == null || target.assetId() != assetId)) rejectTarget();
  }

  public void requireMonitor(long monitorId) {
    if (target != null && (target.qualityMonitorId() == null || target.qualityMonitorId() != monitorId)) rejectTarget();
  }

  public void requireExecution(String executionNo) {
    if (target != null && !java.util.Objects.equals(executionNo, target.qualityExecutionNo())) rejectTarget();
  }

  private static void rejectTarget() {
    throw new IllegalArgumentException("[TASK_TARGET_MISMATCH] 工具目标与当前任务不一致，请重新发起任务");
  }
}

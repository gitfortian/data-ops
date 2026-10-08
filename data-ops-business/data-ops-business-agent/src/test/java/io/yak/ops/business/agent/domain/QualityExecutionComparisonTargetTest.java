package io.yak.ops.business.agent.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QualityExecutionComparisonTargetTest {
  private final ObjectMapper json = new ObjectMapper();
  @Test void legacyJsonAndSelectedPairRoundtripWithoutChangingScope() throws Exception {
    var old = json.readValue("{\"qualityExecutionNo\":\"after\"}", GovernanceTarget.class);
    assertNull(old.qualityBaselineExecutionNo());
    assertTrue(new AgentTaskToolPolicy(old).allows("get_quality_execution_evidence"));
    assertFalse(new AgentTaskToolPolicy(old).allows("get_quality_execution_comparison"));
    var target = json.readValue("{\"qualityExecutionNo\":\"after\",\"qualityBaselineExecutionNo\":\"before\"}", GovernanceTarget.class);
    assertEquals(target, json.readValue(json.writeValueAsString(target), GovernanceTarget.class));
    var policy = new AgentTaskToolPolicy(target);
    assertTrue(policy.allows("get_quality_execution_comparison")); assertTrue(policy.allows("request_clarification"));
    assertTrue(policy.allows("verify_governance_facts"));
    for (String tool : java.util.List.of("get_quality_execution_evidence", "get_quality_monitor_evidence", "propose_quality_rules",
        "run_dataset_query", "analyze_with_python", "save_analysis_report", "get_asset_evidence")) assertFalse(policy.allows(tool), tool);
    assertFalse(new AgentTaskToolPolicy(null).allows("get_quality_execution_comparison"));
  }
  @Test void rejectsMalformedOrMixedPairs() {
    for (String value : java.util.List.of("{\"qualityBaselineExecutionNo\":\"before\"}",
        "{\"assetId\":7,\"qualityBaselineExecutionNo\":\"before\"}",
        "{\"qualityExecutionNo\":\"after\",\"qualityBaselineExecutionNo\":\"after\"}",
        "{\"qualityExecutionNo\":\"after\",\"qualityBaselineExecutionNo\":\"../before\"}",
        "{\"qualityExecutionNo\":\"after\",\"qualityBaselineExecutionNo\":\"before\",\"purpose\":\"QUALITY_RULES\"}")) {
      assertThrows(Exception.class, () -> json.readValue(value, GovernanceTarget.class), value);
    }
  }
}

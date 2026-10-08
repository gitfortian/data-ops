package io.yak.ops.business.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssetImpactTargetTest {
  @Test void targetRoundtripsAndOnlyFixedSummaryAndAuxiliaryToolsAreAllowed() throws Exception {
    var json = new ObjectMapper();
    var target = json.readValue("{\"assetId\":7,\"purpose\":\"ASSET_IMPACT\"}", GovernanceTarget.class);
    assertEquals(target, json.readValue(json.writeValueAsString(target), GovernanceTarget.class));
    var policy = new AgentTaskToolPolicy(target);
    for (String tool : AgentTaskToolPolicy.REGISTERED) {
      assertEquals(java.util.Set.of("get_asset_impact_evidence", "current_date_info", "request_clarification",
          "verify_governance_facts", "load_skill_through_path").contains(tool), policy.allows(tool), tool);
    }
    assertFalse(new AgentTaskToolPolicy(null).allows("get_asset_impact_evidence"));
    assertFalse(new AgentTaskToolPolicy(new GovernanceTarget(7L, null)).allows("get_asset_impact_evidence"));
    assertThrows(IllegalArgumentException.class, () -> policy.requireAsset(8));
    for (String body : java.util.List.of("{\"purpose\":\"ASSET_IMPACT\"}",
        "{\"qualityExecutionNo\":\"Q1\",\"purpose\":\"ASSET_IMPACT\"}", "{\"assetId\":0,\"purpose\":\"ASSET_IMPACT\"}")) {
      assertThrows(Exception.class, () -> json.readValue(body, GovernanceTarget.class));
    }
  }
}

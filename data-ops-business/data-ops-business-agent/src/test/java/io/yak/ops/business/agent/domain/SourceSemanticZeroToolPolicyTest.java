package io.yak.ops.business.agent.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SourceSemanticZeroToolPolicyTest {
  @Test void readOnlyModeDeniesAllBusinessSourceSqlAndDynamicTools() {
    var readOnly = new AgentExecutionContext(null, true);
    for (String tool : new String[] {
        "run_dataset_query", "analyze_with_python", "save_analysis_report",
        "load_skill_through_path", "request_clarification",
        "get_asset_evidence", "generate_response"}) {
      assertFalse(readOnly.toolPolicy().allows(tool), tool);
      assertThrows(IllegalArgumentException.class, () -> readOnly.requireTool(tool));
    }
    assertFalse(readOnly.toolPolicy().allows("some_newly_registered_sdk_tool"));
    assertTrue(new AgentExecutionContext(null).toolPolicy().allows("current_date_info"));
  }
}

package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigManageServiceTest {
  @Test void unwiredKeysAreRejectedByManagementApiBeforeDatabaseWrite() {
    var dynamic = mock(AgentDynamicConfigService.class);
    var service = new AgentConfigManageService(dynamic, new AgentProperties());
    assertThrows(IllegalArgumentException.class, () -> service.update(AgentDynamicConfigService.KEY_LLM_MAX_ITERS, "40"));
    assertThrows(IllegalArgumentException.class, () -> service.update(AgentDynamicConfigService.KEY_APPROVAL_QUERY_EXECUTION, "true"));
    verifyNoInteractions(dynamic);
  }

  @Test void reportsStartupOverrideOverflowAndUnwiredValuesFromOneSnapshot() {
    var config = mock(AgentDynamicConfigService.class);
    var properties = new AgentProperties();
    properties.getMemory().setEnabled(false);
    properties.getChat().setLlmCallTimeoutSeconds(45);
    when(config.listRegistered()).thenReturn(List.of(
        new AgentDynamicConfigService.RegisteredKeyValue(AgentDynamicConfigService.KEY_MEMORY_ENABLED, "bool", "", null),
        new AgentDynamicConfigService.RegisteredKeyValue(AgentDynamicConfigService.KEY_OBSERVABILITY_ENABLED, "bool", "", " true "),
        new AgentDynamicConfigService.RegisteredKeyValue(AgentDynamicConfigService.KEY_LLM_TIMEOUT_SECONDS, "int", "", "999999999999999"),
        new AgentDynamicConfigService.RegisteredKeyValue(AgentDynamicConfigService.KEY_LLM_MAX_ITERS, "int", "", "20")));
    var values = new AgentConfigManageService(config, properties).list();
    assertEquals("false", values.get(0).effectiveValue());
    assertEquals("STARTUP", values.get(0).valueSource());
    assertEquals("true", values.get(1).effectiveValue());
    assertEquals("DYNAMIC", values.get(1).valueSource());
    assertEquals("45", values.get(2).effectiveValue());
    assertEquals("STARTUP", values.get(2).valueSource());
    assertNull(values.get(3).effectiveValue());
    assertEquals("NOT_CONNECTED", values.get(3).updateMode());
    verify(config, never()).lookupInt(anyString(), anyInt());
  }
}

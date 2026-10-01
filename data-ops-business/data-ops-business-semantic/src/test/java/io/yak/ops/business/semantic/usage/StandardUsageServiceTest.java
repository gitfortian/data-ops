package io.yak.ops.business.semantic.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.semantic.api.StandardUsageApi;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardUsageMapper;
import io.yak.ops.common.bean.po.semantic.SemanticStandardUsagePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 引用统计单元测试:事件落库、服务端聚合、反哺建议阈值。 */
class StandardUsageServiceTest {

  private SemanticStandardUsageMapper mapper;
  private StandardUsageService service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(SemanticStandardUsageMapper.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(1L, "p"));
    service = new StandardUsageService(mapper, currentProject);
    Mockito.when(mapper.selectSummary(Mockito.anyLong(), Mockito.anyLong())).thenReturn(Map.of());
  }

  @Test
  void recordInsertsProjectScopedEvent() {
    service.record(new StandardUsageApi.UsageEvent(5L, "APPLY", "EDITOR", "77", "tester"));
    ArgumentCaptor<SemanticStandardUsagePO> captor =
        ArgumentCaptor.forClass(SemanticStandardUsagePO.class);
    Mockito.verify(mapper).insert(captor.capture());
    assertEquals(1L, captor.getValue().getProjectId());
    assertEquals(5L, captor.getValue().getStandardId());
    assertEquals("APPLY", captor.getValue().getUsageType());
  }

  @Test
  void summaryAggregatesByTypeServerSide() {
    SemanticStandardUsagePO apply = new SemanticStandardUsagePO();
    apply.setUsageType("APPLY");
    SemanticStandardUsagePO bypass = new SemanticStandardUsagePO();
    bypass.setUsageType("BYPASS");
    SemanticStandardUsagePO bypass2 = new SemanticStandardUsagePO();
    bypass2.setUsageType("BYPASS");
    Mockito.when(mapper.selectSummary(1L, 5L)).thenReturn(Map.of("applyCount", 1L, "bypassCount", 2L));

    StandardUsageApi.UsageSummary summary = service.summary(5L);
    assertEquals(1, summary.applyCount());
    assertEquals(2, summary.bypassCount());
    assertFalse(summary.suspicious());
  }

  @Test
  void suspiciousWhenBypassDominant() {
    SemanticStandardUsagePO bypass1 = new SemanticStandardUsagePO();
    bypass1.setUsageType("BYPASS");
    SemanticStandardUsagePO bypass2 = new SemanticStandardUsagePO();
    bypass2.setUsageType("BYPASS");
    SemanticStandardUsagePO bypass3 = new SemanticStandardUsagePO();
    bypass3.setUsageType("BYPASS");
    SemanticStandardUsagePO apply = new SemanticStandardUsagePO();
    apply.setUsageType("APPLY");
    Mockito.when(mapper.selectSummary(1L, 5L)).thenReturn(Map.of("applyCount", 1L, "bypassCount", 3L));

    assertTrue(service.summary(5L).suspicious());
  }
}

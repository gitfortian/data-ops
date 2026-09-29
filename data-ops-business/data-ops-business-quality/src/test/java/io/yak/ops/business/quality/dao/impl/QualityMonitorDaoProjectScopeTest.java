package io.yak.ops.business.quality.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.dao.mapper.QualityAlertEventMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorSettingMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTableAssetMapper;
import io.yak.ops.business.quality.dao.mapper.QualityWriteMapper;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QualityMonitorDaoProjectScopeTest {

  @Test
  void complexQueriesReceiveTrustedProjectId() {
    Fixture fixture = fixture(7L);
    when(fixture.queryMapper.countMonitors(any())).thenReturn(3L);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("keyword", "%orders%");

    assertThat(fixture.dao.countMonitors(params)).isEqualTo(3L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(fixture.queryMapper).countMonitors(captor.capture());
    assertThat(captor.getValue())
        .containsEntry("projectId", 7L)
        .containsEntry("keyword", "%orders%");
    assertThat(params).doesNotContainKey("projectId");
  }

  @Test
  void monitorInsertAlwaysUsesTrustedProject() {
    Fixture fixture = fixture(7L);
    doAnswer(
            invocation -> {
              QualityMonitorPO row = invocation.getArgument(0);
              row.setId(42L);
              return 1;
            })
        .when(fixture.monitorMapper)
        .insert(any(QualityMonitorPO.class));
    QualityMonitorPO monitor = new QualityMonitorPO();

    assertThat(fixture.dao.insertMonitor(monitor)).isEqualTo(42L);
    assertThat(monitor.getProjectId()).isEqualTo(7L);
  }

  @Test
  void callerCannotOverrideMonitorProject() {
    Fixture fixture = fixture(7L);
    QualityMonitorPO monitor = new QualityMonitorPO();
    monitor.setProjectId(8L);

    assertThatThrownBy(() -> fixture.dao.insertMonitor(monitor))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void monitorDetailUsesProjectAndIdTogether() {
    Fixture fixture = fixture(7L);

    fixture.dao.selectMonitor(42L);

    verify(fixture.queryMapper).selectMonitor(7L, 42L);
  }

  private Fixture fixture(long projectId) {
    QualityQueryMapper queryMapper = mock(QualityQueryMapper.class);
    QualityWriteMapper writeMapper = mock(QualityWriteMapper.class);
    QualityMonitorMapper monitorMapper = mock(QualityMonitorMapper.class);
    QualityRuleMapper ruleMapper = mock(QualityRuleMapper.class);
    QualityMonitorSettingMapper settingMapper = mock(QualityMonitorSettingMapper.class);
    QualityTableAssetMapper tableAssetMapper = mock(QualityTableAssetMapper.class);
    QualityAlertEventMapper alertMapper = mock(QualityAlertEventMapper.class);
    CurrentProject currentProject =
        () -> Optional.of(new ProjectContext(projectId, "test-project"));
    return new Fixture(
        new QualityMonitorDaoImpl(
            queryMapper,
            writeMapper,
            monitorMapper,
            ruleMapper,
            settingMapper,
            tableAssetMapper,
            alertMapper,
            currentProject),
        queryMapper,
        monitorMapper);
  }

  private record Fixture(
      QualityMonitorDaoImpl dao,
      QualityQueryMapper queryMapper,
      QualityMonitorMapper monitorMapper) {}
}

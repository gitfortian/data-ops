package io.yak.ops.business.datasource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceRepositoryAdapterTest {

  @Mock private DataSourceDao dao;

  @Test
  void mapsPersistenceRowToDomainIncludingRuntimeConnectionData() {
    DataSourcePO po = new DataSourcePO();
    po.setId(42L);
    po.setProjectId(7L);
    po.setName("orders-db");
    po.setDbType(DataSourceDbType.MYSQL);
    po.setEnvironment(DataSourceEnvironment.PROD);
    po.setConnStatus(DataSourceConnStatus.CONNECTED);
    po.setConnectionParams("{\"host\":\"127.0.0.1\"}");
    po.setOriginalJson("{\"host\":\"127.0.0.1\"}");
    CurrentProject currentProject = project(7L);
    when(dao.selectById(7L, 42L)).thenReturn(po);

    DataSourceDefinition result =
        new DataSourceRepositoryAdapter(dao, currentProject).findById(42L).orElseThrow();

    assertThat(result.getId()).isEqualTo(42L);
    assertThat(result.getProjectId()).isEqualTo(7L);
    assertThat(result.getDbType()).isEqualTo(DataSourceDbType.MYSQL);
    assertThat(result.getEnvironment()).isEqualTo(DataSourceEnvironment.PROD);
    assertThat(result.getConnStatus()).isEqualTo(DataSourceConnStatus.CONNECTED);
    assertThat(result.getConnectionParams()).contains("127.0.0.1");
  }

  @Test
  void usesProjectQualifiedLookupWhenContextIsBound() {
    DataSourcePO po = new DataSourcePO();
    po.setId(42L);
    po.setProjectId(7L);
    po.setName("orders-db");
    CurrentProject currentProject = project(7L);
    when(dao.selectById(7L, 42L)).thenReturn(po);

    DataSourceDefinition result =
        new DataSourceRepositoryAdapter(dao, currentProject).findById(42L).orElseThrow();

    assertThat(result.getProjectId()).isEqualTo(7L);
    verify(dao).selectById(7L, 42L);
  }

  @Test
  void mapsProjectQualifiedSummaryProjectionToDomain() {
    DataSourceSummaryRow row = new DataSourceSummaryRow();
    row.setTotal(8L);
    row.setConnected(5L);
    row.setDisconnected(2L);
    row.setUnknown(1L);
    row.setEnvironmentCount(3L);
    when(dao.selectSummary(7L)).thenReturn(row);

    DataSourceSummary result = new DataSourceRepositoryAdapter(dao, project(7L)).summary();

    assertThat(result.total()).isEqualTo(8L);
    assertThat(result.connected()).isEqualTo(5L);
    assertThat(result.disconnected()).isEqualTo(2L);
    assertThat(result.unknown()).isEqualTo(1L);
    assertThat(result.environmentCount()).isEqualTo(3L);
    verify(dao).selectSummary(7L);
  }

  @Test
  void insertBackfillsGeneratedIdSoCreateCommandCanPublishOutwardEvent() {
    when(dao.addDataSource(any(DataSourcePO.class)))
        .thenAnswer(
            invocation -> {
              invocation.<DataSourcePO>getArgument(0).setId(42L);
              return 1;
            });
    DataSourceDefinition definition =
        DataSourceDefinition.create(
            "orders-db",
            DataSourceDbType.MYSQL,
            new ConnectionProfile("jdbc:mysql://127.0.0.1/orders", "{}", "{}"),
            DataSourceEnvironment.PROD,
            null);

    assertThat(new DataSourceRepositoryAdapter(dao, project(7L)).insert(definition)).isTrue();
    assertThat(definition.getId()).isEqualTo(42L);
  }

  @Test
  void rejectsBusinessReadsWithoutCurrentProject() {
    assertThatThrownBy(() -> new DataSourceRepositoryAdapter(dao).findById(42L))
        .isInstanceOf(ProjectContextException.class);
  }

  @Test
  void healthProbeProjectScanNeedsNoProjectContextByDesign() {
    when(dao.selectDistinctProjectIds()).thenReturn(List.of(7L, 8L));

    assertThat(new DataSourceRepositoryAdapter(dao).distinctProjectIds())
        .containsExactly(7L, 8L);
  }

  @Test
  void credentialBackfillStaysProjectScoped() {
    when(dao.encryptPlainCredentials(7L)).thenReturn(3);

    assertThat(new DataSourceRepositoryAdapter(dao, project(7L)).encryptStoredCredentials())
        .isEqualTo(3);
    assertThatThrownBy(() -> new DataSourceRepositoryAdapter(dao).encryptStoredCredentials())
        .isInstanceOf(ProjectContextException.class);
  }

  private static CurrentProject project(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "Project " + projectId));
  }
}

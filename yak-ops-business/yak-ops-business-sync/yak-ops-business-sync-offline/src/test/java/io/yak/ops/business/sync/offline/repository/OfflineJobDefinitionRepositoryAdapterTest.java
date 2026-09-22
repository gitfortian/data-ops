package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.PROJECT_ID;
import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.currentProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineJobDefinitionRepositoryAdapterTest {

  @Mock private OfflineJobDefinitionDao dao;
  @Mock private DataSourceDao dataSourceDao;

  @Test
  void runtimeReadDoesNotLoadDatasourceDisplayMetadata() {
    OfflineJobDefinitionPO po = definition();
    when(dao.selectById(42L)).thenReturn(po);

    OfflineJobDefinition result =
        repository().findById(42L).orElseThrow();

    assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(result.getSourceDatasourceName()).isNull();
    assertThat(result.getSinkDatasourceName()).isNull();
    verifyNoInteractions(dataSourceDao);
  }

  @Test
  void displayReadEnrichesDatasourceNames() {
    OfflineJobDefinitionPO po = definition();
    DataSourcePO source = dataSource(1L, "source-mysql");
    DataSourcePO sink = dataSource(2L, "sink-mysql");
    when(dao.selectById(42L)).thenReturn(po);
    when(dataSourceDao.selectByIds(List.of(1L, 2L))).thenReturn(List.of(source, sink));

    OfflineJobDefinition result = repository().findForViewById(42L).orElseThrow();

    assertThat(result.getSourceDatasourceName()).isEqualTo("source-mysql");
    assertThat(result.getSinkDatasourceName()).isEqualTo("sink-mysql");
    verify(dataSourceDao).selectByIds(List.of(1L, 2L));
  }

  @Test
  void displayPageLoadsDatasourceNamesInOneBatch() {
    OfflineJobDefinitionPO first = definition();
    OfflineJobDefinitionPO second = definition();
    second.setId(43L);
    second.setSinkDatasourceId(3L);

    Page<OfflineJobDefinitionPO> page = Page.of(1, 10);
    page.setRecords(List.of(first, second));
    page.setTotal(2L);
    when(dao.selectPage(any())).thenReturn(page);
    when(dataSourceDao.selectByIds(List.of(1L, 2L, 3L)))
        .thenReturn(
            List.of(
                dataSource(1L, "source-mysql"),
                dataSource(2L, "sink-mysql"),
                dataSource(3L, "archive-mysql")));

    repository().pageForView(null);

    verify(dataSourceDao).selectByIds(List.of(1L, 2L, 3L));
  }

  @Test
  void displayPageRejectsForeignProjectRowsBeforeDatasourceEnrichment() {
    OfflineJobDefinitionPO foreign = definition();
    foreign.setProjectId(PROJECT_ID + 1L);

    Page<OfflineJobDefinitionPO> page = Page.of(1, 10);
    page.setRecords(List.of(foreign));
    page.setTotal(1L);
    when(dao.selectPage(any())).thenReturn(page);

    assertThatThrownBy(() -> repository().pageForView(null))
        .isInstanceOf(ProjectContextException.class);
    verifyNoInteractions(dataSourceDao);
  }

  private OfflineJobDefinitionRepositoryAdapter repository() {
    return new OfflineJobDefinitionRepositoryAdapter(dao, dataSourceDao, currentProject());
  }

  private OfflineJobDefinitionPO definition() {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    po.setId(42L);
    po.setProjectId(PROJECT_ID);
    po.setJobName("demo");
    po.setSourceDatasourceId(1L);
    po.setSinkDatasourceId(2L);
    return po;
  }

  private DataSourcePO dataSource(Long id, String name) {
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(id);
    dataSource.setName(name);
    return dataSource;
  }
}

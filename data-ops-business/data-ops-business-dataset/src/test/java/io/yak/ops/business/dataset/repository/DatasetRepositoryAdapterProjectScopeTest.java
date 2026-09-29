package io.yak.ops.business.dataset.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.dataset.repository.support.DatasetJsonCodec;
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
class DatasetRepositoryAdapterProjectScopeTest {

  @Mock private DatasetDao datasetDao;
  @Mock private DatasetJsonCodec jsonCodec;

  @Test
  void listUsesCurrentProjectAsRepositoryBoundary() {
    DatasetPO po = new DatasetPO();
    po.setId(11L);
    po.setProjectId(7L);
    po.setName("sales");
    po.setStatus("ONLINE");
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    when(datasetDao.selectDatasets(7L)).thenReturn(List.of(po));

    List<Dataset> result =
        new DatasetRepositoryAdapter(datasetDao, jsonCodec, currentProject).listDatasets();

    assertThat(result).extracting(Dataset::id).containsExactly(11L);
    assertThat(result.get(0).requireProjectId()).isEqualTo(7L);
    verify(datasetDao).selectDatasets(7L);
  }

  @Test
  void inheritedVersionLookupUsesParentProjectBoundary() {
    DatasetVersionPO version = new DatasetVersionPO();
    version.setId(31L);
    version.setDatasetId(11L);
    version.setVersionNo(2);
    version.setSourceType("SQL_QUERY");
    version.setSourceTaskAssetId(0L);
    version.setSourceTaskRevisionId(0L);
    version.setSourceTaskRevisionNo(0);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    when(datasetDao.selectVersion(7L, 31L)).thenReturn(version);

    var resolved =
        new DatasetRepositoryAdapter(datasetDao, jsonCodec, currentProject)
            .findVersion(31L)
            .orElseThrow();

    assertThat(resolved.datasetId()).isEqualTo(11L);
    verify(datasetDao).selectVersion(7L, 31L);
  }

  @Test
  void missingCurrentProjectFailsClosedInsteadOfUsingGlobalDao() {
    DatasetRepositoryAdapter repository = new DatasetRepositoryAdapter(datasetDao, jsonCodec);

    assertThrows(ProjectContextException.class, repository::listDatasets);
  }
}

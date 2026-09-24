package io.yak.ops.business.dataset.development;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.core.project.CurrentProject;
import org.junit.jupiter.api.Test;

class DatasetDevelopmentSourceServiceTest {

  @Test
  void resolvesDevelopmentNodeInsideCurrentProject() {
    DatasetDao dao = mock(DatasetDao.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(23L);
    DatasetPO dataset = new DatasetPO();
    dataset.setId(55L);
    dataset.setProjectId(23L);
    dataset.setDevelopmentNodeId(7L);
    dataset.setCurrentVersionId(99L);
    when(dao.selectDataset(23L, 55L)).thenReturn(dataset);
    DatasetVersionPO version = new DatasetVersionPO();
    version.setId(99L);
    version.setDatasetId(55L);
    version.setVersionNo(3);
    when(dao.selectVersion(23L, 99L)).thenReturn(version);

    var source = new DatasetDevelopmentSourceService(dao, project).require(55L);

    assertEquals("FOUND", source.state());
    assertEquals(55L, source.datasetId());
    assertEquals(7L, source.developmentNodeId());
    assertEquals(3, source.currentDatasetVersionNo());
  }

  @Test
  void nonDevelopmentDatasetIsExplicitlyNotApplicable() {
    DatasetDao dao = mock(DatasetDao.class);
    CurrentProject project = mock(CurrentProject.class);
    when(project.requireProjectId()).thenReturn(23L);
    DatasetPO dataset = new DatasetPO();
    dataset.setId(55L);
    dataset.setProjectId(23L);
    when(dao.selectDataset(23L, 55L)).thenReturn(dataset);

    var source = new DatasetDevelopmentSourceService(dao, project).require(55L);

    assertEquals("NOT_APPLICABLE", source.state());
    assertNull(source.developmentNodeId());
    assertNull(source.currentDatasetVersionNo());
  }
}

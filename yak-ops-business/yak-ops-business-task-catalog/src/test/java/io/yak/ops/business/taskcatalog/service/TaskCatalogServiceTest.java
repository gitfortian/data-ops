package io.yak.ops.business.taskcatalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.repository.TaskAssetRepository;
import io.yak.ops.business.taskcatalog.spi.TaskAssetRevisionProvider;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskCatalogServiceTest {

  @Test
  void publishCreatesOrAdvancesOneOnlineAsset() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskCatalogService service = new TaskCatalogService(repository);
    TaskAsset expected = asset(9L, 101L, 2);
    when(repository.upsertPublished(
            TaskAssetSource.DATA_DEVELOPMENT,
            "12",
            7L,
            "今天统计",
            "SQL",
            101L,
            2))
        .thenReturn(expected);

    TaskAsset result = service.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        " 12 ",
        7L,
        " 今天统计 ",
        "sql",
        101L,
        2);

    assertEquals(expected, result);
    assertEquals(2, result.currentRevision().revisionNo());
  }

  @Test
  void dataDevelopmentOutputResourcesCannotEnterTaskCatalog() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskCatalogService service = new TaskCatalogService(repository);

    for (String taskType : List.of("DATASET", "DATA_SERVICE")) {
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> service.publish(
              TaskAssetSource.DATA_DEVELOPMENT,
              "12",
              7L,
              "输出资源",
              taskType,
              101L,
              1));
      assertTrue(exception.getMessage().contains("不能发布到 Task Catalog"));
    }
  }

  @Test
  void listDefaultsToOnlineAndNormalizesFilters() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskCatalogService service = new TaskCatalogService(repository);
    when(repository.list(
            TaskAssetSource.DATA_DEVELOPMENT,
            TaskAssetStatus.ONLINE,
            "统计"))
        .thenReturn(List.of(asset(9L, 101L, 2)));

    List<TaskAsset> result = service.list("data_development", null, " 统计 ");

    assertEquals(1, result.size());
    verify(repository).list(
        TaskAssetSource.DATA_DEVELOPMENT,
        TaskAssetStatus.ONLINE,
        "统计");
  }

  @Test
  void revisionProjectMustMatchPublishedAssetProject() {
    TaskAssetRepository repository = mock(TaskAssetRepository.class);
    TaskAssetRevisionProvider provider = new TaskAssetRevisionProvider() {
      @Override
      public TaskAssetSource source() {
        return TaskAssetSource.DATA_DEVELOPMENT;
      }

      @Override
      public Optional<TaskSourceRevision> resolve(String sourceRef, long revisionId) {
        return Optional.of(new TaskSourceRevision(
            101L,
            2,
            new TaskDefinition("SQL", 1, "select 1", "{}"),
            "checksum",
            8L));
      }
    };
    TaskCatalogService service = new TaskCatalogService(repository, List.of(provider));
    when(repository.findById(9L)).thenReturn(Optional.of(asset(9L, 101L, 2)));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.resolveRevision(9L, 101L));

    assertTrue(exception.getMessage().contains("Project"));
  }

  private TaskAsset asset(long id, long revisionId, int revisionNo) {
    Instant now = Instant.parse("2026-08-12T03:30:00Z");
    return new TaskAsset(
        id,
        TaskAssetSource.DATA_DEVELOPMENT,
        "12",
        7L,
        "今天统计",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(id, revisionId, revisionNo),
        now,
        now);
  }
}

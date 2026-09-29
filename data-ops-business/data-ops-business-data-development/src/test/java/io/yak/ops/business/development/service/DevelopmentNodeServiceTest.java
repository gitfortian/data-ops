package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DevelopmentNodeServiceTest {

  @Test
  void createsUnconfiguredSqlAndShellNodes() {
    InMemoryDirectoryRepository directories = new InMemoryDirectoryRepository();
    DevelopmentDirectory folder = directories.insert(null, "ODS");
    DevelopmentNodeService service = new DevelopmentNodeService(
        new InMemoryNodeRepository(),
        directories,
        mock(TaskCatalogService.class));

    DevelopmentNode sql = service.create("用户清洗", "sql", folder.id());
    DevelopmentNode shell = service.create("清理临时文件", "shell", null);

    assertEquals("SQL", sql.type());
    assertEquals(folder.id(), sql.directoryId());
    assertFalse(sql.configured());
    assertEquals("SHELL", shell.type());
    assertFalse(shell.configured());
  }

  @Test
  void rejectsUnknownDirectoryAndDuplicateSiblingName() {
    InMemoryDirectoryRepository directories = new InMemoryDirectoryRepository();
    InMemoryNodeRepository nodes = new InMemoryNodeRepository();
    DevelopmentNodeService service = new DevelopmentNodeService(
        nodes,
        directories,
        mock(TaskCatalogService.class));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("任务", "SQL", 999L));

    service.create("任务", "SQL", null);
    assertThrows(
        IllegalStateException.class,
        () -> service.create("任务", "SHELL", null));
  }

  @Test
  void renamesAndDeletesNodeWhileKeepingCatalogLifecycleAligned() {
    InMemoryDirectoryRepository directories = new InMemoryDirectoryRepository();
    InMemoryNodeRepository nodes = new InMemoryNodeRepository();
    TaskCatalogService taskCatalogService = mock(TaskCatalogService.class);
    DevelopmentNodeService service = new DevelopmentNodeService(
        nodes,
        directories,
        taskCatalogService);
    DevelopmentNode node = service.create("任务", "SQL", null);

    DevelopmentNode renamed = service.rename(node.id(), "新任务");
    assertEquals("新任务", renamed.name());
    verify(taskCatalogService).updateSourceMetadata(
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(node.id()),
        null,
        "新任务",
        "SQL");

    service.delete(node.id());
    assertEquals(List.of(), service.list());
    verify(taskCatalogService).offlineSource(
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(node.id()));
  }

  private static final class InMemoryNodeRepository implements DevelopmentNodeRepository {

    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<Long, DevelopmentNode> values = new LinkedHashMap<>();

    @Override
    public DevelopmentNode insert(
        String name,
        String type,
        Long ignoredProjectId,
        Long directoryId,
        boolean configured) {
      Long id = ids.getAndIncrement();
      Instant now = Instant.now();
      DevelopmentNode node = new DevelopmentNode(
          id,
          name,
          type,
          null,
          directoryId,
          configured,
          now,
          now);
      values.put(id, node);
      return node;
    }

    @Override
    public Optional<DevelopmentNode> findById(Long id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<DevelopmentNode> list() {
      return new ArrayList<>(values.values());
    }

    @Override
    public boolean existsByName(Long directoryId, String name) {
      return values.values().stream().anyMatch(node ->
          java.util.Objects.equals(directoryId, node.directoryId())
              && name.equals(node.name()));
    }

    @Override
    public boolean existsInDirectory(Long directoryId) {
      return values.values().stream().anyMatch(node ->
          java.util.Objects.equals(directoryId, node.directoryId()));
    }

    @Override
    public boolean updateName(Long id, String name) {
      DevelopmentNode current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentNode(
          current.id(),
          name,
          current.type(),
          current.projectId(),
          current.directoryId(),
          current.configured(),
          current.createTime(),
          Instant.now()));
      return true;
    }

    @Override
    public boolean updateConfigured(Long id, boolean configured) {
      DevelopmentNode current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentNode(
          current.id(),
          current.name(),
          current.type(),
          current.projectId(),
          current.directoryId(),
          configured,
          current.createTime(),
          Instant.now()));
      return true;
    }

    @Override
    public boolean updateDirectoryId(Long id, Long directoryId) {
      DevelopmentNode current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentNode(
          current.id(),
          current.name(),
          current.type(),
          current.projectId(),
          directoryId,
          current.configured(),
          current.createTime(),
          Instant.now()));
      return true;
    }

    @Override
    public boolean deleteById(Long id) {
      return values.remove(id) != null;
    }
  }

  private static final class InMemoryDirectoryRepository
      implements DevelopmentDirectoryRepository {

    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<Long, DevelopmentDirectory> values = new LinkedHashMap<>();

    @Override
    public DevelopmentDirectory insert(Long parentId, String name) {
      Long id = ids.getAndIncrement();
      Instant now = Instant.now();
      DevelopmentDirectory directory = new DevelopmentDirectory(
          id,
          parentId,
          name,
          null,
          now,
          now);
      values.put(id, directory);
      return directory;
    }

    @Override
    public Optional<DevelopmentDirectory> findById(Long id) {
      return Optional.ofNullable(values.get(id));
    }

    @Override
    public List<DevelopmentDirectory> list() {
      return new ArrayList<>(values.values());
    }

    @Override
    public boolean existsByName(Long parentId, String name) {
      return values.values().stream().anyMatch(directory ->
          java.util.Objects.equals(parentId, directory.parentId())
              && name.equals(directory.name()));
    }

    @Override
    public boolean hasChildren(Long id) {
      return values.values().stream().anyMatch(directory -> id.equals(directory.parentId()));
    }

    @Override
    public boolean updateName(Long id, String name) {
      DevelopmentDirectory current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentDirectory(
          current.id(), current.parentId(), name, null, current.createTime(), Instant.now()));
      return true;
    }

    @Override
    public boolean updateParentId(Long id, Long parentId) {
      DevelopmentDirectory current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentDirectory(
          current.id(), parentId, current.name(), null, current.createTime(), Instant.now()));
      return true;
    }

    @Override
    public boolean deleteById(Long id) {
      return values.remove(id) != null;
    }
  }
}

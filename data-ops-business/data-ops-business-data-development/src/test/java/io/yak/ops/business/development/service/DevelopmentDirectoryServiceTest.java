package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DevelopmentDirectoryServiceTest {

  @Test
  void createsNestedDirectoryPaths() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = service(repository, new InMemoryNodeRepository());

    DevelopmentDirectory ods = service.create(null, "ODS");
    DevelopmentDirectory user = service.create(ods.id(), "用户域");

    assertEquals("/ODS", ods.path());
    assertEquals("/ODS/用户域", user.path());
    assertEquals(
        List.of("/ODS", "/ODS/用户域"),
        service.list().stream().map(DevelopmentDirectory::path).toList());
  }

  @Test
  void rejectsDuplicateSiblingDirectory() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = service(repository, new InMemoryNodeRepository());
    service.create(null, "ODS");

    assertThrows(IllegalStateException.class, () -> service.create(null, "ODS"));
  }

  @Test
  void renamesDirectoryAndRecomputesChildPath() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    DevelopmentDirectoryService service = service(repository, new InMemoryNodeRepository());
    DevelopmentDirectory ods = service.create(null, "ODS");
    DevelopmentDirectory child = service.create(ods.id(), "用户域");

    service.rename(ods.id(), "DWD");

    assertEquals(
        "/DWD/用户域",
        service.list().stream()
            .filter(directory -> directory.id().equals(child.id()))
            .findFirst()
            .orElseThrow()
            .path());
  }

  @Test
  void onlyDeletesEmptyDirectory() {
    InMemoryDirectoryRepository repository = new InMemoryDirectoryRepository();
    InMemoryNodeRepository nodes = new InMemoryNodeRepository();
    DevelopmentDirectoryService service = service(repository, nodes);
    DevelopmentDirectory parent = service.create(null, "ODS");
    DevelopmentDirectory child = service.create(parent.id(), "用户域");

    assertThrows(IllegalStateException.class, () -> service.delete(parent.id()));
    service.delete(child.id());
    service.delete(parent.id());
    assertEquals(List.of(), service.list());
  }

  private DevelopmentDirectoryService service(
      DevelopmentDirectoryRepository directories,
      DevelopmentNodeRepository nodes) {
    return new DevelopmentDirectoryService(directories, nodes);
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
          current.id(),
          current.parentId(),
          name,
          null,
          current.createTime(),
          Instant.now()));
      return true;
    }

    @Override
    public boolean updateParentId(Long id, Long parentId) {
      DevelopmentDirectory current = values.get(id);
      if (current == null) return false;
      values.put(id, new DevelopmentDirectory(
          current.id(),
          parentId,
          current.name(),
          null,
          current.createTime(),
          Instant.now()));
      return true;
    }

    @Override
    public boolean deleteById(Long id) {
      return values.remove(id) != null;
    }
  }

  private static final class InMemoryNodeRepository implements DevelopmentNodeRepository {

    @Override
    public DevelopmentNode insert(
        String name,
        String type,
        Long projectId,
        Long directoryId,
        boolean configured) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<DevelopmentNode> findById(Long id) {
      return Optional.empty();
    }

    @Override
    public List<DevelopmentNode> list() {
      return List.of();
    }

    @Override
    public boolean existsByName(Long directoryId, String name) {
      return false;
    }

    @Override
    public boolean existsInDirectory(Long directoryId) {
      return false;
    }

    @Override
    public boolean updateName(Long id, String name) {
      return false;
    }

    @Override
    public boolean updateConfigured(Long id, boolean configured) {
      return false;
    }

    @Override
    public boolean updateDirectoryId(Long id, Long directoryId) {
      return false;
    }

    @Override
    public boolean deleteById(Long id) {
      return false;
    }
  }
}

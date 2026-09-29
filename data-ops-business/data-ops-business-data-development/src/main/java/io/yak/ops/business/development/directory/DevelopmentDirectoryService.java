package io.yak.ops.business.development.directory;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.business.development.domain.DevelopmentDirectoryName;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for hierarchical data-development directories. */
@Service
public class DevelopmentDirectoryService {

  private final DevelopmentDirectoryRepository repository;
  private final DevelopmentNodeRepository nodeRepository;

  public DevelopmentDirectoryService(
      DevelopmentDirectoryRepository repository,
      DevelopmentNodeRepository nodeRepository) {
    this.repository = repository;
    this.nodeRepository = nodeRepository;
  }

  public List<DevelopmentDirectory> list() {
    List<DevelopmentDirectory> directories = repository.list();
    Map<Long, DevelopmentDirectory> byId = new HashMap<>();
    directories.forEach(directory -> byId.put(directory.id(), directory));
    Map<Long, String> pathCache = new HashMap<>();

    return directories.stream()
        .map(directory -> withPath(
            directory,
            resolvePath(directory, byId, pathCache, new HashSet<>())))
        .sorted(Comparator.comparing(DevelopmentDirectory::path, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDirectory create(Long parentId, String name) {
    Long normalizedParentId = normalizeParentId(parentId);
    DevelopmentDirectoryName directoryName = DevelopmentDirectoryName.of(name);

    if (normalizedParentId != null) {
      repository.findById(normalizedParentId)
          .orElseThrow(() -> new IllegalArgumentException("父目录不存在：" + normalizedParentId));
    }

    if (repository.existsByName(normalizedParentId, directoryName.value())) {
      throw new IllegalStateException("当前路径下已存在同名目录：" + directoryName.value());
    }

    DevelopmentDirectory created = repository.insert(normalizedParentId, directoryName.value());
    return requireFromList(created.id());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDirectory rename(Long id, String name) {
    DevelopmentDirectory current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("目录不存在：" + id));
    DevelopmentDirectoryName directoryName = DevelopmentDirectoryName.of(name);
    if (current.name().equals(directoryName.value())) return requireFromList(id);
    if (repository.existsByName(current.parentId(), directoryName.value())) {
      throw new IllegalStateException("当前路径下已存在同名目录：" + directoryName.value());
    }
    if (!repository.updateName(id, directoryName.value())) {
      throw new IllegalStateException("目录重命名失败：" + id);
    }
    return requireFromList(id);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("目录不存在：" + id));
    if (repository.hasChildren(id) || nodeRepository.existsInDirectory(id)) {
      throw new IllegalStateException("目录不为空，请先删除目录下的节点和子目录");
    }
    if (!repository.deleteById(id)) {
      throw new IllegalStateException("目录删除失败：" + id);
    }
  }

  /** Moves a directory under a different parent directory. */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDirectory move(Long id, Long targetParentId) {
    DevelopmentDirectory current = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("目录不存在：" + id));
    Long normalizedParentId = normalizeParentId(targetParentId);

    if (normalizedParentId != null) {
      if (normalizedParentId.equals(id)) {
        throw new IllegalStateException("不能将目录移动到自身下");
      }
      repository.findById(normalizedParentId)
          .orElseThrow(() -> new IllegalArgumentException("目标父目录不存在：" + normalizedParentId));
      if (isDescendant(normalizedParentId, id)) {
        throw new IllegalStateException("不能将目录移动到其子目录下");
      }
    }
    if (normalizedParentId != null && normalizedParentId.equals(current.parentId())) {
      return requireFromList(id);
    }
    if (normalizedParentId == null && current.parentId() == null) {
      return requireFromList(id);
    }
    if (repository.existsByName(normalizedParentId, current.name())) {
      throw new IllegalStateException("目标路径下已存在同名目录：" + current.name());
    }
    if (!repository.updateParentId(id, normalizedParentId)) {
      throw new IllegalStateException("目录移动失败：" + id);
    }
    return requireFromList(id);
  }

  /** Returns true when candidateId is a descendant of ancestorId. */
  private boolean isDescendant(Long candidateId, Long ancestorId) {
    Set<Long> visited = new HashSet<>();
    Long currentId = candidateId;
    while (currentId != null) {
      if (currentId.equals(ancestorId)) return true;
      if (!visited.add(currentId)) break;
      Optional<DevelopmentDirectory> dir = repository.findById(currentId);
      if (dir.isEmpty()) break;
      currentId = dir.get().parentId();
    }
    return false;
  }

  private DevelopmentDirectory requireFromList(Long id) {
    return list().stream()
        .filter(directory -> directory.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("目录保存成功但无法重新读取：" + id));
  }

  private DevelopmentDirectory withPath(DevelopmentDirectory directory, String path) {
    return new DevelopmentDirectory(
        directory.id(),
        directory.parentId(),
        directory.name(),
        path,
        directory.createTime(),
        directory.updateTime());
  }

  private String resolvePath(
      DevelopmentDirectory directory,
      Map<Long, DevelopmentDirectory> byId,
      Map<Long, String> pathCache,
      Set<Long> visiting) {
    String cached = pathCache.get(directory.id());
    if (cached != null) return cached;
    if (!visiting.add(directory.id())) {
      throw new IllegalStateException("数据开发目录存在循环引用：" + directory.id());
    }

    String path;
    if (directory.parentId() == null) {
      path = "/" + directory.name();
    } else {
      DevelopmentDirectory parent = byId.get(directory.parentId());
      if (parent == null) {
        throw new IllegalStateException(
            "数据开发目录父节点不存在：" + directory.id() + " -> " + directory.parentId());
      }
      path = resolvePath(parent, byId, pathCache, visiting) + "/" + directory.name();
    }

    visiting.remove(directory.id());
    pathCache.put(directory.id(), path);
    return path;
  }

  private Long normalizeParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? null : parentId;
  }
}

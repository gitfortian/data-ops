package io.yak.ops.business.modeling.catalog;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.domain.ModelingDirectory;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelDirectoryRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns modeling directory rules: unique sibling names, empty-directory
 * deletion and cycle-free moves. Mirrors the data-development directory
 * semantics so both trees behave identically.
 */
@Component
public class ModelDirectoryService {

  private final ModelDirectoryRepository repository;
  private final ModelRepository modelRepository;
  private final BusinessAuditService auditService;

  public ModelDirectoryService(
      ModelDirectoryRepository repository,
      ModelRepository modelRepository,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.modelRepository = modelRepository;
    this.auditService = auditService;
  }

  public List<ModelingDirectory> list() {
    return repository.listAll();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ModelingDirectory create(Long parentId, String name) {
    Long normalizedParentId = normalizeParentId(parentId);
    String trimmedName = requireName(name);
    AuditOperationHandle audit = startAudit("MODELING_DIRECTORY_CREATE", null, trimmedName);
    try {
      if (normalizedParentId != null) {
        requireExisting(normalizedParentId);
      }
      if (repository.existsByName(normalizedParentId, trimmedName)) {
        throw new ModelingException(ModelingErrorCode.DUPLICATE_CODE, "同级目录已存在同名目录：" + trimmedName);
      }
      ModelingDirectory created = repository.insert(normalizedParentId, trimmedName);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Modeling directory created", Map.of(), "目录已创建");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("MODELING_DIRECTORY_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void rename(Long id, String name) {
    ModelingDirectory current = requireExisting(id);
    String trimmedName = requireName(name);
    AuditOperationHandle audit = startAudit("MODELING_DIRECTORY_RENAME", id, trimmedName);
    try {
      if (!current.name().equals(trimmedName)
          && repository.existsByName(current.parentId(), trimmedName)) {
        throw new ModelingException(ModelingErrorCode.DUPLICATE_CODE, "同级目录已存在同名目录：" + trimmedName);
      }
      if (!repository.updateName(id, trimmedName)) {
        throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Modeling directory renamed", Map.of(), "目录已重命名");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_DIRECTORY_RENAME_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void move(Long id, Long targetParentId) {
    ModelingDirectory current = requireExisting(id);
    Long normalizedParentId = normalizeParentId(targetParentId);
    AuditOperationHandle audit = startAudit("MODELING_DIRECTORY_MOVE", id, current.name());
    try {
      if (normalizedParentId != null) {
        if (normalizedParentId.equals(id)) {
          throw new ModelingException(ModelingErrorCode.INVALID_DIRECTORY_TARGET, "不能将目录移动到自身下");
        }
        requireExisting(normalizedParentId);
        if (isDescendant(normalizedParentId, id)) {
          throw new ModelingException(ModelingErrorCode.INVALID_DIRECTORY_TARGET, "不能将目录移动到其子目录下");
        }
      }
      if (!sameEffectiveParent(normalizedParentId, current.parentId())) {
        if (repository.existsByName(normalizedParentId, current.name())) {
          throw new ModelingException(
              ModelingErrorCode.DUPLICATE_CODE, "目标路径下已存在同名目录：" + current.name());
        }
        if (!repository.updateParentId(id, normalizedParentId)) {
          throw new ModelingException(ModelingErrorCode.UPDATE_FAILED);
        }
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Modeling directory moved", Map.of(), "目录已移动");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_DIRECTORY_MOVE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    ModelingDirectory current = requireExisting(id);
    AuditOperationHandle audit = startAudit("MODELING_DIRECTORY_DELETE", id, current.name());
    try {
      if (repository.hasChildren(id)) {
        throw new ModelingException(ModelingErrorCode.DIRECTORY_NOT_EMPTY, "目录下存在子目录，请先删除子目录");
      }
      if (modelRepository.countByDirectory(id) > 0) {
        throw new ModelingException(ModelingErrorCode.DIRECTORY_NOT_EMPTY, "目录下存在模型，请先移动或删除模型");
      }
      if (!repository.deleteById(id)) {
        throw new ModelingException(ModelingErrorCode.DELETE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED, "Modeling directory deleted", Map.of(), "目录已删除");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_DIRECTORY_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** Returns true when candidateId is inside the subtree of ancestorId. */
  private boolean isDescendant(Long candidateId, Long ancestorId) {
    Set<Long> visited = new HashSet<>();
    Long currentId = candidateId;
    while (currentId != null) {
      if (currentId.equals(ancestorId)) {
        return true;
      }
      if (!visited.add(currentId)) {
        break;
      }
      Optional<ModelingDirectory> directory = repository.findById(currentId);
      if (directory.isEmpty()) {
        break;
      }
      currentId = directory.get().parentId();
    }
    return false;
  }

  private ModelingDirectory requireExisting(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "目录不存在：" + id));
  }

  private String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ModelingException(ModelingErrorCode.INVALID_DIRECTORY_NAME, "目录名称不能为空");
    }
    String trimmed = name.trim();
    if (trimmed.length() > 128) {
      throw new ModelingException(ModelingErrorCode.INVALID_DIRECTORY_NAME, "目录名称不能超过 128 个字符");
    }
    return trimmed;
  }

  private AuditOperationHandle startAudit(String operationType, Long id, String name) {
    return auditService.start(
        new AuditOperationRequest(
            operationType,
            operationType,
            "MODELING_DIRECTORY",
            id == null ? null : String.valueOf(id),
            name,
            "APPLICATION",
            Map.of()));
  }

  private boolean sameEffectiveParent(Long normalizedParentId, Long currentParentId) {
    return normalizedParentId == null
        ? currentParentId == null
        : normalizedParentId.equals(currentParentId);
  }

  private Long normalizeParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? null : parentId;
  }

}

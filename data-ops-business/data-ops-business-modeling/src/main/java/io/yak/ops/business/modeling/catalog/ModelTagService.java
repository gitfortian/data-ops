package io.yak.ops.business.modeling.catalog;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.domain.ModelingTag;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelTagRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Owns modeling tag rules: project-unique names, cascade detach on delete. */
@Component
public class ModelTagService {

  private final ModelTagRepository repository;
  private final BusinessAuditService auditService;

  public ModelTagService(ModelTagRepository repository, BusinessAuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  public List<ModelingTag> list() {
    return repository.listAll();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ModelingTag create(String name) {
    String trimmed = requireName(name);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_TAG_CREATE",
                "Create modeling tag",
                "MODELING_TAG",
                null,
                trimmed,
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByName(trimmed)) {
        throw new ModelingException(ModelingErrorCode.DUPLICATE_CODE, "已存在同名标签：" + trimmed);
      }
      ModelingTag created = repository.insert(trimmed);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Modeling tag created", Map.of(), "标签已创建");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("MODELING_TAG_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    ModelingTag tag =
        repository
            .findById(id)
            .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, "标签不存在：" + id));
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_TAG_DELETE",
                "Delete modeling tag",
                "MODELING_TAG",
                String.valueOf(id),
                tag.name(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new ModelingException(ModelingErrorCode.DELETE_FAILED);
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED, "Modeling tag deleted", Map.of(), "标签已删除");
    } catch (RuntimeException exception) {
      audit.failure("MODELING_TAG_DELETE_FAILED", exception);
      throw exception;
    }
  }

  private String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ModelingException(ModelingErrorCode.INVALID_TAG_NAME, "标签名称不能为空");
    }
    String trimmed = name.trim();
    if (trimmed.length() > 128) {
      throw new ModelingException(ModelingErrorCode.INVALID_TAG_NAME, "标签名称不能超过 128 个字符");
    }
    return trimmed;
  }

}

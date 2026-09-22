package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmEntityRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.util.AuditDiffs;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the master data entity rules: code format/uniqueness, status flow, and
 * delete blocking on references. Attribute (52) / source (53) reference checks
 * arrive with their owning tickets (empty hook today).
 */
@Component
public class MdmEntityService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  private final MdmEntityRepository repository;
  private final BusinessAuditService auditService;

  public MdmEntityService(MdmEntityRepository repository, BusinessAuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  public PageData<MdmEntity> page(int pageNo, int pageSize, String keyword, String status) {
    MdmEntityStatus resolvedStatus = resolveStatus(status);
    return repository.page(pageNo, pageSize, keyword, resolvedStatus);
  }

  public MdmEntity get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.ENTITY_NOT_FOUND, String.valueOf(id)));
  }

  /** 列出全部实体(总览/治理聚合用,ticket 62)。 */
  public List<MdmEntity> findAll() {
    return repository.findAll();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmEntity create(
      String code, String name, String owner, String description, String operator) {
    validateCode(code);
    validateName(name);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ENTITY_CREATE",
                "Create master data entity",
                "MDM_ENTITY",
                null,
                code,
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByCode(code)) {
        throw new MdmException(MdmErrorCode.DUPLICATE_CODE, code);
      }
      MdmEntity inserted =
          repository.insert(
              new MdmEntity(
                  null,
                  code,
                  name,
                  MdmEntityStatus.DRAFT,
                  owner,
                  description,
                  operator,
                  null,
                  null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Master data entity created",
          Map.of(),
          "Master data entity created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_ENTITY_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(Long id, String name, String owner, String description) {
    MdmEntity existing = get(id);
    validateName(name);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ENTITY_UPDATE",
                "Update master data entity",
                "MDM_ENTITY",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      MdmEntity updated = existing.withEditable(name, owner, description);
      repository.update(updated);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Master data entity updated",
          AuditDiffs.diff(entitySnapshot(existing), entitySnapshot(updated)),
          "Master data entity updated");
    } catch (RuntimeException exception) {
      audit.failure("MDM_ENTITY_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  private static Map<String, Object> entitySnapshot(MdmEntity value) {
    Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
    snapshot.put("name", value.name());
    snapshot.put("owner", value.owner());
    snapshot.put("description", value.description());
    snapshot.put("status", value.status() == null ? null : value.status().name());
    return snapshot;
  }

  /** 状态流转:DRAFT 为创建态,离场后不可回退;生效/停用可互切。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void changeStatus(Long id, MdmEntityStatus target) {
    MdmEntity existing = get(id);
    if (target == null) {
      throw new MdmException(MdmErrorCode.INVALID_STATUS, "状态不能为空");
    }
    if (target == MdmEntityStatus.DRAFT && existing.status() != MdmEntityStatus.DRAFT) {
      throw new MdmException(MdmErrorCode.INVALID_STATUS, "已生效/停用的实体不可回退为草稿");
    }
    if (existing.status() == target) {
      return;
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ENTITY_STATUS",
                "Change master data entity status",
                "MDM_ENTITY",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of("from", existing.status() == null ? "-" : existing.status().name(),
                    "to", target.name())));
    try {
      repository.changeStatus(id, target);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Master data entity status changed",
          Map.of(),
          "Master data entity status changed");
    } catch (RuntimeException exception) {
      audit.failure("MDM_ENTITY_STATUS_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    MdmEntity existing = get(id);
    // 引用校验挂点:52 属性 / 53 来源引用后并入(本期校验为空实现)。
    if (isReferenced(existing.id())) {
      throw new MdmException(MdmErrorCode.ENTITY_REFERENCED, existing.code());
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ENTITY_DELETE",
                "Delete master data entity",
                "MDM_ENTITY",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new MdmException(MdmErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Master data entity deleted",
          Map.of(),
          "Master data entity deleted");
    } catch (RuntimeException exception) {
      audit.failure("MDM_ENTITY_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** 引用校验挂点:52 属性引用 / 53 来源引用随 ticket 并入,本期恒 false。 */
  private boolean isReferenced(Long entityId) {
    return false;
  }

  private static MdmEntityStatus resolveStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    try {
      return MdmEntityStatus.valueOf(status);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_STATUS, status);
    }
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new MdmException(MdmErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new MdmException(MdmErrorCode.INVALID_NAME, "实体名称不能为空");
    }
  }
}

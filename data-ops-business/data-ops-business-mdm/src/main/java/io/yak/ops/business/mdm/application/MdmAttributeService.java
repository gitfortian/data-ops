package io.yak.ops.business.mdm.application;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.util.AuditDiffs;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the master data attribute rules: code format/uniqueness within an
 * entity, single PK, and standard-reference validation via the semantic SPI
 * (type/unit/security by id, code-set by code). Delete reference checks for
 * collection mapping (54) / clean rules (56) arrive with their owning tickets
 * (source field mappings, cleansing rules and materialized records).
 */
@Component
public class MdmAttributeService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  private final MdmAttributeRepository repository;
  private final MdmEntityService entityService;
  private final StandardQueryApi standardQueryApi;
  private final BusinessAuditService auditService;

  public MdmAttributeService(
      MdmAttributeRepository repository,
      MdmEntityService entityService,
      StandardQueryApi standardQueryApi,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.entityService = entityService;
    this.standardQueryApi = standardQueryApi;
    this.auditService = auditService;
  }

  public List<MdmAttribute> list(Long entityId) {
    entityService.get(entityId);
    return repository.listByEntity(entityId);
  }

  public MdmAttribute get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.ATTRIBUTE_NOT_FOUND, String.valueOf(id)));
  }

  /** 标准引用标签解析(经语义 SPI,`名称（编码）`);供列表 VO 展示。 */
  public Map<Long, String> standardLabels(Collection<Long> ids) {
    return standardQueryApi.labels(ids);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmAttribute create(
      Long entityId,
      String code,
      String name,
      MdmAttributeType type,
      String dataType,
      Long stdTypeId,
      Long stdUnitId,
      String stdCodeSetCode,
      Long stdSecurityId,
      Boolean required,
      String businessDesc,
      Integer sortOrder,
      String operator) {
    entityService.get(entityId);
    validateCode(code);
    validateName(name);
    validateType(type);
    validateRefs(stdTypeId, stdUnitId, stdCodeSetCode, stdSecurityId);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ATTRIBUTE_CREATE",
                "Create master data attribute",
                "MDM_ATTRIBUTE",
                null,
                code,
                "APPLICATION",
                Map.of("entityId", String.valueOf(entityId), "attrType", type.name())));
    try {
      if (repository.existsByCode(entityId, code)) {
        throw new MdmException(MdmErrorCode.DUPLICATE_ATTR_CODE, code);
      }
      if (type == MdmAttributeType.PK && repository.existsPk(entityId)) {
        throw new MdmException(MdmErrorCode.PK_ATTRIBUTE_EXISTS, entityId.toString());
      }
      MdmAttribute inserted =
          repository.insert(
              new MdmAttribute(
                  null,
                  entityId,
                  code,
                  name,
                  type,
                  dataType,
                  stdTypeId,
                  stdUnitId,
                  stdCodeSetCode,
                  stdSecurityId,
                  Boolean.TRUE.equals(required),
                  businessDesc,
                  sortOrder == null ? 0 : sortOrder,
                  MdmAttribute.STATUS_ENABLED,
                  operator,
                  null,
                  null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Master data attribute created",
          Map.of(),
          "Master data attribute created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_ATTRIBUTE_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(
      Long id,
      String name,
      MdmAttributeType type,
      String dataType,
      Long stdTypeId,
      Long stdUnitId,
      String stdCodeSetCode,
      Long stdSecurityId,
      Boolean required,
      String businessDesc,
      Integer sortOrder) {
    MdmAttribute existing = get(id);
    validateName(name);
    validateType(type);
    validateRefs(stdTypeId, stdUnitId, stdCodeSetCode, stdSecurityId);
    boolean changesPkRole =
        (existing.type() == MdmAttributeType.PK) != (type == MdmAttributeType.PK);
    if (changesPkRole && repository.hasReferences(existing.entityId(), existing.code())) {
      throw new MdmException(
          MdmErrorCode.ATTRIBUTE_REFERENCED,
          "属性参与已有记录、来源映射或清洗规则，不能改变 PK 身份角色");
    }
    if (type == MdmAttributeType.PK
        && existing.type() != MdmAttributeType.PK
        && repository.existsPk(existing.entityId())) {
      throw new MdmException(MdmErrorCode.PK_ATTRIBUTE_EXISTS, existing.entityId().toString());
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ATTRIBUTE_UPDATE",
                "Update master data attribute",
                "MDM_ATTRIBUTE",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of("attrType", type.name())));
    try {
      MdmAttribute updated = new MdmAttribute(
          existing.id(),
          existing.entityId(),
          existing.code(),
          name,
          type,
          dataType,
          stdTypeId,
          stdUnitId,
          stdCodeSetCode,
          stdSecurityId,
          Boolean.TRUE.equals(required),
          businessDesc,
          sortOrder == null ? existing.sortOrder() : sortOrder,
          existing.status(),
          existing.createdBy(),
          existing.createTime(),
          existing.updateTime());
      repository.update(updated);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Master data attribute updated",
          AuditDiffs.diff(attributeSnapshot(existing), attributeSnapshot(updated)),
          "Master data attribute updated");
    } catch (RuntimeException exception) {
      audit.failure("MDM_ATTRIBUTE_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  private static Map<String, Object> attributeSnapshot(MdmAttribute value) {
    Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
    snapshot.put("name", value.name());
    snapshot.put("type", value.type() == null ? null : value.type().name());
    snapshot.put("dataType", value.dataType());
    snapshot.put("stdTypeId", value.stdTypeId());
    snapshot.put("stdUnitId", value.stdUnitId());
    snapshot.put("stdCodeSetCode", value.stdCodeSetCode());
    snapshot.put("stdSecurityId", value.stdSecurityId());
    snapshot.put("required", value.required());
    snapshot.put("businessDesc", value.businessDesc());
    snapshot.put("sortOrder", value.sortOrder());
    return snapshot;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    MdmAttribute existing = get(id);
    // Records retain the entity's declared shape; mappings and rule JSON are loose references.
    if (isReferenced(existing.id())) {
      throw new MdmException(MdmErrorCode.ATTRIBUTE_REFERENCED, existing.code());
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_ATTRIBUTE_DELETE",
                "Delete master data attribute",
                "MDM_ATTRIBUTE",
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
          "Master data attribute deleted",
          Map.of(),
          "Master data attribute deleted");
    } catch (RuntimeException exception) {
      audit.failure("MDM_ATTRIBUTE_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** Project-scoped check for source mappings, cleansing expressions and existing records. */
  private boolean isReferenced(Long attributeId) {
    MdmAttribute attribute = get(attributeId);
    return repository.hasReferences(attribute.entityId(), attribute.code());
  }

  private void validateRefs(
      Long stdTypeId, Long stdUnitId, String stdCodeSetCode, Long stdSecurityId) {
    validateKindRef(stdTypeId, StandardKind.TYPE, "类型");
    validateKindRef(stdUnitId, StandardKind.UNIT, "单位");
    validateKindRef(stdSecurityId, StandardKind.SECURITY, "安全");
    validateCodeSet(stdCodeSetCode);
  }

  private void validateKindRef(Long standardId, StandardKind expectedKind, String label) {
    if (standardId == null) {
      return;
    }
    Standard standard = standardQueryApi.get(standardId);
    if (standard == null
        || standard.kind() != expectedKind
        || standard.status() != StandardStatus.ENABLED) {
      throw new MdmException(MdmErrorCode.INVALID_STANDARD_REF, label + "标准引用不合法或不可用: " + standardId);
    }
  }

  private void validateCodeSet(String codeSetCode) {
    if (!StringUtils.hasText(codeSetCode)) {
      return;
    }
    if (!standardQueryApi.existsCodeSet(codeSetCode)) {
      throw new MdmException(MdmErrorCode.INVALID_STANDARD_REF, "码集不存在或未启用: " + codeSetCode);
    }
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new MdmException(MdmErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new MdmException(MdmErrorCode.INVALID_NAME, "属性名称不能为空");
    }
  }

  private static void validateType(MdmAttributeType type) {
    if (type == null) {
      throw new MdmException(MdmErrorCode.INVALID_ATTR_ROLE, "属性角色不能为空");
    }
  }
}

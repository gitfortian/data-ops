package io.yak.ops.business.semantic.field;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticFieldApi;
import io.yak.ops.business.semantic.api.SemanticFieldReferenceReader;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the standard field library rules (ticket 35 + 2026-09-15 评审补充):
 * code/role validation, matching-kind standard references, data_type snapshot
 * sync (收紧 1:引用类型标准时服务端覆盖), code-set existence check (决策一),
 * status lifecycle (约束 4 部分) and optimistic versioning (约束 3).
 */
@Component
public class SemanticFieldService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  private final SemanticFieldRepository repository;
  private final SemanticProcessFieldRepository processFieldRepository;
  private final SemanticProcessRepository processRepository;
  private final SemanticStandardRepository standardRepository;
  private final BusinessAuditService auditService;
  private final List<SemanticFieldReferenceReader> referenceReaders;

  @Autowired
  public SemanticFieldService(
      SemanticFieldRepository repository,
      SemanticProcessFieldRepository processFieldRepository,
      SemanticProcessRepository processRepository,
      SemanticStandardRepository standardRepository,
      BusinessAuditService auditService,
      ObjectProvider<SemanticFieldReferenceReader> referenceReaders) {
    this(repository, processFieldRepository, processRepository, standardRepository,
        auditService, referenceReaders.orderedStream().toList());
  }

  /** Compatibility constructor for focused tests without cross-domain consumers. */
  public SemanticFieldService(
      SemanticFieldRepository repository,
      SemanticProcessFieldRepository processFieldRepository,
      SemanticProcessRepository processRepository,
      SemanticStandardRepository standardRepository,
      BusinessAuditService auditService) {
    this(repository, processFieldRepository, processRepository, standardRepository,
        auditService, List.of());
  }

  SemanticFieldService(
      SemanticFieldRepository repository,
      SemanticProcessFieldRepository processFieldRepository,
      SemanticProcessRepository processRepository,
      SemanticStandardRepository standardRepository,
      BusinessAuditService auditService,
      List<SemanticFieldReferenceReader> referenceReaders) {
    this.repository = repository;
    this.processFieldRepository = processFieldRepository;
    this.processRepository = processRepository;
    this.standardRepository = standardRepository;
    this.auditService = auditService;
    this.referenceReaders = List.copyOf(referenceReaders);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StandardField create(SemanticFieldApi.CreateRequest request, String operator) {
    validateCode(request.code());
    validateName(request.name());
    validateRole(request.role());
    // 按角色适配(2026-09-16):三类字段都必须引用启用的类型标准;METRIC 必填单位、口径选填。
    Standard typeStandard = validateTypeRef(request.stdTypeId());
    RoleRefs refs =
        normalizeRoleRefs(
            request.role(), request.stdUnitId(), request.stdCaliberId(),
            request.stdCodeSetCode(), request.stdSecurityId());
    validateCaliberRef(refs.stdCaliberId());
    validateUnitRef(refs.stdUnitId(), request.role());
    validateSecurityRef(refs.stdSecurityId());
    validateCodeSet(refs.stdCodeSetCode());
    // 收紧 1:引用类型标准时 data_type 以标准为准(服务端覆盖)。
    String dataType = resolveDataType(typeStandard, request.dataType());
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_FIELD_CREATE",
                "Create standard field",
                "SEMANTIC_FIELD",
                null,
                request.code(),
                "APPLICATION",
                Map.of("role", request.role())));
    try {
      if (repository.existsByCode(request.code())) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, request.code());
      }
      StandardField inserted =
          repository.insert(
              new StandardField(
                  null, request.code(), request.name(), request.role(),
                  StandardField.STATUS_ENABLED, dataType, request.stdTypeId(),
                  refs.stdUnitId(), refs.stdCaliberId(), refs.stdCodeSetCode(),
                  refs.stdSecurityId(), request.businessDesc(), StandardField.SOURCE_MANUAL,
                  1, false, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Standard field created",
          Map.of("role", request.role()),
          "Standard field created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_FIELD_CREATE_FAILED", exception);
      throw exception;
    }
  }

  public StandardField get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public PageData<StandardField> page(int pageNo, int pageSize, String role, String keyword) {
    return repository.page(pageNo, pageSize, role, keyword);
  }

  /** 启用态字段清单(关键字可空);经 ProcessApi 只读开放,供 38 导入与 44 派生匹配。 */
  public List<StandardField> listEnabled(String keyword) {
    return repository.listEnabled(keyword);
  }

  /** 字段列表引用名称解析(2026-09-16):批量取标准,标签 = 名称（编码）,前端不再依赖字典。 */
  public Map<Long, String> standardLabels(java.util.Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    return standardRepository.findByIds(ids).stream()
        .collect(
            Collectors.toMap(
                Standard::id,
                standard -> standard.name() + "（" + standard.code() + "）",
                (first, second) -> first));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StandardField update(SemanticFieldApi.UpdateRequest request, String operator) {
    StandardField existing = get(request.id());
    validateName(request.name());
    validateRole(request.role());
    // 按角色适配(2026-09-16):同 create,类型引用必填 + 角色必填校验 + 无意义引用归零。
    Standard typeStandard = validateTypeRef(request.stdTypeId());
    RoleRefs refs =
        normalizeRoleRefs(
            request.role(), request.stdUnitId(), request.stdCaliberId(),
            request.stdCodeSetCode(), request.stdSecurityId());
    validateCaliberRef(refs.stdCaliberId());
    validateUnitRef(refs.stdUnitId(), request.role());
    validateSecurityRef(refs.stdSecurityId());
    validateCodeSet(refs.stdCodeSetCode());
    // 约束 3:乐观锁,版本冲突拒绝。
    if (request.version() == null || request.version() != existing.version()) {
      throw new SemanticException(
          SemanticErrorCode.VERSION_CONFLICT,
          "当前版本 " + existing.version() + "，请求版本 " + request.version());
    }
    // 收紧 1:引用类型标准时 data_type 服务端覆盖。
    String dataType = resolveDataType(typeStandard, request.dataType());
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_FIELD_UPDATE",
                "Update standard field",
                "SEMANTIC_FIELD",
                String.valueOf(request.id()),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      StandardField updated =
          new StandardField(
              existing.id(), existing.code(), request.name(), request.role(),
              existing.status(), dataType, request.stdTypeId(), refs.stdUnitId(),
              refs.stdCaliberId(), refs.stdCodeSetCode(), refs.stdSecurityId(),
              request.businessDesc(), existing.source(), existing.version() + 1, existing.required(),
              existing.createdBy(), existing.createTime(), existing.updateTime());
      StandardField saved = repository.update(updated);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Standard field updated",
          Map.of("version", String.valueOf(saved.version())),
          "Standard field updated");
      return saved;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_FIELD_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  /** 启用/停用(约束 4 部分):停用字段不出现在字段集/绑定/派生。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public StandardField changeStatus(Long id, String status, String operator) {
    StandardField existing = get(id);
    if (!StandardField.STATUS_ENABLED.equals(status)
        && !StandardField.STATUS_DISABLED.equals(status)) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, status);
    }
    if (status.equals(existing.status())) return existing;
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_FIELD_STATUS", "Change standard field status",
            "SEMANTIC_FIELD", String.valueOf(id), existing.code(), "APPLICATION",
            Map.of("from", existing.status(), "to", status)));
    try {
      if (!repository.changeStatus(id, status)) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
          "Standard field status changed", Map.of("status", status), "Standard field status changed");
      return get(id);
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_FIELD_STATUS_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    StandardField existing = get(id);
    if (processFieldRepository.countByField(id) > 0) {
      throw new SemanticException(SemanticErrorCode.FIELD_REFERENCED, "存在业务过程引用");
    }
    // Modeling (and future consumers) own their reference truth. Never delete a
    // standard field while a consumer still holds its stable ID; failed queries
    // must block deletion rather than masquerade as zero references.
    for (SemanticFieldReferenceReader reader : referenceReaders) {
      if (reader.countFieldReferences(id) > 0) {
        throw new SemanticException(
            SemanticErrorCode.FIELD_REFERENCED, "存在建模或其它消费模块引用，请先解除关联");
      }
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_FIELD_DELETE",
                "Delete standard field",
                "SEMANTIC_FIELD",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Standard field deleted",
          Map.of(),
          "Standard field deleted");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_FIELD_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** 过程绑定字段(is_required 驱动 44 默认勾选);停用字段拒绝绑定。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void bindToProcess(Long processId, Long fieldId, boolean isRequired, String operator) {
    var process = processRepository
        .findById(processId)
        .orElseThrow(
            () -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    StandardField field = get(fieldId);
    if (!field.isEnabled()) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, "已停用的字段不可绑定");
    }
    if (processFieldRepository.existsByProcessAndField(processId, fieldId)) {
      throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, "该字段已被此业务过程引用");
    }
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_PROCESS_FIELD_BIND", "Bind field to business process",
            "SEMANTIC_PROCESS_FIELD", processId + ":" + fieldId, field.code(), "APPLICATION",
            Map.of("processCode", process.code(), "required", String.valueOf(isRequired),
                "operator", operator)));
    try {
      processFieldRepository.bind(processId, fieldId, isRequired, operator);
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_CREATED,
          "Business process field bound", Map.of("required", String.valueOf(isRequired)),
          "Business process field bound");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_FIELD_BIND_FAILED", exception);
      throw exception;
    }
  }

  /** 过程移除字段引用。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void unbindFromProcess(Long processId, Long fieldId, String operator) {
    var process = processRepository.findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    StandardField field = get(fieldId);
    if (!processFieldRepository.existsByProcessAndField(processId, fieldId)) {
      throw new SemanticException(SemanticErrorCode.NOT_FOUND, "此过程未引用该字段");
    }
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_PROCESS_FIELD_UNBIND", "Unbind field from business process",
            "SEMANTIC_PROCESS_FIELD", processId + ":" + fieldId, field.code(), "APPLICATION",
            Map.of("processCode", process.code(), "operator", operator)));
    try {
      processFieldRepository.unbind(processId, fieldId);
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_DELETED,
          "Business process field unbound", Map.of(), "Business process field unbound");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_FIELD_UNBIND_FAILED", exception);
      throw exception;
    }
  }

  /** 过程字段集(按过程内顺序;停用字段剔除;required 随装配标记)。 */
  public List<StandardField> fieldsOfProcess(Long processId) {
    processRepository
        .findById(processId)
        .orElseThrow(
            () -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    List<StandardField> result = new ArrayList<>();
    for (SemanticProcessFieldRepository.ProcessFieldBinding binding :
        processFieldRepository.bindingsByProcess(processId)) {
      StandardField field = get(binding.fieldId());
      if (field.isEnabled()) {
        result.add(field.withRequired(binding.required()));
      }
    }
    return result;
  }

  /** 管理查询保留停用引用，消费查询继续仅返回启用字段。 */
  public List<StandardField> managedFieldsOfProcess(Long processId) {
    processRepository.findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    return processFieldRepository.bindingsByProcess(processId).stream()
        .map(binding -> get(binding.fieldId()).withRequired(binding.required())).toList();
  }

  /** 更新过程字段是否必需。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void updateRequired(Long processId, Long fieldId, boolean required, String operator) {
    var process = processRepository.findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    var binding = processFieldRepository.bindingsByProcess(processId).stream()
        .filter(candidate -> candidate.fieldId().equals(fieldId)).findFirst()
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "此过程未引用该字段"));
    if (binding.required() == required) return;
    StandardField field = get(fieldId);
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_PROCESS_FIELD_REQUIRED", "Update required field flag",
            "SEMANTIC_PROCESS_FIELD", processId + ":" + fieldId, field.code(), "APPLICATION",
            Map.of("processCode", process.code(), "from", String.valueOf(binding.required()),
                "to", String.valueOf(required), "operator", operator)));
    try {
      if (!processFieldRepository.updateRequired(processId, fieldId, required)) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND, "此过程未引用该字段");
      }
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
          "Business process field requirement changed", Map.of("required", String.valueOf(required)),
          "Business process field requirement changed");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_FIELD_REQUIRED_FAILED", exception);
      throw exception;
    }
  }

  /** 过程字段重排序。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void reorderProcessFields(Long processId, List<Long> orderedFieldIds, String operator) {
    var process = processRepository.findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    List<Long> currentIds = processFieldRepository.bindingsByProcess(processId).stream()
        .map(SemanticProcessFieldRepository.ProcessFieldBinding::fieldId).toList();
    if (orderedFieldIds == null || orderedFieldIds.size() != currentIds.size()
        || new java.util.HashSet<>(orderedFieldIds).size() != currentIds.size()
        || !new java.util.HashSet<>(orderedFieldIds).equals(new java.util.HashSet<>(currentIds))) {
      throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "排序必须包含该过程当前全部已引用字段");
    }
    if (orderedFieldIds.equals(currentIds)) return;
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest("SEMANTIC_PROCESS_FIELD_REORDER", "Reorder business process fields",
            "SEMANTIC_PROCESS", String.valueOf(processId), process.code(), "APPLICATION",
            Map.of("from", currentIds.toString(), "to", orderedFieldIds.toString(), "operator", operator)));
    try {
      processFieldRepository.reorder(processId, orderedFieldIds);
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
          "Business process fields reordered", Map.of("fieldIds", orderedFieldIds.toString()),
          "Business process fields reordered");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_FIELD_REORDER_FAILED", exception);
      throw exception;
    }
  }

  /** 34 挂点:业务过程删除前校验存在字段引用。 */
  public void assertProcessDeletable(Long processId) {
    if (processFieldRepository.countByProcess(processId) > 0) {
      throw new SemanticException(SemanticErrorCode.PROCESS_REFERENCED, "存在标准字段引用");
    }
  }

  /** Required enabled TYPE standard reference; its std_type supplies the data type snapshot. */
  private Standard validateTypeRef(Long stdTypeId) {
    if (stdTypeId == null) {
      throw new SemanticException(SemanticErrorCode.ROLE_FIELD_REQUIRED, "std_type_id");
    }
    Standard standard =
        standardRepository
            .findById(stdTypeId)
            .orElseThrow(
                () ->
                    new SemanticException(SemanticErrorCode.NOT_FOUND, "引用的类型标准不存在"));
    if (standard.kind() != StandardKind.TYPE) {
      throw new SemanticException(
          SemanticErrorCode.INVALID_KIND, "std_type_id 必须引用 TYPE 类别标准");
    }
    if (standard.status() != StandardStatus.ENABLED) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, "类型标准已停用");
    }
    return standard;
  }

  /** 决策一:码集编码非空时,项目内必须存在 ENABLED 且 code_set_code 匹配的 CODE 行。 */
  private void validateCodeSet(String codeSetCode) {
    if (!StringUtils.hasText(codeSetCode)) {
      return;
    }
    if (!standardRepository.existsEnabledByCodeSet(codeSetCode)) {
      throw new SemanticException(
          SemanticErrorCode.CODE_SET_NOT_FOUND, "码集不存在或已停用：" + codeSetCode);
    }
  }

  /** 收紧 1:引用类型标准时 data_type 服务端覆盖;未引用时保留手填。 */
  private static String resolveDataType(Standard typeStandard, String handFilled) {
    if (typeStandard != null && StringUtils.hasText(typeStandard.fields().stdType())) {
      return typeStandard.fields().stdType();
    }
    return handFilled;
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new SemanticException(SemanticErrorCode.INVALID_NAME, "字段名称不能为空");
    }
  }

  private static void validateRole(String role) {
    if (!StandardField.isValidRole(role)) {
      throw new SemanticException(SemanticErrorCode.INVALID_KIND, "字段角色必须为 PROCESS/DIMENSION/METRIC");
    }
  }

  /** 归零后的角色引用集合(2026-09-16):仅保留当前角色有意义的引用。 */
  private record RoleRefs(Long stdUnitId, Long stdCaliberId, String stdCodeSetCode, Long stdSecurityId) {}

  /**
   * 按角色适配:归零当前角色无意义的引用并校验角色必填项——
   * METRIC 必填单位;口径选填(作为字段默认推荐口径,指标可覆盖,2026-09-17),
   * 非空时校验类别/存在/启用;清空码集/安全。
   * DIMENSION 清空单位/口径,码集/安全可选;PROCESS 仅保留类型引用。
   * 前端隐藏即清空,此处为服务端兜底,避免脏引用入库。
   */
  private static RoleRefs normalizeRoleRefs(
      String role, Long stdUnitId, Long stdCaliberId, String stdCodeSetCode, Long stdSecurityId) {
    return switch (role) {
      case "METRIC" -> new RoleRefs(stdUnitId, stdCaliberId, null, null);
      case "DIMENSION" -> new RoleRefs(null, null, stdCodeSetCode, stdSecurityId);
      default -> new RoleRefs(null, null, null, null);
    };
  }

  /** 口径选填但非空时必须指向存在且启用的 CALIBER 标准(2026-09-17)。 */
  private void validateCaliberRef(Long stdCaliberId) {
    if (stdCaliberId == null) {
      return;
    }
    Standard standard =
        standardRepository
            .findById(stdCaliberId)
            .orElseThrow(
                () ->
                    new SemanticException(
                        SemanticErrorCode.NOT_FOUND, "口径标准不存在或已停用"));
    if (standard.kind() != StandardKind.CALIBER) {
      throw new SemanticException(
          SemanticErrorCode.INVALID_KIND, "std_caliber_id 必须引用 CALIBER 类别标准");
    }
    if (standard.status() != StandardStatus.ENABLED) {
      throw new SemanticException(
          SemanticErrorCode.NOT_FOUND, "口径标准不存在或已停用");
    }
  }

  private void validateUnitRef(Long stdUnitId, String role) {
    if ("METRIC".equals(role) && stdUnitId == null) {
      throw new SemanticException(SemanticErrorCode.ROLE_FIELD_REQUIRED, "std_unit_id");
    }
    if (stdUnitId == null) return;
    Standard standard = standardRepository.findById(stdUnitId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "单位标准不存在或已停用"));
    if (standard.kind() != StandardKind.UNIT) {
      throw new SemanticException(SemanticErrorCode.INVALID_KIND, "std_unit_id 必须引用 UNIT 类别标准");
    }
    if (standard.status() != StandardStatus.ENABLED) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, "单位标准已停用");
    }
  }

  private void validateSecurityRef(Long stdSecurityId) {
    if (stdSecurityId == null) return;
    Standard standard = standardRepository.findById(stdSecurityId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "安全标准不存在或已停用"));
    if (standard.kind() != StandardKind.SECURITY) {
      throw new SemanticException(SemanticErrorCode.INVALID_KIND, "std_security_id 必须引用 SECURITY 类别标准");
    }
    if (standard.status() != StandardStatus.ENABLED) {
      throw new SemanticException(SemanticErrorCode.INVALID_STATUS, "安全标准已停用");
    }
  }
}

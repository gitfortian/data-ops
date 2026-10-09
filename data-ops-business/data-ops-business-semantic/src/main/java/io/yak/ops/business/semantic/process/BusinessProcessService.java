package io.yak.ops.business.semantic.process;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.SemanticStructureReferenceReader;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns business-process rules: code format/uniqueness, domain existence,
 * delete blocking on references (field sets arrive with 35, source bindings
 * with 36 — hooks marked below).
 */
@Component
public class BusinessProcessService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  private final SemanticProcessRepository repository;
  private final SemanticDomainRepository domainRepository;
  private final SemanticFieldService fieldService;
  private final SemanticProcessBindingService bindingService;
  private final BusinessAuditService auditService;
  private final List<SemanticStructureReferenceReader> referenceReaders;

  @Autowired
  public BusinessProcessService(
      SemanticProcessRepository repository,
      SemanticDomainRepository domainRepository,
      SemanticFieldService fieldService,
      SemanticProcessBindingService bindingService,
      BusinessAuditService auditService,
      ObjectProvider<SemanticStructureReferenceReader> referenceReaders) {
    this(repository, domainRepository, fieldService, bindingService, auditService,
        referenceReaders.orderedStream().toList());
  }

  /** Compatibility constructor for existing focused service tests. */
  public BusinessProcessService(
      SemanticProcessRepository repository,
      SemanticDomainRepository domainRepository,
      SemanticFieldService fieldService,
      SemanticProcessBindingService bindingService,
      BusinessAuditService auditService) {
    this(repository, domainRepository, fieldService, bindingService, auditService, List.of());
  }

  BusinessProcessService(
      SemanticProcessRepository repository,
      SemanticDomainRepository domainRepository,
      SemanticFieldService fieldService,
      SemanticProcessBindingService bindingService,
      BusinessAuditService auditService,
      List<SemanticStructureReferenceReader> referenceReaders) {
    this.repository = repository;
    this.domainRepository = domainRepository;
    this.fieldService = fieldService;
    this.bindingService = bindingService;
    this.auditService = auditService;
    this.referenceReaders = List.copyOf(referenceReaders);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public BusinessProcess create(
      Long domainId,
      String code,
      String name,
      String grain,
      String bizType,
      String owner,
      String description,
      Integer sortOrder,
      String operator) {
    validateCode(code);
    validateName(name);
    validateBizType(bizType);
    if (domainId == null || domainRepository.findById(domainId).isEmpty()) {
      throw new SemanticException(SemanticErrorCode.NOT_FOUND, "所属业务域不存在");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PROCESS_CREATE",
                "Create business process",
                "SEMANTIC_PROCESS",
                null,
                code,
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByCode(code)) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, code);
      }
      BusinessProcess inserted =
          repository.insert(
              new BusinessProcess(null, code, name, domainId, grain, bizType, owner, description,
                  sortOrder == null ? 0 : sortOrder, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Business process created",
          Map.of(),
          "Business process created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_CREATE_FAILED", exception);
      throw exception;
    }
  }

  public BusinessProcess get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public io.yak.framework.common.PageData<BusinessProcess> page(
      int pageNo, int pageSize, Long domainId, String keyword, String bizType) {
    return repository.page(pageNo, pageSize, domainId, keyword, bizType);
  }

  public List<BusinessProcess> listByDomain(Long domainId) {
    return repository.listByDomain(domainId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(
      Long id,
      String name,
      Long domainId,
      String grain,
      String bizType,
      String owner,
      String description,
      Integer sortOrder) {
    BusinessProcess existing = get(id);
    validateName(name);
    validateBizType(bizType);
    if (domainId == null || domainRepository.findById(domainId).isEmpty()) {
      throw new SemanticException(SemanticErrorCode.NOT_FOUND, "所属业务域不存在");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PROCESS_UPDATE",
                "Update business process",
                "SEMANTIC_PROCESS",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.update(
          new BusinessProcess(existing.id(), existing.code(), name, domainId, grain, bizType,
              owner, description, sortOrder == null ? existing.sortOrder() : sortOrder,
              existing.createdBy(), existing.createTime(), existing.updateTime()))) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND,
            "业务过程已删除或项目发生变化，请刷新后重试");
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Business process updated",
          Map.of(),
          "Business process updated");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    BusinessProcess existing = get(id);
    // 引用校验挂点:35(标准字段集引用)/36(源表关联引用)落地后在此阻断。
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PROCESS_DELETE",
                "Delete business process",
                "SEMANTIC_PROCESS",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      // 引用校验(35/36 挂点):被标准字段或源表关联引用时阻断。
      fieldService.assertProcessDeletable(id);
      bindingService.assertProcessDeletable(id);
      // Modeling owns model.processId; semantic must not read Modeling tables.
      // Fail closed when the consumer count cannot be queried.
      for (SemanticStructureReferenceReader reader : referenceReaders) {
        if (reader.countProcessReferences(id) > 0) {
          throw new SemanticException(SemanticErrorCode.PROCESS_REFERENCED,
              "存在建模或其它消费模块引用，请先解除关联");
        }
      }
      if (!repository.deleteById(id)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Business process deleted",
          Map.of(),
          "Business process deleted");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_DELETE_FAILED", exception);
      throw exception;
    }
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new SemanticException(SemanticErrorCode.INVALID_NAME, "业务过程名称不能为空");
    }
  }

  private static void validateBizType(String bizType) {
    if (!"FACT".equals(bizType) && !"DIMENSION".equals(bizType)) {
      throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "业务过程类型必须为 FACT 或 DIMENSION");
    }
  }
}

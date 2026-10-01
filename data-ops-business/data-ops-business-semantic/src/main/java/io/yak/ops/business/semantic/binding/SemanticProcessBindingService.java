package io.yak.ops.business.semantic.binding;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.connection.DataSourceConnectionTester;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessSourceRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns process-source binding rules: datasource connectivity is validated at
 * bind time through the datasource public contract (DataSourceConnectionTester);
 * failed connectivity refuses the binding.
 */
@Component
public class SemanticProcessBindingService {

  private final SemanticProcessSourceRepository repository;
  private final SemanticProcessRepository processRepository;
  private final DataSourceConnectionTester connectionTester;
  private final BusinessAuditService auditService;

  public SemanticProcessBindingService(
      SemanticProcessSourceRepository repository,
      SemanticProcessRepository processRepository,
      DataSourceConnectionTester connectionTester,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.processRepository = processRepository;
    this.connectionTester = connectionTester;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ProcessSourceBinding bind(
      Long processId,
      Long datasourceId,
      String sourceTable,
      String tableRole,
      String joinCondition,
      String operator) {
    processRepository
        .findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    if (datasourceId == null || datasourceId <= 0) {
      throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "必须选择数据源");
    }
    if (!StringUtils.hasText(sourceTable)) {
      throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "必须填写源表名");
    }
    String resolvedRole = tableRole == null || tableRole.isBlank() ? "MAIN" : tableRole;
    if (!ProcessSourceBinding.isValidTableRole(resolvedRole)) {
      throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "表角色必须为 MAIN/DETAIL/DIM");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PROCESS_SOURCE_BIND",
                "Bind process source table",
                "SEMANTIC_PROCESS_SOURCE",
                String.valueOf(processId),
                sourceTable,
                "APPLICATION",
                Map.of("datasourceId", String.valueOf(datasourceId))));
    try {
      // 连通性校验(经 datasource 公共契约):失败拒绝绑定。
      if (!connectionTester.testSaved(datasourceId)) {
        throw new SemanticException(SemanticErrorCode.INVALID_SEARCH, "数据源连通性校验失败，无法绑定");
      }
      ProcessSourceBinding inserted =
          repository.insert(
              new ProcessSourceBinding(null, processId, datasourceId, sourceTable, resolvedRole,
                  joinCondition, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Process source bound",
          Map.of(),
          "Process source bound");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_SOURCE_BIND_FAILED", exception);
      throw exception;
    }
  }

  public List<ProcessSourceBinding> listByProcess(Long processId) {
    processRepository
        .findById(processId)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "业务过程不存在"));
    return repository.listByProcess(processId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void unbind(Long processId, Long bindingId) {
    if (listByProcess(processId).stream().noneMatch(binding -> binding.id().equals(bindingId))) {
      throw new SemanticException(SemanticErrorCode.NOT_FOUND, "此业务过程不存在该源表关联");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PROCESS_SOURCE_UNBIND",
                "Unbind process source table",
                "SEMANTIC_PROCESS_SOURCE",
                String.valueOf(bindingId),
                null,
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(bindingId)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(bindingId));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Process source unbound",
          Map.of(),
          "Process source unbound");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PROCESS_SOURCE_UNBIND_FAILED", exception);
      throw exception;
    }
  }

  /** 34 挂点:业务过程删除前校验存在源表关联。 */
  public void assertProcessDeletable(Long processId) {
    if (repository.existsByProcess(processId)) {
      throw new SemanticException(SemanticErrorCode.STANDARD_REFERENCED, "存在源表关联");
    }
  }
}

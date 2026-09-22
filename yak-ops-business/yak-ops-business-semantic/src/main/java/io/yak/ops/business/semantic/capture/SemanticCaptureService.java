package io.yak.ops.business.semantic.capture;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticStandardApi;
import io.yak.ops.business.semantic.api.StandardCaptureApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default capture implementation (ticket 40): reuses the catalog service for
 * validation/audit so a captured standard is indistinguishable from a
 * hand-created one. Duplicate (kind, code) is idempotent (created=false).
 */
@Component
public class SemanticCaptureService implements StandardCaptureApi {

  private final StandardCatalogService catalogService;
  private final SemanticStandardRepository standardRepository;
  private final BusinessAuditService auditService;

  public SemanticCaptureService(
      StandardCatalogService catalogService,
      SemanticStandardRepository standardRepository,
      BusinessAuditService auditService) {
    this.catalogService = catalogService;
    this.standardRepository = standardRepository;
    this.auditService = auditService;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public CaptureResult capture(CaptureRequest request) {
    StandardKind kind =
        StandardKind.fromStored(request.kind())
            .orElseThrow(() -> new SemanticException(SemanticErrorCode.INVALID_KIND, request.kind()));
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_STANDARD_CAPTURE",
                "Capture to standard",
                "SEMANTIC_STANDARD",
                null,
                request.code(),
                "APPLICATION",
                captureMetadata(request)));
    try {
      Standard existing = standardRepository.findByCode(kind, request.code()).orElse(null);
      if (existing != null) {
        AuditTransactions.completeOnCommit(
            audit,
            AuditEventType.OPERATION_SUCCEEDED,
            "Capture skipped: standard already exists",
            Map.of("created", "false"),
            "Capture skipped: standard already exists");
        return new CaptureResult(
            existing.id(),
            existing.code(),
            existing.name(),
            false,
            "同名标准已存在，未重复创建（可选择并入）");
      }
      Standard created =
          catalogService.create(toCreateRequest(kind, request), captureOperator(request));
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Standard captured",
          Map.of("created", "true", "standardId", String.valueOf(created.id())),
          "Standard captured");
      return new CaptureResult(created.id(), created.code(), created.name(), true, "沉淀成功");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_STANDARD_CAPTURE_FAILED", exception);
      throw exception;
    }
  }

  private static Map<String, ?> captureMetadata(CaptureRequest request) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("kind", request.kind());
    if (request.sourceModelRef() != null) {
      metadata.put("sourceModelRef", request.sourceModelRef());
    }
    if (request.sourceColumn() != null) {
      metadata.put("sourceColumn", request.sourceColumn());
    }
    return metadata;
  }

  private static String captureOperator(CaptureRequest request) {
    return request.sourceColumn() == null ? "capture" : "capture:" + request.sourceColumn();
  }

  private static SemanticStandardApi.CreateRequest toCreateRequest(
      StandardKind kind, CaptureRequest request) {
    boolean isNaming = kind == StandardKind.NAMING;
    String scope = isNaming ? "FIELD" : null;
    return new SemanticStandardApi.CreateRequest(
        kind.name(),
        request.code(),
        request.name(),
        null,
        null,
        scope,
        null,
        request.ruleExpr(),
        null,
        request.typeCode(),
        request.stdType(),
        request.sourceMapping(),
        request.codeSetCode(),
        request.codeValue(),
        request.codeLabel(),
        request.unitCode(),
        request.unitType(),
        request.caliberCode(),
        request.calRule(),
        request.businessDesc(),
        request.levelCode(),
        request.maskRule());
  }
}

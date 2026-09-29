package io.yak.ops.business.semantic.preset;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.repository.SemanticPresetTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.business.audit.AuditTransactions;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies platform preset templates into the current project's standard
 * catalog. Idempotent by (kind, std_code): templates whose code already
 * exists in the project are skipped, so repeated runs never duplicate rows
 * (REQUIREMENTS.md, ticket 31).
 */
@Component
public class SemanticPresetService {

  private final SemanticPresetTemplateRepository templateRepository;
  private final SemanticStandardRepository standardRepository;
  private final BusinessAuditService auditService;

  public SemanticPresetService(
      SemanticPresetTemplateRepository templateRepository,
      SemanticStandardRepository standardRepository,
      BusinessAuditService auditService) {
    this.templateRepository = templateRepository;
    this.standardRepository = standardRepository;
    this.auditService = auditService;
  }

  /** Whether the project already holds any standard row (empty-state button gate). */
  public boolean initialized() {
    return standardRepository.countByProject() > 0;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int initialize(String operator) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_PRESET_INITIALIZE",
                "Initialize preset standards",
                "SEMANTIC_STANDARD",
                null,
                "preset",
                "APPLICATION",
                Map.of()));
    try {
      int created = 0;
      for (PresetTemplate template : templateRepository.findAll()) {
        if (standardRepository.existsByCode(template.kind(), template.code())) {
          continue;
        }
        standardRepository.insert(
            new Standard(
                null,
                template.kind(),
                template.code(),
                template.name(),
                StandardStatus.ENABLED,
                1,
                template.sortOrder(),
                true,
                template.description(),
                template.fields(),
                operator,
                null,
                null),
            operator);
        created++;
      }
      Map<String, String> metadata = Map.of("created", String.valueOf(created));
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Preset standards initialized",
          metadata,
          "Preset standards initialized");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_PRESET_INITIALIZE_FAILED", exception);
      throw exception;
    }
  }
}

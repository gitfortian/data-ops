package io.yak.ops.business.lineage.registration;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterAssetCommand;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterRelationCommand;
import io.yak.ops.business.lineage.repository.LineageRepository;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates application commands and converts them into persistence-neutral domain drafts. */
@Component
public class LineageRegistrationDraftFactory {

  private final LineageRepository repository;
  private final CurrentProject currentProject;

  @Autowired
  public LineageRegistrationDraftFactory(
      LineageRepository repository, CurrentProject currentProject) {
    this.repository = repository;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused unit tests. */
  public LineageRegistrationDraftFactory(LineageRepository repository) {
    this(repository, Optional::<ProjectContext>empty);
  }

  public LineageAssetDraft asset(RegisterAssetCommand command, boolean validateParent) {
    Objects.requireNonNull(command, "command");
    String assetKey = required(command.assetKey(), "assetKey", 512);
    LineageAssetType assetType = Objects.requireNonNull(command.assetType(), "assetType");
    String name = optional(command.name(), 200);
    if (name == null) name = assetKey;
    Long projectId = resolveSourceProject(command.sourceProjectId());
    Long parentAssetId = command.parentAssetId();
    if (parentAssetId != null) {
      requirePositive(parentAssetId, "parentAssetId");
      if (validateParent) {
        LineageAsset parent = requireAsset(parentAssetId);
        projectId = reconcileProject(projectId, parent.projectId());
      }
    }
    return new LineageAssetDraft(
        projectId,
        assetKey,
        assetType,
        name,
        valueOrEmpty(command.sourceType(), 64),
        valueOrEmpty(command.sourceId(), 200),
        parentAssetId,
        optional(command.dataSourceId(), 64),
        optional(command.databaseName(), 256),
        optional(command.schemaName(), 256),
        optional(command.tableName(), 256),
        optional(command.columnName(), 256),
        command.properties());
  }

  public LineageRelationDraft relation(
      RegisterRelationCommand command, boolean validateAssets) {
    Objects.requireNonNull(command, "command");
    requirePositive(command.sourceAssetId(), "sourceAssetId");
    requirePositive(command.targetAssetId(), "targetAssetId");
    if (command.sourceAssetId() == command.targetAssetId()) {
      throw new IllegalArgumentException("血缘关系不能指向资产自身");
    }
    Long projectId = resolveSourceProject(command.sourceProjectId());
    if (validateAssets) {
      LineageAsset source = requireAsset(command.sourceAssetId());
      LineageAsset target = requireAsset(command.targetAssetId());
      projectId = reconcileProject(projectId, source.projectId());
      projectId = reconcileProject(projectId, target.projectId());
    }
    LineageRelationType type =
        Objects.requireNonNull(command.relationType(), "relationType");
    BigDecimal confidence =
        command.confidence() == null ? BigDecimal.ONE : command.confidence();
    if (confidence.compareTo(BigDecimal.ZERO) < 0
        || confidence.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
    }
    return new LineageRelationDraft(
        projectId,
        command.sourceAssetId(),
        command.targetAssetId(),
        type,
        valueOrEmpty(command.sourceType(), 64),
        valueOrEmpty(command.sourceId(), 200),
        optional(command.expression(), 16000),
        confidence,
        valueOrEmpty(command.version(), 128),
        command.observedAt() == null ? Instant.now() : command.observedAt(),
        command.properties());
  }

  private Long resolveSourceProject(Long sourceProjectId) {
    Long normalized = normalizeProjectId(sourceProjectId);
    return currentProject.current()
        .map(
            context -> {
              if (normalized != null && !Objects.equals(context.projectId(), normalized)) {
                throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
              }
              return context.projectId();
            })
        .orElse(normalized);
  }

  private Long reconcileProject(Long expected, Long actual) {
    Long normalizedActual = normalizeProjectId(actual);
    if (expected == null) return normalizedActual;
    if (normalizedActual == null) return expected;
    if (!Objects.equals(expected, normalizedActual)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return expected;
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  private LineageAsset requireAsset(long assetId) {
    requirePositive(assetId, "assetId");
    return repository.findAsset(assetId)
        .orElseThrow(() -> new IllegalArgumentException("血缘资产不存在：" + assetId));
  }

  static void requireBatchSize(int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("batchSize 必须大于 0");
  }

  private static long requirePositive(long value, String field) {
    if (value <= 0) throw new IllegalArgumentException(field + " 必须大于 0");
    return value;
  }

  private static String required(String value, String field, int maxLength) {
    String normalized = optional(value, maxLength);
    if (normalized == null) throw new IllegalArgumentException(field + " 不能为空");
    return normalized;
  }

  private static String valueOrEmpty(String value, int maxLength) {
    String normalized = optional(value, maxLength);
    return normalized == null ? "" : normalized;
  }

  private static String optional(String value, int maxLength) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("字段长度不能超过 " + maxLength);
    }
    return normalized;
  }
}

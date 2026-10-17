package io.yak.ops.business.development.handoff;

import io.yak.ops.business.development.task.DevelopmentPublishedSqlRevisionEvidenceService;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.modeling.PublishedDwdDesignEvidenceProvider;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * GATED DRAFT. Implements the DataDev-owned immutable binding transaction,
 * but cannot be invoked in production until DR-07 Owner signoff; the only
 * HTTP command is disabled by default and this branch is not merged.
 *
 * This is NOT authority to run an SQL task or claim target reconciliation.
 */
@Component
@ConditionalOnProperty(prefix = "data-ops.dwd", name = "binding-write-authorized",
    havingValue = "true", matchIfMissing = false)
public class DevelopmentModelTaskBindingCoordinator {
  private final CurrentProject currentProject;
  private final PublishedDwdDesignEvidenceProvider modeling;
  private final DevelopmentPublishedSqlRevisionEvidenceService revisions;
  private final DevelopmentModelTaskBindingRepository repository;

  public DevelopmentModelTaskBindingCoordinator(
      CurrentProject currentProject,
      PublishedDwdDesignEvidenceProvider modeling,
      DevelopmentPublishedSqlRevisionEvidenceService revisions,
      DevelopmentModelTaskBindingRepository repository) {
    this.currentProject = currentProject;
    this.modeling = modeling;
    this.revisions = revisions;
    this.repository = repository;
  }

  public record BindCommand(
      long modelId, long modelVersionId, int modelVersionNo,
      long mappingRevisionId, String mappingSha256,
      long nodeId, long taskRevisionId, int taskRevisionNo,
      String taskDefinitionSha256,
      long targetDatasourceId, String targetDatabase,
      String targetSchema, String targetTable,
      String writeMode, String idempotencyKey,
      boolean confirmed) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentModelTaskBinding bind(BindCommand cmd) {
    validateRequest(cmd);
    long projectId = currentProject.requireProjectId();
    // One exact node lock serializes idempotent retries and binding conflicts.
    repository.lockNode(cmd.nodeId());
    var evidence = modeling.inspect(cmd.modelId(), cmd.modelVersionNo());
    if (evidence.projectId() != projectId
        || evidence.modelId() != cmd.modelId()
        || evidence.modelVersionId() != cmd.modelVersionId()
        || evidence.modelVersionNo() != cmd.modelVersionNo()
        || evidence.mappingRevisionId() != cmd.mappingRevisionId()
        || !Objects.equals(evidence.mappingContentSha256(), cmd.mappingSha256())
        || !evidence.reviewable()
        || !"REVIEWABLE_ONLY".equals(evidence.status())
        || !validSha(evidence.logicalSnapshotSha256())
        || !validSha(evidence.physicalStructureSha256())
        || !validSha(evidence.currentStandardEvidenceSha256())) {
      throw new IllegalStateException("DWD 精确版本、冻结映射或当前标准证据已失效");
    }
    var published = revisions.inspect(cmd.nodeId(), cmd.taskRevisionNo());
    if (!String.valueOf(cmd.nodeId()).equals(published.nodeId())
        || !String.valueOf(cmd.taskRevisionId()).equals(published.taskRevisionId())
        || published.taskRevisionNo() != cmd.taskRevisionNo()
        || !Objects.equals(published.immutableDefinitionSha256(),
            cmd.taskDefinitionSha256())
        || !"PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED".equals(published.status())
        || published.authorizedForFrozenExecution()) {
      throw new IllegalStateException("DataDev 持久 SQL TaskRevision 身份或 SHA 已变化");
    }
    String digest = VersionDigests.sha256Fields(
        String.valueOf(projectId), String.valueOf(cmd.modelId()),
        String.valueOf(cmd.modelVersionId()), String.valueOf(cmd.modelVersionNo()),
        String.valueOf(cmd.mappingRevisionId()), cmd.mappingSha256(),
        evidence.logicalSnapshotSha256(), evidence.physicalStructureSha256(),
        String.valueOf(cmd.nodeId()), String.valueOf(cmd.taskRevisionId()),
        String.valueOf(cmd.taskRevisionNo()), cmd.taskDefinitionSha256(),
        String.valueOf(cmd.targetDatasourceId()), cmd.targetDatabase().trim(),
        normalizeSchema(cmd.targetSchema()), cmd.targetTable().trim(), cmd.writeMode());

    var existing = repository.byKey(cmd.idempotencyKey());
    if (existing.isPresent()) {
      if (!digest.equals(existing.get().bindingDigest())) {
        throw new IllegalStateException("幂等键已用于其他 DWD/TaskRevision/目标绑定，拒绝覆盖");
      }
      return existing.get();
    }
    var prior = repository.byRevision(cmd.nodeId(), cmd.taskRevisionId());
    if (prior.isPresent()) {
      throw new IllegalStateException("该已发布 SQL 修订已绑定另一项业务版本，须发布新 TaskRevision");
    }
    var candidate = new DevelopmentModelTaskBinding(
        null, projectId, evidence.modelId(), evidence.modelVersionId(),
        evidence.modelVersionNo(), evidence.mappingRevisionId(),
        evidence.mappingContentSha256(), evidence.logicalSnapshotSha256(),
        evidence.physicalStructureSha256(), evidence.currentStandardEvidenceSha256(),
        cmd.nodeId(), cmd.taskRevisionId(), cmd.taskRevisionNo(),
        cmd.taskDefinitionSha256(), cmd.targetDatasourceId(),
        cmd.targetDatabase().trim(), normalizeSchema(cmd.targetSchema()),
        cmd.targetTable().trim(), cmd.writeMode(), digest, cmd.idempotencyKey(),
        "BINDING_RECORDED_NOT_EXECUTABLE");
    return repository.insert(candidate);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public DevelopmentModelTaskBinding get(long bindingId) {
    if (bindingId <= 0) throw new IllegalArgumentException("绑定 ID 非法");
    return repository.byId(bindingId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有指定的 DataDev 绑定"));
  }

  private static void validateRequest(BindCommand cmd) {
    if (cmd == null || !cmd.confirmed()
        || cmd.modelId() <= 0 || cmd.modelVersionId() <= 0
        || cmd.modelVersionNo() <= 0 || cmd.mappingRevisionId() <= 0
        || !validSha(cmd.mappingSha256())
        || cmd.nodeId() <= 0 || cmd.taskRevisionId() <= 0
        || cmd.taskRevisionNo() <= 0 || !validSha(cmd.taskDefinitionSha256())
        || cmd.targetDatasourceId() <= 0
        || invalidName(cmd.targetDatabase()) || invalidName(cmd.targetTable())
        || (cmd.targetSchema() != null && cmd.targetSchema().length() > 256)
        || !Set.of("INSERT", "OVERWRITE_PARTITION").contains(cmd.writeMode())
        || !validUuid(cmd.idempotencyKey())) {
      throw new IllegalArgumentException(
          "缺少人工确认、精确模型/任务修订身份、有效目标信息或唯一幂等键");
    }
  }

  private static boolean validUuid(String value) {
    if (value == null || value.length() != 36) return false;
    try { return UUID.fromString(value).toString().equals(value.toLowerCase()); }
    catch (IllegalArgumentException exception) { return false; }
  }

  private static String normalizeSchema(String value) {
    return value == null ? null : value.trim();
  }

  private static boolean invalidName(String value) {
    return value == null || value.isBlank() || value.length() > 256
        || !value.equals(value.trim());
  }

  private static boolean validSha(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }
}

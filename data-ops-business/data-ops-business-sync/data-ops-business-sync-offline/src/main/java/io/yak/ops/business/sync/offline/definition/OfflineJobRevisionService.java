package io.yak.ops.business.sync.offline.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditActor;
import io.yak.ops.business.audit.AuditActorResolver;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobRevisionRepository;
import io.yak.ops.business.sync.offline.domain.OfflineJobRevision;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 离线同步任务的发布快照存储（内部角色件，对外经 OfflineJobDefinitionService 暴露）。
 *
 * <p>不变量：主表 definition_json/job_spec_json=可编辑草稿；{@code yak_offline_job_revision}=
 * 发布全量快照(append-only)；执行认领/调度/工作流一律经 {@code published_revision_id}
 * 读快照——改草稿不发布，线上执行仍是旧版本内容。
 */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineJobRevisionService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final CurrentProject currentProject;
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobRevisionRepository revisionRepository;
  private final BusinessAuditService auditService;
  private final AuditActorResolver auditActorResolver;

  /** 发布结果：appended=false 表示内容与最新发布版一致，幂等复用不追加。 */
  public record PublishResult(VersionSummary version, boolean appended) {}

  public record VersionSummary(
      Long id, int versionNo, String checksum, String createdBy,
      LocalDateTime createTime, boolean current) {}

  /** 版本详情：payload 供前端 diff（definition/jobSpec 解析为对象便于逐字段对比）。 */
  public record RevisionDetailView(VersionSummary version, Map<String, Object> payload) {}

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public PublishResult publish(Long id) {
    OfflineJobDefinition definition = requireDefinition(id);
    return publishLoaded(definition, resolveOperator(), "OFFLINE_JOB_PUBLISH", "发布");
  }

  /** 幂等辅助：上线时若从未发布但已有可执行配置，自动补发 v1（存量行为不变）。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void publishIfNeededOnOnline(OfflineJobDefinition definition, String operator) {
    if (definition.getPublishedRevisionId() != null
        || !StringUtils.hasText(definition.getJobSpecJson())) {
      return;
    }
    publishLoaded(definition, operator, "OFFLINE_JOB_AUTO_PUBLISH_ONLINE", "上线自动发布");
  }

  public List<VersionSummary> versions(Long id) {
    OfflineJobDefinition definition = requireDefinition(id);
    List<OfflineJobRevision> rows =
        revisionRepository.findAllByJobDefinitionId(definition.getId());
    return rows.stream()
        .map(row -> toSummary(row, definition.getPublishedRevisionId()))
        .toList();
  }

  public RevisionDetailView versionDetail(Long id, int versionNo) {
    OfflineJobDefinition definition = requireDefinition(id);
    OfflineJobRevision row =
        revisionRepository
            .findByJobDefinitionIdAndVersionNo(definition.getId(), versionNo)
            .orElse(null);
    if (row == null) {
      throw new IllegalArgumentException(
          "发布版本不存在：job=" + id + ", versionNo=" + versionNo);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("definition", parseJson(row.getDefinitionJson()));
    payload.put("jobSpec", parseJson(row.getJobSpecJson()));
    payload.put("configDigest", row.getConfigDigest());
    return new RevisionDetailView(toSummary(row, definition.getPublishedRevisionId()), payload);
  }

  /** 回滚=用目标版内容覆盖草稿并立即发布（追加式 activate，不抹历史）。 */
  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public PublishResult rollback(Long id, int versionNo) {
    OfflineJobDefinition definition = requireDefinition(id);
    OfflineJobRevision target =
        revisionRepository
            .findByJobDefinitionIdAndVersionNo(definition.getId(), versionNo)
            .orElse(null);
    if (target == null) {
      throw new IllegalArgumentException(
          "发布版本不存在：job=" + id + ", versionNo=" + versionNo);
    }
    Map<String, Object> beforeDraft = draftSnapshot(definition);

    definition.setDefinitionJson(target.getDefinitionJson());
    definition.setJobSpecJson(target.getJobSpecJson());
    definition.setConfigDigest(target.getConfigDigest());
    definition.setVersion(Math.max(0, Objects.requireNonNullElse(definition.getVersion(), 0)) + 1);
    definition.setUpdateTime(LocalDateTime.now());
    definitionRepository.update(definition);

    PublishResult result =
        publishLoaded(definition, resolveOperator(), "OFFLINE_JOB_ROLLBACK", "回滚");
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "OFFLINE_JOB_ROLLBACK_DRAFT", "Rollback overwrote draft", "OFFLINE_JOB",
        String.valueOf(id), definition.getJobName(), "APPLICATION",
        Map.of("discardedDraft", beforeDraft)));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "回滚覆盖了回滚前的草稿内容 " + definition.getJobName(),
        Map.of("jobId", id, "rollbackToVersionNo", versionNo), null);
    return result;
  }

  /** 执行/调度/工作流读路径：当前发布快照；未发布返回 null。 */
  public OfflineJobRevision publishedRevision(OfflineJobDefinition definition) {
    if (definition == null || definition.getPublishedRevisionId() == null) {
      return null;
    }
    OfflineJobRevision row =
        revisionRepository.findById(definition.getPublishedRevisionId()).orElse(null);
    if (row == null || !Objects.equals(row.getJobDefinitionId(), definition.getId())) {
      return null;
    }
    return row;
  }

  /** 列表批量判定"草稿相对最新发布版是否有未发布修改"（每任务一条最新版本行，两查完成）。 */
  public Map<Long, Boolean> pendingDraftFlags(Collection<OfflineJobDefinition> definitions) {
    Map<Long, Boolean> flags = new HashMap<>();
    List<Long> ids = definitions.stream()
        .map(OfflineJobDefinition::getId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) return flags;
    Map<Long, OfflineJobRevision> latestByDefinitionId = new HashMap<>();
    revisionRepository.findLatestByJobDefinitionIds(ids).forEach(row ->
        latestByDefinitionId.put(row.getJobDefinitionId(), row));
    for (OfflineJobDefinition definition : definitions) {
      OfflineJobRevision latest = latestByDefinitionId.get(definition.getId());
      flags.put(definition.getId(), latest == null
          ? StringUtils.hasText(definition.getJobSpecJson()) || definition.getDefinitionJson() != null
          : !contentEquals(latest, definition));
    }
    return flags;
  }

  // ---------- internal ----------

  private PublishResult publishLoaded(
      OfflineJobDefinition definition, String operator, String auditType, String labelCn) {
    if (!StringUtils.hasText(definition.getJobSpecJson())) {
      throw new IllegalStateException("任务仍是草稿，请先完成配置并保存后再发布");
    }
    long projectId = currentProject.requireProjectId();
    OfflineJobRevision latest =
        revisionRepository.findLatestByJobDefinitionId(definition.getId()).orElse(null);

    OfflineJobRevision revision;
    boolean appended = false;
    if (latest != null && contentEquals(latest, definition)) {
      revision = latest; // 幂等：内容未变不追加版本，仅移动发布指针
    } else {
      revision = new OfflineJobRevision();
      revision.setProjectId(projectId);
      revision.setJobDefinitionId(definition.getId());
      revision.setVersionNo(revisionRepository.nextVersionNo(definition.getId()));
      revision.setDefinitionJson(definition.getDefinitionJson());
      revision.setJobSpecJson(definition.getJobSpecJson());
      revision.setConfigDigest(definition.getConfigDigest());
      revision.setChecksum(VersionDigests.sha256Fields(
          definition.getDefinitionJson(), definition.getJobSpecJson()));
      revision.setSourceVersion(definition.getVersion());
      revision.setCreatedBy(operator);
      revisionRepository.insert(revision);
      appended = true;
    }

    definition.setPublishedRevisionId(revision.getId());
    definition.setLatestVersionNo(revision.getVersionNo());
    definition.setUpdateTime(LocalDateTime.now());
    definitionRepository.update(definition);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        auditType, labelCn + " offline sync job", "OFFLINE_JOB",
        String.valueOf(definition.getId()), definition.getJobName(), "APPLICATION",
        Map.of("versionNo", revision.getVersionNo())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        labelCn + "离线同步任务 " + definition.getJobName() + " v" + revision.getVersionNo(),
        Map.of("jobId", definition.getId(),
            "versionNo", revision.getVersionNo(),
            "appended", appended),
        null);
    return new PublishResult(toSummary(revision, revision.getId()), appended);
  }

  /** 内容等值=定义草稿与快照的 (configDigest, definitionJson) 双一致。 */
  static boolean contentEquals(OfflineJobRevision revision, OfflineJobDefinition definition) {
    return Objects.equals(revision.getConfigDigest(), definition.getConfigDigest())
        && Objects.equals(revision.getDefinitionJson(), definition.getDefinitionJson());
  }

  private OfflineJobDefinition requireDefinition(Long id) {
    return definitionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + id));
  }

  /** 本模块不直连认证框架，经 audit 的解析器取当前用户名（失败按系统操作）。 */
  private String resolveOperator() {
    try {
      AuditActor actor = auditActorResolver.currentActor();
      return actor == null ? null : actor.name();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  /** 回滚前草稿的轻量指纹(不落大字段全文,只留定位信息)。 */
  private static Map<String, Object> draftSnapshot(OfflineJobDefinition definition) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("version", definition.getVersion());
    snapshot.put("configDigest", definition.getConfigDigest());
    snapshot.put("publishedRevisionId", definition.getPublishedRevisionId());
    return snapshot;
  }

  private static Object parseJson(String json) {
    if (!StringUtils.hasText(json)) return null;
    try {
      return JSON.readValue(json, Object.class);
    } catch (Exception e) {
      return json;
    }
  }

  private static VersionSummary toSummary(OfflineJobRevision row, Long publishedRevisionId) {
    return new VersionSummary(row.getId(), row.getVersionNo(), row.getChecksum(),
        row.getCreatedBy(), row.getCreateTime(), Objects.equals(row.getId(), publishedRevisionId));
  }
}

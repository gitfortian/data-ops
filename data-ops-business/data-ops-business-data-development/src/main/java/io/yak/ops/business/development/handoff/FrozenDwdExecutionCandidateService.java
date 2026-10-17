package io.yak.ops.business.development.handoff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.development.task.DevelopmentPublishedSqlRevisionEvidenceService;
import io.yak.ops.business.development.task.DevelopmentTaskNodeResolver;
import io.yak.ops.business.development.task.DevelopmentTaskRevisionReader;
import io.yak.ops.business.development.task.TaskDefinitionDigestCalculator;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.modeling.PublishedDwdDesignEvidenceProvider;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only preparation for an immutable, exact DWD TaskRevision execution.
 * This class NEVER calls TaskExecutionGateway or creates an execution history.
 * Its snapshot is an internal value, not a permission or an executable command.
 */
@Component
@ConditionalOnProperty(prefix = "data-ops.dwd", name = "binding-write-authorized",
    havingValue = "true", matchIfMissing = false)
public class FrozenDwdExecutionCandidateService {
  private final CurrentProject projects;
  private final DevelopmentModelTaskBindingRepository bindings;
  private final PublishedDwdDesignEvidenceProvider modeling;
  private final DataSourceReader sources;
  private final DevelopmentTaskNodeResolver nodes;
  private final DevelopmentTaskRevisionReader revisions;
  private final DevelopmentPublishedSqlRevisionEvidenceService integrity;
  private final TaskDefinitionDigestCalculator digests;
  private final ObjectMapper objectMapper;

  public FrozenDwdExecutionCandidateService(
      CurrentProject projects,
      DevelopmentModelTaskBindingRepository bindings,
      PublishedDwdDesignEvidenceProvider modeling,
      DataSourceReader sources,
      DevelopmentTaskNodeResolver nodes,
      DevelopmentTaskRevisionReader revisions,
      DevelopmentPublishedSqlRevisionEvidenceService integrity,
      TaskDefinitionDigestCalculator digests,
      ObjectMapper objectMapper) {
    this.projects = projects;
    this.bindings = bindings;
    this.modeling = modeling;
    this.sources = sources;
    this.nodes = nodes;
    this.revisions = revisions;
    this.integrity = integrity;
    this.digests = digests;
    this.objectMapper = objectMapper;
  }

  /** The frozen snapshot is intentionally internal; the HTTP preview never returns SQL. */
  record Candidate(TaskVersionSnapshot frozenSnapshot, Preview preview) {}

  public record Preview(
      long bindingId,
      long modelVersionId,
      long mappingRevisionId,
      long taskRevisionId,
      int taskRevisionNo,
      String bindingDigest,
      String frozenTaskDefinitionSha256,
      String targetTable,
      boolean editorDiffersFromPublishedRevision,
      String status,
      boolean authorizedForExecution) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Preview inspect(long bindingId) {
    return prepare(bindingId).preview();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  Candidate prepare(long bindingId) {
    if (bindingId <= 0) throw new IllegalArgumentException("DWD 绑定 ID 必须为正整数");
    long projectId = projects.requireProjectId();
    var binding = bindings.byId(bindingId).orElseThrow(
        () -> new IllegalArgumentException("当前项目没有该 DWD 绑定"));
    if (binding.id() == null || binding.id() != bindingId
        || binding.projectId() != projectId
        || !"BINDING_RECORDED_NOT_EXECUTABLE".equals(binding.state())) {
      throw new IllegalStateException("DataDev 项目绑定身份或未执行状态非法");
    }
    var owner = modeling.inspect(binding.modelId(), binding.modelVersionNo());
    if (owner.projectId() != projectId
        || !owner.reviewable() || !"REVIEWABLE_ONLY".equals(owner.status())
        || owner.modelId() != binding.modelId()
        || owner.modelVersionId() != binding.modelVersionId()
        || owner.modelVersionNo() != binding.modelVersionNo()
        || owner.mappingRevisionId() != binding.mappingRevisionId()
        || !Objects.equals(owner.mappingContentSha256(), binding.mappingSha256())
        || !Objects.equals(owner.logicalSnapshotSha256(), binding.logicalSha256())
        || !Objects.equals(owner.physicalStructureSha256(), binding.physicalSha256())
        || !Objects.equals(owner.currentStandardEvidenceSha256(), binding.currentStandardSha256())
        || !Objects.equals(owner.targetTableCode(), binding.targetTable())) {
      throw new IllegalStateException("DWD 冻结版本或当前治理证据已变化，执行预演拒绝");
    }

    var source = sources.require(binding.targetDatasourceId());
    if (!Objects.equals(source.getProjectId(), projectId)
        || source.getConnStatus() != DataSourceConnStatus.CONNECTED) {
      throw new IllegalStateException("目标连接不存在、已撤权或状态不再有效");
    }
    var node = nodes.requireTaskNode(binding.nodeId());
    if (!Objects.equals(node.projectId(), projectId)
        || !"SQL".equalsIgnoreCase(node.type())) {
      throw new IllegalStateException("绑定 SQL 节点不属于当前项目");
    }
    var published = revisions.get(node, binding.taskRevisionNo());
    var validation = integrity.inspect(binding.nodeId(), binding.taskRevisionNo());
    if (published == null || published.id() == null
        || !Objects.equals(published.id(), binding.taskRevisionId())
        || !Objects.equals(published.nodeId(), binding.nodeId())
        || published.revisionNo() != binding.taskRevisionNo()
        || published.definition() == null
        || !"SQL".equals(published.definition().taskType())
        || !Objects.equals(digests.calculate(published.definition()), binding.taskDefinitionSha256())
        || !Objects.equals(validation.immutableDefinitionSha256(), binding.taskDefinitionSha256())
        || !Objects.equals(validation.taskRevisionId(), String.valueOf(binding.taskRevisionId()))
        || !Objects.equals(validation.nodeId(), String.valueOf(binding.nodeId()))
        || validation.authorizedForFrozenExecution()) {
      throw new IllegalStateException("绑定的不可变 SQL 修订与 DataDev 原域已不一致");
    }

    String digest = VersionDigests.sha256Fields(
        String.valueOf(projectId), String.valueOf(binding.modelId()),
        String.valueOf(binding.modelVersionId()), String.valueOf(binding.modelVersionNo()),
        String.valueOf(binding.mappingRevisionId()), binding.mappingSha256(),
        binding.logicalSha256(), binding.physicalSha256(), binding.currentStandardSha256(),
        String.valueOf(binding.nodeId()), String.valueOf(binding.taskRevisionId()),
        String.valueOf(binding.taskRevisionNo()), binding.taskDefinitionSha256(),
        String.valueOf(binding.targetDatasourceId()), binding.targetDatabase(),
        binding.targetSchema(), binding.targetTable(), binding.writeMode());
    if (!Objects.equals(digest, binding.bindingDigest())) {
      throw new IllegalStateException("持久绑定摘要被更改，拒绝构造执行快照");
    }

    try {
      // Version and config come ONLY from immutable published TaskRevision;
      // no mutable editor content, latest pointer, or request SQL is consulted.
      TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
          "development:" + node.id(), node.name(), published.definition().taskType(),
          published.revisionNo(), published.checksum(),
          objectMapper.writeValueAsString(published.definition()),
          published.definition().configJson());
      return new Candidate(snapshot, new Preview(
          bindingId, binding.modelVersionId(), binding.mappingRevisionId(),
          binding.taskRevisionId(), binding.taskRevisionNo(), binding.bindingDigest(),
          binding.taskDefinitionSha256(), binding.targetTable(),
          !validation.currentDraftMatchesPublishedDefinition(),
          "FROZEN_REVISION_VERIFIED_EXECUTION_NOT_AUTHORIZED", false));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("发布 SQL 修订序列化失败", ex);
    }
  }
}

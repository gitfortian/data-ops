package io.yak.ops.business.sync.realtime.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeDefinitionVersionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeDefinitionVersionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis persistence adapter for immutable DefinitionVersion inherited from its Project-owned task. */
@Repository
@DependsOn("realtimeSyncFlyway")
public class DefinitionVersionRepositoryAdapter implements DefinitionVersionRepository {

  private final RealtimeDefinitionVersionMapper versionMapper;
  private final RealtimeJobDefinitionMapper definitionMapper;
  private final RealtimeJsonCodec json;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public DefinitionVersionRepositoryAdapter(
      RealtimeDefinitionVersionMapper versionMapper,
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJsonCodec json,
      CurrentProject currentProject) {
    this.versionMapper = versionMapper;
    this.definitionMapper = definitionMapper;
    this.json = json;
    this.currentProject = currentProject;
  }

  /** Test-only compatibility constructor; calls remain fail-closed without CurrentProject. */
  public DefinitionVersionRepositoryAdapter(
      RealtimeDefinitionVersionMapper versionMapper,
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJsonCodec json) {
    this(
        versionMapper,
        definitionMapper,
        json,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public StoredVersion findOrCreate(PublicationCandidate candidate) {
    requireTaskOwned(candidate.taskId());
    RealtimeDefinitionVersionPO existing =
        findBySourceConfigDigest(candidate.taskId(), candidate.sourceConfigDigest()).orElse(null);
    if (existing != null) return stored(existing);

    RealtimeDefinitionVersionPO po = new RealtimeDefinitionVersionPO();
    po.setTaskId(candidate.taskId());
    po.setVersionNo(nextVersionNo(candidate.taskId()));
    po.setSourceDraftRevision(candidate.sourceDraftRevision());
    po.setRuntimeEnvironmentId(candidate.runtimeEnvironmentId());
    po.setDefinitionJson(json.write(candidate.compatibilityDefinition()));
    po.setDefinitionDigest(candidate.definitionDigest() == null ? null : candidate.definitionDigest().value());
    po.setSourceConfigDigest(candidate.sourceConfigDigest());
    po.setDomainMappingState(candidate.domainMappingState().name());
    po.setCreateTime(LocalDateTime.now());

    try {
      versionMapper.insert(po);
    } catch (DuplicateKeyException exception) {
      return stored(
          findBySourceConfigDigest(candidate.taskId(), candidate.sourceConfigDigest())
              .orElseThrow(() -> new IllegalStateException("DefinitionVersion 幂等冲突后记录不存在", exception)));
    }
    if (po.getId() == null) throw new IllegalStateException("新增 DefinitionVersion 未返回主键");
    return stored(po);
  }

  @Override
  public Optional<PublicationSnapshot> find(long definitionVersionId) {
    long projectId = currentProject.requireProjectId();
    RealtimeDefinitionVersionPO po =
        versionMapper.selectByIdAndProject(definitionVersionId, projectId);
    if (po == null) return Optional.empty();
    return Optional.of(
        new PublicationSnapshot(
            stored(po), po.getRuntimeEnvironmentId(), json.readSpec(po.getDefinitionJson())));
  }

  @Override
  public Optional<Long> publishedDefinitionVersionId(long taskId) {
    return Optional.of(requireTaskOwned(taskId))
        .map(RealtimeJobDefinitionPO::getPublishedDefinitionVersionId);
  }

  @Override
  public void bindPublishedReference(
      long taskId,
      long definitionVersionId,
      int expectedDraftRevision,
      String expectedSourceConfigDigest) {
    long projectId = currentProject.requireProjectId();
    requireTaskOwned(taskId);
    if (versionMapper.selectByIdAndProject(definitionVersionId, projectId) == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    int updated =
        definitionMapper.update(
            null,
            Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
                .eq(RealtimeJobDefinitionPO::getId, taskId)
                .eq(RealtimeJobDefinitionPO::getProjectId, projectId)
                .eq(RealtimeJobDefinitionPO::getDefinitionVersion, expectedDraftRevision)
                .eq(RealtimeJobDefinitionPO::getPublishedVersion, expectedDraftRevision)
                .eq(RealtimeJobDefinitionPO::getConfigDigest, expectedSourceConfigDigest)
                .set(RealtimeJobDefinitionPO::getPublishedDefinitionVersionId, definitionVersionId));
    if (updated != 1) throw new IllegalStateException("绑定 Published DefinitionVersion 时 Task 已变化");
  }

  private Optional<RealtimeDefinitionVersionPO> findBySourceConfigDigest(
      long taskId, String sourceConfigDigest) {
    return versionMapper.selectList(
            Wrappers.<RealtimeDefinitionVersionPO>lambdaQuery()
                .eq(RealtimeDefinitionVersionPO::getTaskId, taskId)
                .eq(RealtimeDefinitionVersionPO::getSourceConfigDigest, sourceConfigDigest)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  private int nextVersionNo(long taskId) {
    return versionMapper.selectList(
            Wrappers.<RealtimeDefinitionVersionPO>lambdaQuery()
                .eq(RealtimeDefinitionVersionPO::getTaskId, taskId)
                .orderByDesc(RealtimeDefinitionVersionPO::getVersionNo)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(RealtimeDefinitionVersionPO::getVersionNo)
        .orElse(0)
        + 1;
  }

  private RealtimeJobDefinitionPO requireTaskOwned(long taskId) {
    long projectId = currentProject.requireProjectId();
    RealtimeJobDefinitionPO task = definitionMapper.selectOne(
        Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
            .eq(RealtimeJobDefinitionPO::getId, taskId)
            .eq(RealtimeJobDefinitionPO::getProjectId, projectId));
    if (task == null) throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    return task;
  }

  private StoredVersion stored(RealtimeDefinitionVersionPO po) {
    return new StoredVersion(
        po.getId(),
        po.getTaskId(),
        po.getVersionNo(),
        po.getSourceDraftRevision(),
        po.getDefinitionDigest(),
        po.getSourceConfigDigest(),
        DomainMappingState.valueOf(po.getDomainMappingState()),
        po.getCreateTime());
  }
}

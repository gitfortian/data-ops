package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentTaskDraftMapper;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.common.bean.po.development.DevelopmentTaskDraftPO;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for mutable development-task drafts. */
@Repository
public class DevelopmentTaskDraftRepositoryAdapter implements DevelopmentTaskDraftRepository {

  private final DevelopmentTaskDraftMapper mapper;

  public DevelopmentTaskDraftRepositoryAdapter(DevelopmentTaskDraftMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<DevelopmentTaskDraft> findByNodeId(Long nodeId) {
    return Optional.ofNullable(mapper.selectById(nodeId)).map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentTaskDraft> findByNodeIdForUpdate(Long nodeId) {
    return Optional.ofNullable(mapper.selectForUpdate(nodeId)).map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentTaskDraft> save(
      Long nodeId,
      TaskDefinition definition,
      long expectedBaseRevision) {
    DevelopmentTaskDraftPO current = mapper.selectById(nodeId);
    Instant now = Instant.now();

    if (current == null) {
      if (expectedBaseRevision != 0L) return Optional.empty();

      DevelopmentTaskDraftPO created = new DevelopmentTaskDraftPO();
      created.setNodeId(nodeId);
      applyDefinition(created, definition);
      created.setDraftRevision(1L);
      created.setCreateTime(now);
      created.setUpdateTime(now);
      try {
        mapper.insert(created);
      } catch (DuplicateKeyException conflict) {
        return Optional.empty();
      }
      return Optional.of(toDomain(created));
    }

    long currentRevision = current.getDraftRevision() == null ? 0L : current.getDraftRevision();
    if (currentRevision != expectedBaseRevision) return Optional.empty();

    long nextRevision = currentRevision + 1L;
    int updated = mapper.update(
        null,
        new LambdaUpdateWrapper<DevelopmentTaskDraftPO>()
            .eq(DevelopmentTaskDraftPO::getNodeId, nodeId)
            .eq(DevelopmentTaskDraftPO::getDraftRevision, currentRevision)
            .set(DevelopmentTaskDraftPO::getTaskType, definition.taskType())
            .set(DevelopmentTaskDraftPO::getSchemaVersion, definition.schemaVersion())
            .set(DevelopmentTaskDraftPO::getContent, definition.content())
            .set(DevelopmentTaskDraftPO::getConfigJson, definition.configJson())
            .set(DevelopmentTaskDraftPO::getDraftRevision, nextRevision)
            .set(DevelopmentTaskDraftPO::getUpdateTime, now));
    if (updated <= 0) return Optional.empty();
    return findByNodeId(nodeId);
  }

  private void applyDefinition(DevelopmentTaskDraftPO po, TaskDefinition definition) {
    po.setTaskType(definition.taskType());
    po.setSchemaVersion(definition.schemaVersion());
    po.setContent(definition.content());
    po.setConfigJson(definition.configJson());
  }

  private DevelopmentTaskDraft toDomain(DevelopmentTaskDraftPO po) {
    return new DevelopmentTaskDraft(
        po.getNodeId(),
        new TaskDefinition(
            po.getTaskType(),
            po.getSchemaVersion() == null ? 1 : po.getSchemaVersion(),
            po.getContent(),
            po.getConfigJson()),
        po.getDraftRevision() == null ? 0L : po.getDraftRevision(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}

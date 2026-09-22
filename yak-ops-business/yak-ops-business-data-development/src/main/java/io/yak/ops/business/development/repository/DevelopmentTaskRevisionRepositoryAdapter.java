package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentTaskRevisionMapper;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.common.bean.po.development.DevelopmentTaskRevisionPO;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for immutable published task revisions. */
@Repository
public class DevelopmentTaskRevisionRepositoryAdapter
    implements DevelopmentTaskRevisionRepository {

  private final DevelopmentTaskRevisionMapper mapper;

  public DevelopmentTaskRevisionRepositoryAdapter(DevelopmentTaskRevisionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public int nextRevisionNo(Long nodeId) {
    Integer max = mapper.selectMaxRevisionNo(nodeId);
    return (max == null ? 0 : max) + 1;
  }

  @Override
  public DevelopmentTaskRevision insert(
      Long nodeId,
      int revisionNo,
      long sourceDraftRevision,
      TaskDefinition definition,
      String checksum) {
    DevelopmentTaskRevisionPO po = new DevelopmentTaskRevisionPO();
    po.setNodeId(nodeId);
    po.setRevisionNo(revisionNo);
    po.setSourceDraftRevision(sourceDraftRevision);
    po.setTaskType(definition.taskType());
    po.setSchemaVersion(definition.schemaVersion());
    po.setContent(definition.content());
    po.setConfigJson(definition.configJson());
    po.setChecksum(checksum);
    po.setCreateTime(Instant.now());
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<DevelopmentTaskRevision> findById(Long revisionId) {
    if (revisionId == null || revisionId <= 0L) return Optional.empty();
    return Optional.ofNullable(mapper.selectById(revisionId)).map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentTaskRevision> findLatestByNodeId(Long nodeId) {
    return Optional.ofNullable(mapper.selectOne(
            new LambdaQueryWrapper<DevelopmentTaskRevisionPO>()
                .eq(DevelopmentTaskRevisionPO::getNodeId, nodeId)
                .orderByDesc(DevelopmentTaskRevisionPO::getRevisionNo)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentTaskRevision> findByRevisionNo(Long nodeId, int revisionNo) {
    return Optional.ofNullable(mapper.selectOne(
            new LambdaQueryWrapper<DevelopmentTaskRevisionPO>()
                .eq(DevelopmentTaskRevisionPO::getNodeId, nodeId)
                .eq(DevelopmentTaskRevisionPO::getRevisionNo, revisionNo)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public List<DevelopmentTaskRevisionSummary> listByNodeId(Long nodeId) {
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentTaskRevisionPO>()
                .eq(DevelopmentTaskRevisionPO::getNodeId, nodeId)
                .orderByDesc(DevelopmentTaskRevisionPO::getRevisionNo))
        .stream()
        .map(this::toSummary)
        .toList();
  }

  private DevelopmentTaskRevision toDomain(DevelopmentTaskRevisionPO po) {
    return new DevelopmentTaskRevision(
        po.getId(),
        po.getNodeId(),
        po.getRevisionNo() == null ? 0 : po.getRevisionNo(),
        po.getSourceDraftRevision() == null ? 0L : po.getSourceDraftRevision(),
        new TaskDefinition(
            po.getTaskType(),
            po.getSchemaVersion() == null ? 1 : po.getSchemaVersion(),
            po.getContent(),
            po.getConfigJson()),
        po.getChecksum(),
        po.getCreateTime());
  }

  private DevelopmentTaskRevisionSummary toSummary(DevelopmentTaskRevisionPO po) {
    return new DevelopmentTaskRevisionSummary(
        po.getId(),
        po.getNodeId(),
        po.getRevisionNo() == null ? 0 : po.getRevisionNo(),
        po.getSourceDraftRevision() == null ? 0L : po.getSourceDraftRevision(),
        po.getChecksum(),
        po.getCreateTime());
  }
}

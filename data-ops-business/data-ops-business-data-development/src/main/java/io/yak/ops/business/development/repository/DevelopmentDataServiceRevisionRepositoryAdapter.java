package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentDataServiceRevisionMapper;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevisionSummary;
import io.yak.ops.common.bean.po.development.DevelopmentDataServiceRevisionPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for immutable Data Service Node revisions. */
@Repository
public class DevelopmentDataServiceRevisionRepositoryAdapter
    implements DevelopmentDataServiceRevisionRepository {

  private final DevelopmentDataServiceRevisionMapper mapper;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceRevisionRepositoryAdapter(
      DevelopmentDataServiceRevisionMapper mapper,
      ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public int nextRevisionNo(Long nodeId) {
    Integer max = mapper.selectMaxRevisionNo(nodeId);
    return (max == null ? 0 : max) + 1;
  }

  @Override
  public DevelopmentDataServiceRevision insert(
      Long nodeId,
      int revisionNo,
      long sourceDraftRevision,
      DevelopmentDataServiceDefinition definition,
      String checksum) {
    if (definition == null) {
      throw new IllegalArgumentException("Data Service Node 发布定义不能为空");
    }
    definition.validatePublishable();

    DevelopmentDataServiceRevisionPO po = new DevelopmentDataServiceRevisionPO();
    po.setNodeId(nodeId);
    po.setRevisionNo(revisionNo);
    po.setSourceDraftRevision(sourceDraftRevision);
    po.setDefinitionJson(writeDefinition(definition));
    po.setChecksum(checksum);
    po.setCreateTime(Instant.now());
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<DevelopmentDataServiceRevision> findLatestByNodeId(Long nodeId) {
    return Optional.ofNullable(mapper.selectOne(
            new LambdaQueryWrapper<DevelopmentDataServiceRevisionPO>()
                .eq(DevelopmentDataServiceRevisionPO::getNodeId, nodeId)
                .orderByDesc(DevelopmentDataServiceRevisionPO::getRevisionNo)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentDataServiceRevision> findByRevisionNo(Long nodeId, int revisionNo) {
    return Optional.ofNullable(mapper.selectOne(
            new LambdaQueryWrapper<DevelopmentDataServiceRevisionPO>()
                .eq(DevelopmentDataServiceRevisionPO::getNodeId, nodeId)
                .eq(DevelopmentDataServiceRevisionPO::getRevisionNo, revisionNo)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public List<DevelopmentDataServiceRevisionSummary> listByNodeId(Long nodeId) {
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentDataServiceRevisionPO>()
                .eq(DevelopmentDataServiceRevisionPO::getNodeId, nodeId)
                .orderByDesc(DevelopmentDataServiceRevisionPO::getRevisionNo))
        .stream()
        .map(this::toSummary)
        .toList();
  }

  private DevelopmentDataServiceRevision toDomain(DevelopmentDataServiceRevisionPO po) {
    return new DevelopmentDataServiceRevision(
        po.getId(),
        po.getNodeId(),
        po.getRevisionNo() == null ? 0 : po.getRevisionNo(),
        po.getSourceDraftRevision() == null ? 0L : po.getSourceDraftRevision(),
        readDefinition(po.getDefinitionJson()),
        po.getChecksum(),
        po.getCreateTime());
  }

  private DevelopmentDataServiceRevisionSummary toSummary(DevelopmentDataServiceRevisionPO po) {
    DevelopmentDataServiceDefinition definition = readDefinition(po.getDefinitionJson());
    return new DevelopmentDataServiceRevisionSummary(
        po.getId(),
        po.getNodeId(),
        po.getRevisionNo() == null ? 0 : po.getRevisionNo(),
        po.getSourceDraftRevision() == null ? 0L : po.getSourceDraftRevision(),
        definition.sourceTaskRevisionId(),
        definition.sourceTaskRevisionNo(),
        po.getChecksum(),
        po.getCreateTime());
  }

  private String writeDefinition(DevelopmentDataServiceDefinition definition) {
    try {
      return objectMapper.writeValueAsString(definition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Data Service Node revision 序列化失败", exception);
    }
  }

  private DevelopmentDataServiceDefinition readDefinition(String json) {
    try {
      return objectMapper.readValue(json, DevelopmentDataServiceDefinition.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Data Service Node revision 反序列化失败", exception);
    }
  }
}

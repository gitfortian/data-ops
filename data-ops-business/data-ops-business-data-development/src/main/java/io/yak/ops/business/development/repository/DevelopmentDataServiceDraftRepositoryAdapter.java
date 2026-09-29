package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentDataServiceDraftMapper;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDraft;
import io.yak.ops.common.bean.po.development.DevelopmentDataServiceDraftPO;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for mutable Data Service Node drafts. */
@Repository
public class DevelopmentDataServiceDraftRepositoryAdapter
    implements DevelopmentDataServiceDraftRepository {

  private final DevelopmentDataServiceDraftMapper mapper;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceDraftRepositoryAdapter(
      DevelopmentDataServiceDraftMapper mapper,
      ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<DevelopmentDataServiceDraft> findByNodeId(Long nodeId) {
    return Optional.ofNullable(mapper.selectById(nodeId)).map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentDataServiceDraft> findByNodeIdForUpdate(Long nodeId) {
    return Optional.ofNullable(mapper.selectForUpdate(nodeId)).map(this::toDomain);
  }

  @Override
  public Optional<DevelopmentDataServiceDraft> save(
      Long nodeId,
      DevelopmentDataServiceDefinition definition,
      long expectedBaseRevision) {
    DevelopmentDataServiceDraftPO current = mapper.selectById(nodeId);
    Instant now = Instant.now();
    String definitionJson = writeDefinition(definition);

    if (current == null) {
      if (expectedBaseRevision != 0L) return Optional.empty();
      DevelopmentDataServiceDraftPO created = new DevelopmentDataServiceDraftPO();
      created.setNodeId(nodeId);
      created.setDefinitionJson(definitionJson);
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
        new LambdaUpdateWrapper<DevelopmentDataServiceDraftPO>()
            .eq(DevelopmentDataServiceDraftPO::getNodeId, nodeId)
            .eq(DevelopmentDataServiceDraftPO::getDraftRevision, currentRevision)
            .set(DevelopmentDataServiceDraftPO::getDefinitionJson, definitionJson)
            .set(DevelopmentDataServiceDraftPO::getDraftRevision, nextRevision)
            .set(DevelopmentDataServiceDraftPO::getUpdateTime, now));
    if (updated <= 0) return Optional.empty();
    return findByNodeId(nodeId);
  }

  private DevelopmentDataServiceDraft toDomain(DevelopmentDataServiceDraftPO po) {
    return new DevelopmentDataServiceDraft(
        po.getNodeId(),
        readDefinition(po.getDefinitionJson()),
        po.getDraftRevision() == null ? 0L : po.getDraftRevision(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private String writeDefinition(DevelopmentDataServiceDefinition definition) {
    try {
      return objectMapper.writeValueAsString(definition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Data Service Node definition 序列化失败", exception);
    }
  }

  private DevelopmentDataServiceDefinition readDefinition(String json) {
    try {
      return objectMapper.readValue(json, DevelopmentDataServiceDefinition.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Data Service Node definition 反序列化失败", exception);
    }
  }
}

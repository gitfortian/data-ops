package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.mdm.dao.mapper.MdmSourceMapper;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.dao.model.MdmSourcePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the master data source binding (project-scoped reads/writes). */
@Repository
public class MdmSourceRepositoryAdapter implements MdmSourceRepository {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, String>> MAPPING_TYPE = new TypeReference<>() {};

  private final MdmSourceMapper mapper;
  private final CurrentProject currentProject;

  public MdmSourceRepositoryAdapter(MdmSourceMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmSource insert(MdmSource source, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmSourcePO po = toPo(source);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return source.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmSource> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmSourcePO>()
                    .eq(MdmSourcePO::getId, id)
                    .eq(MdmSourcePO::getProjectId, projectId)))
        .map(MdmSourceRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmSource> listByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmSourcePO>()
                .eq(MdmSourcePO::getProjectId, projectId)
                .eq(MdmSourcePO::getEntityId, entityId)
                .orderByAsc(MdmSourcePO::getSortOrder)
                .orderByAsc(MdmSourcePO::getId))
        .stream()
        .map(MdmSourceRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MdmSource> listByDatasource(Long datasourceId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmSourcePO>()
                .eq(MdmSourcePO::getProjectId, projectId)
                .eq(MdmSourcePO::getDatasourceId, datasourceId))
        .stream()
        .map(MdmSourceRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MdmSource> listAll() {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmSourcePO>()
                .eq(MdmSourcePO::getProjectId, projectId)
                .orderByAsc(MdmSourcePO::getSortOrder)
                .orderByAsc(MdmSourcePO::getId))
        .stream()
        .map(MdmSourceRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean exists(
      Long entityId, Long datasourceId, String database, String schema, String table) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmSourcePO> wrapper =
        new LambdaQueryWrapper<MdmSourcePO>()
            .eq(MdmSourcePO::getProjectId, projectId)
            .eq(MdmSourcePO::getEntityId, entityId)
            .eq(MdmSourcePO::getDatasourceId, datasourceId)
            .eq(MdmSourcePO::getSourceTable, table);
    wrapper.eq(StringUtils.hasText(database), MdmSourcePO::getSourceDatabase, database);
    wrapper.eq(StringUtils.hasText(schema), MdmSourcePO::getSourceSchema, schema);
    return mapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<MdmSourcePO>()
                .eq(MdmSourcePO::getId, id)
                .eq(MdmSourcePO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmSourcePO toPo(MdmSource source) {
    MdmSourcePO po = new MdmSourcePO();
    po.setId(source.id());
    po.setEntityId(source.entityId());
    po.setDatasourceId(source.datasourceId());
    po.setSourceDatabase(source.database());
    po.setSourceSchema(source.schema());
    po.setSourceTable(source.table());
    po.setFieldMapping(writeMapping(source.fieldMapping()));
    po.setSourceRole(source.role() == null ? null : source.role().name());
    po.setStatus(source.status());
    po.setSortOrder(source.sortOrder());
    return po;
  }

  private static MdmSource toDomain(MdmSourcePO po) {
    return new MdmSource(
        po.getId(),
        po.getEntityId(),
        po.getDatasourceId(),
        po.getSourceDatabase(),
        po.getSourceSchema(),
        po.getSourceTable(),
        readMapping(po.getFieldMapping()),
        po.getSourceRole() == null ? null : MdmSourceRole.valueOf(po.getSourceRole()),
        po.getStatus(),
        po.getSortOrder() == null ? 0 : po.getSortOrder(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private static String writeMapping(Map<String, String> mapping) {
    if (mapping == null || mapping.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(mapping);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("field_mapping 序列化失败", exception);
    }
  }

  private static Map<String, String> readMapping(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(json, MAPPING_TYPE);
    } catch (Exception exception) {
      // 脏数据容错:解析失败按"同名回退"处理
      return null;
    }
  }
}

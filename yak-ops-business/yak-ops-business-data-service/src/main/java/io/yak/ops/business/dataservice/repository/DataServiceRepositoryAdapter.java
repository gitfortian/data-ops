package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceRepositoryAdapter implements DataServiceRepository {

  private final DataServiceApiMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public Optional<DataServiceDefinition> findById(Long id) {
    if (id == null) return Optional.empty();
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getId, id)
                .eq(DataServiceApiPO::getProjectId, projectId)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DataServiceDefinition> findByPath(String path) {
    if (path == null) return Optional.empty();
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getProjectId, projectId)
                .eq(DataServiceApiPO::getPath, path)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DataServiceDefinition> findByRuntimePath(String path) {
    if (path == null) return Optional.empty();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getPath, path)
                .isNotNull(DataServiceApiPO::getProjectId)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef) {
    if (sourceType == null || sourceRef == null) return Optional.empty();
    Long projectId = currentProject.requireProjectId();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getProjectId, projectId)
                .eq(DataServiceApiPO::getSourceType, sourceType)
                .eq(DataServiceApiPO::getSourceRef, sourceRef)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public List<DataServiceDefinition> findAll() {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getProjectId, projectId)
                .orderByDesc(DataServiceApiPO::getUpdateTime)
                .orderByDesc(DataServiceApiPO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public long count() {
    Long count = mapper.selectCount(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .isNotNull(DataServiceApiPO::getProjectId));
    return count == null ? 0L : count;
  }

  @Override
  public boolean existsByPath(String path, Long excludeId) {
    Long count = mapper.selectCount(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getPath, path)
            .ne(excludeId != null, DataServiceApiPO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public DataServiceDefinition save(DataServiceDefinition definition) {
    Long projectId = currentProject.requireProjectId();
    if (definition == null || !Objects.equals(projectId, definition.projectId())) {
      throw new IllegalArgumentException("数据服务不属于当前 Project Space");
    }
    DataServiceApiPO po = toPo(definition);
    if (definition.id() == null) {
      mapper.insert(po);
    } else {
      int updated = mapper.update(
          po,
          Wrappers.<DataServiceApiPO>lambdaUpdate()
              .eq(DataServiceApiPO::getId, definition.id())
              .eq(DataServiceApiPO::getProjectId, projectId));
      if (updated != 1) throw new IllegalArgumentException("数据服务不存在：" + definition.id());
    }
    return toDomain(po);
  }

  @Override
  public boolean delete(Long id) {
    if (id == null) return false;
    Long projectId = currentProject.requireProjectId();
    return mapper.delete(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getId, id)
            .eq(DataServiceApiPO::getProjectId, projectId)) > 0;
  }

  private DataServiceDefinition toDomain(DataServiceApiPO po) {
    DataServiceSettings settings = new DataServiceSettings(
        po.getName(), po.getPath(), value(po.getMaxRows(), 1_000), value(po.getTimeoutSeconds(), 30),
        Boolean.TRUE.equals(po.getEnabled()), po.getDescription(), Boolean.TRUE.equals(po.getPaginationEnabled()));
    PublishedRuntimeSnapshot runtime = new PublishedRuntimeSnapshot(po.getDataSourceId(), po.getSqlText());
    SourceReference source = new SourceReference(
        po.getSourceType(), po.getSourceRef(), po.getSourceRevisionId(), po.getSourceRevisionNo());
    RuntimePolicy policy = new RuntimePolicy(
        Boolean.TRUE.equals(po.getCacheEnabled()), value(po.getCacheTtlSeconds(), 60),
        value(po.getCacheMaxEntries(), 200), Boolean.TRUE.equals(po.getCircuitBreakerEnabled()),
        value(po.getCircuitFailureThreshold(), 5), value(po.getCircuitRecoverySeconds(), 30));
    return DataServiceDefinition.restore(
        po.getId(), po.getProjectId(), po.getRuntimeGeneration(), settings, runtime, source, policy,
        AuthMode.parse(po.getAuthMode()), po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceApiPO toPo(DataServiceDefinition definition) {
    DataServiceApiPO po = new DataServiceApiPO();
    po.setId(definition.id());
    po.setProjectId(definition.projectId());
    DataServiceSettings settings = definition.settings();
    po.setName(settings.name());
    po.setPath(settings.path());
    po.setMaxRows(settings.maxRows());
    po.setTimeoutSeconds(settings.timeoutSeconds());
    po.setEnabled(settings.enabled());
    po.setDescription(settings.description());
    po.setPaginationEnabled(settings.paginationEnabled());
    PublishedRuntimeSnapshot runtime = definition.runtimeSnapshot();
    po.setDataSourceId(runtime.dataSourceId());
    po.setSqlText(runtime.sql());
    po.setAuthMode(definition.authMode().name());
    RuntimePolicy policy = definition.runtimePolicy();
    po.setCacheEnabled(policy.cacheEnabled());
    po.setCacheTtlSeconds(policy.cacheTtlSeconds());
    po.setCacheMaxEntries(policy.cacheMaxEntries());
    po.setCircuitBreakerEnabled(policy.circuitBreakerEnabled());
    po.setCircuitFailureThreshold(policy.failureThreshold());
    po.setCircuitRecoverySeconds(policy.recoverySeconds());
    po.setRuntimeGeneration(definition.runtimeGeneration());
    SourceReference source = definition.sourceReference();
    po.setSourceType(source.sourceType());
    po.setSourceRef(source.sourceRef());
    po.setSourceRevisionId(source.sourceRevisionId());
    po.setSourceRevisionNo(source.sourceRevisionNo());
    po.setCreateTime(definition.createTime());
    po.setUpdateTime(definition.updateTime());
    return po;
  }

  private int value(Integer value, int fallback) {
    return value == null || value <= 0 ? fallback : value;
  }
}

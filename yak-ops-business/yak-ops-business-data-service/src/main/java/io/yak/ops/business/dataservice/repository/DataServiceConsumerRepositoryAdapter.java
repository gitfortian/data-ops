package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceConsumerApiGrantMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceConsumerMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceConsumerApiGrantPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceConsumerPO;
import io.yak.ops.business.dataservice.domain.access.ConsumerAccessScope;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceConsumerRepositoryAdapter implements DataServiceConsumerRepository {
  private final DataServiceConsumerMapper consumerMapper;
  private final DataServiceConsumerApiGrantMapper grantMapper;
  private final CurrentProject currentProject;

  @Override
  public List<DataServiceConsumer> findAll() {
    Long projectId = currentProject.requireProjectId();
    return consumerMapper.selectList(Wrappers.<DataServiceConsumerPO>lambdaQuery()
            .eq(DataServiceConsumerPO::getProjectId, projectId)
            .orderByDesc(DataServiceConsumerPO::getUpdateTime)
            .orderByDesc(DataServiceConsumerPO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<DataServiceConsumer> findById(Long id) {
    if (id == null) return Optional.empty();
    return findByIdForProject(id, currentProject.requireProjectId());
  }

  @Override
  public Optional<DataServiceConsumer> findByIdForProject(Long id, Long projectId) {
    if (id == null || projectId == null) return Optional.empty();
    return Optional.ofNullable(consumerMapper.selectOne(
            Wrappers.<DataServiceConsumerPO>lambdaQuery()
                .eq(DataServiceConsumerPO::getId, id)
                .eq(DataServiceConsumerPO::getProjectId, projectId)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public boolean existsName(String name, Long excludeId) {
    if (name == null) return false;
    Long projectId = currentProject.requireProjectId();
    Long count = consumerMapper.selectCount(Wrappers.<DataServiceConsumerPO>lambdaQuery()
        .eq(DataServiceConsumerPO::getProjectId, projectId)
        .eq(DataServiceConsumerPO::getName, name)
        .ne(excludeId != null, DataServiceConsumerPO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public DataServiceConsumer save(DataServiceConsumer consumer) {
    Long projectId = currentProject.requireProjectId();
    if (consumer == null || !Objects.equals(projectId, consumer.projectId())) {
      throw new IllegalArgumentException("调用方不属于当前 Project Space");
    }
    DataServiceConsumerPO po = toPo(consumer);
    if (consumer.id() == null) {
      consumerMapper.insert(po);
    } else {
      int updated = consumerMapper.update(po, Wrappers.<DataServiceConsumerPO>lambdaUpdate()
          .eq(DataServiceConsumerPO::getId, consumer.id())
          .eq(DataServiceConsumerPO::getProjectId, projectId));
      if (updated != 1) throw new IllegalArgumentException("调用方不存在：" + consumer.id());
    }
    return toDomain(po);
  }

  @Override
  public boolean delete(Long id) {
    if (id == null) return false;
    Long projectId = currentProject.requireProjectId();
    return consumerMapper.delete(Wrappers.<DataServiceConsumerPO>lambdaQuery()
        .eq(DataServiceConsumerPO::getId, id)
        .eq(DataServiceConsumerPO::getProjectId, projectId)) > 0;
  }

  @Override
  public List<Long> findApiIds(Long consumerId) {
    if (consumerId == null) return List.of();
    return grantMapper.selectList(Wrappers.<DataServiceConsumerApiGrantPO>lambdaQuery()
            .eq(DataServiceConsumerApiGrantPO::getConsumerId, consumerId)
            .orderByAsc(DataServiceConsumerApiGrantPO::getApiId))
        .stream().map(DataServiceConsumerApiGrantPO::getApiId).toList();
  }

  @Override
  public void replaceApiIds(Long consumerId, List<Long> apiIds, LocalDateTime now) {
    deleteApiGrants(consumerId);
    if (apiIds == null || apiIds.isEmpty()) return;
    for (Long apiId : apiIds) {
      DataServiceConsumerApiGrantPO po = new DataServiceConsumerApiGrantPO();
      po.setConsumerId(consumerId);
      po.setApiId(apiId);
      po.setCreateTime(now);
      grantMapper.insert(po);
    }
  }

  @Override
  public void deleteApiGrants(Long consumerId) {
    if (consumerId != null) {
      grantMapper.delete(Wrappers.<DataServiceConsumerApiGrantPO>lambdaQuery()
          .eq(DataServiceConsumerApiGrantPO::getConsumerId, consumerId));
    }
  }

  @Override
  public boolean hasConfiguredAccess(Long projectId, Long apiId) {
    if (projectId == null || apiId == null) return false;
    return consumerMapper.countConfiguredAccess(projectId, apiId) > 0L;
  }

  @Override
  public boolean hasAccess(Long consumerId, Long projectId, Long apiId) {
    if (consumerId == null || projectId == null || apiId == null) return false;
    return consumerMapper.countConsumerAccess(consumerId, projectId, apiId) > 0L;
  }

  private DataServiceConsumer toDomain(DataServiceConsumerPO po) {
    return new DataServiceConsumer(
        po.getId(), po.getProjectId(), po.getName(), po.getDescription(),
        ConsumerAccessScope.parse(po.getAccessScope()), Boolean.TRUE.equals(po.getEnabled()),
        po.getDefaultRateLimitPerMinute() == null ? 60 : po.getDefaultRateLimitPerMinute(),
        po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceConsumerPO toPo(DataServiceConsumer consumer) {
    DataServiceConsumerPO po = new DataServiceConsumerPO();
    po.setId(consumer.id());
    po.setProjectId(consumer.projectId());
    po.setName(consumer.name());
    po.setDescription(consumer.description());
    po.setAccessScope(consumer.accessScope().name());
    po.setEnabled(consumer.enabled());
    po.setDefaultRateLimitPerMinute(consumer.defaultRateLimitPerMinute());
    po.setCreateTime(consumer.createTime());
    po.setUpdateTime(consumer.updateTime());
    return po;
  }
}

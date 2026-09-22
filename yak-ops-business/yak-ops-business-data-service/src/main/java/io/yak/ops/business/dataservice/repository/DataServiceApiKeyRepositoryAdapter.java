package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiKeyMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiKeyPO;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceApiKeyRepositoryAdapter implements DataServiceApiKeyRepository {
  private final DataServiceApiKeyMapper mapper;

  @Override
  public List<DataServiceApiKey> findByApiId(Long apiId) {
    if (apiId == null) return List.of();
    return mapper.selectList(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
            .eq(DataServiceApiKeyPO::getApiId, apiId)
            .orderByDesc(DataServiceApiKeyPO::getCreateTime)
            .orderByDesc(DataServiceApiKeyPO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public List<DataServiceApiKey> findByConsumerId(Long consumerId) {
    if (consumerId == null) return List.of();
    return mapper.selectList(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
            .eq(DataServiceApiKeyPO::getConsumerId, consumerId)
            .orderByDesc(DataServiceApiKeyPO::getCreateTime)
            .orderByDesc(DataServiceApiKeyPO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<DataServiceApiKey> findById(Long id) {
    return Optional.ofNullable(id == null ? null : mapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public Optional<DataServiceApiKey> findByHash(Long apiId, String hash) {
    if (apiId == null || hash == null) return Optional.empty();
    return Optional.ofNullable(mapper.selectOne(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
            .eq(DataServiceApiKeyPO::getApiId, apiId)
            .eq(DataServiceApiKeyPO::getKeyHash, hash)
            .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DataServiceApiKey> findByHash(String hash) {
    if (hash == null) return Optional.empty();
    return Optional.ofNullable(mapper.selectOne(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
            .eq(DataServiceApiKeyPO::getKeyHash, hash)
            .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public DataServiceApiKey save(DataServiceApiKey key) {
    DataServiceApiKeyPO po = toPo(key);
    if (key.id() == null) mapper.insert(po); else mapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public boolean delete(Long id) {
    return id != null && mapper.deleteById(id) > 0;
  }

  @Override
  public void deleteByApiId(Long apiId) {
    if (apiId != null) {
      mapper.delete(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
          .eq(DataServiceApiKeyPO::getApiId, apiId));
    }
  }

  @Override
  public void deleteByConsumerId(Long consumerId) {
    if (consumerId != null) {
      mapper.delete(Wrappers.<DataServiceApiKeyPO>lambdaQuery()
          .eq(DataServiceApiKeyPO::getConsumerId, consumerId));
    }
  }

  private DataServiceApiKey toDomain(DataServiceApiKeyPO po) {
    return new DataServiceApiKey(
        po.getId(), po.getApiId(), po.getConsumerId(), po.getName(), po.getKeyPrefix(),
        po.getKeyHash(), Boolean.TRUE.equals(po.getEnabled()),
        po.getRateLimitPerMinute() == null ? 60 : po.getRateLimitPerMinute(),
        po.getExpiresAt(), po.getLastUsedAt(), po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceApiKeyPO toPo(DataServiceApiKey key) {
    DataServiceApiKeyPO po = new DataServiceApiKeyPO();
    po.setId(key.id());
    po.setApiId(key.apiId());
    po.setConsumerId(key.consumerId());
    po.setName(key.name());
    po.setKeyPrefix(key.keyPrefix());
    po.setKeyHash(key.keyHash());
    po.setEnabled(key.enabled());
    po.setRateLimitPerMinute(key.rateLimitPerMinute());
    po.setExpiresAt(key.expiresAt());
    po.setLastUsedAt(key.lastUsedAt());
    po.setCreateTime(key.createTime());
    po.setUpdateTime(key.updateTime());
    return po;
  }
}

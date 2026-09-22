package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import java.util.List;
import java.util.Optional;

public interface DataServiceApiKeyRepository {
  List<DataServiceApiKey> findByApiId(Long apiId);
  List<DataServiceApiKey> findByConsumerId(Long consumerId);
  Optional<DataServiceApiKey> findById(Long id);
  Optional<DataServiceApiKey> findByHash(Long apiId, String hash);
  Optional<DataServiceApiKey> findByHash(String hash);
  DataServiceApiKey save(DataServiceApiKey key);
  boolean delete(Long id);
  void deleteByApiId(Long apiId);
  void deleteByConsumerId(Long consumerId);
}

package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DataServiceConsumerRepository {
  List<DataServiceConsumer> findAll();
  Optional<DataServiceConsumer> findById(Long id);
  Optional<DataServiceConsumer> findByIdForProject(Long id, Long projectId);
  boolean existsName(String name, Long excludeId);
  DataServiceConsumer save(DataServiceConsumer consumer);
  boolean delete(Long id);
  List<Long> findApiIds(Long consumerId);
  void replaceApiIds(Long consumerId, List<Long> apiIds, LocalDateTime now);
  void deleteApiGrants(Long consumerId);
  boolean hasConfiguredAccess(Long projectId, Long apiId);
  boolean hasAccess(Long consumerId, Long projectId, Long apiId);
}

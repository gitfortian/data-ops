package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.access.DataServiceConsumerIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DataServiceConsumerIpAccessRepository {
  IpAccessMode findMode(Long consumerId);
  void saveMode(Long consumerId, IpAccessMode mode, LocalDateTime updateTime);
  List<DataServiceConsumerIpAccessRule> findRules(Long consumerId);
  Optional<DataServiceConsumerIpAccessRule> findRule(Long id);
  boolean existsRule(Long consumerId, IpAccessRuleType type, String networkCidr, Long excludeId);
  DataServiceConsumerIpAccessRule saveRule(DataServiceConsumerIpAccessRule rule);
  boolean deleteRule(Long id);
  void deleteByConsumerId(Long consumerId);
}

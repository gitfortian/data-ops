package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DataServiceIpAccessRepository {
  IpAccessMode findMode(Long apiId);
  void saveMode(Long apiId, IpAccessMode mode, LocalDateTime updateTime);
  List<DataServiceIpAccessRule> findRules(Long apiId);
  Optional<DataServiceIpAccessRule> findRule(Long id);
  boolean existsRule(Long apiId, IpAccessRuleType ruleType, String networkCidr, Long excludeId);
  DataServiceIpAccessRule saveRule(DataServiceIpAccessRule rule);
  boolean deleteRule(Long id);
  void deleteByApiId(Long apiId);
}

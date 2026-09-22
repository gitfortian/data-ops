package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceConsumerIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerIpAccessRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceConsumerIpAccessAuthorizer {
  private final DataServiceConsumerIpAccessRepository repository;

  public void authorize(Long consumerId, String clientIp) {
    IpAccessMode mode = repository.findMode(consumerId);
    if (mode == IpAccessMode.NONE) return;

    String normalizedIp = IpNetwork.tryNormalizeAddress(clientIp);
    if (normalizedIp == null) {
      throw new DataServiceForbiddenException("无法确认调用来源 IP，调用方访问策略拒绝本次调用");
    }

    IpAccessRuleType activeType = mode == IpAccessMode.ALLOWLIST
        ? IpAccessRuleType.ALLOWLIST
        : IpAccessRuleType.DENYLIST;
    LocalDateTime now = LocalDateTime.now();
    List<DataServiceConsumerIpAccessRule> rules = repository.findRules(consumerId).stream()
        .filter(rule -> rule.ruleType() == activeType)
        .filter(rule -> rule.active(now))
        .toList();
    boolean matched = rules.stream()
        .anyMatch(rule -> IpNetwork.contains(rule.networkCidr(), normalizedIp));

    if (mode == IpAccessMode.ALLOWLIST && !matched) {
      throw new DataServiceForbiddenException("当前来源 IP 不在调用方白名单中");
    }
    if (mode == IpAccessMode.DENYLIST && matched) {
      throw new DataServiceForbiddenException("当前来源 IP 已被调用方黑名单拒绝");
    }
  }
}

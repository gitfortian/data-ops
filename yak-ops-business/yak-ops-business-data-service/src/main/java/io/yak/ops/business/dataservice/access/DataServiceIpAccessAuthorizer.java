package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceIpAccessRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
public class DataServiceIpAccessAuthorizer {
  private final DataServiceIpAccessRepository repository;

  public DataServiceIpAccessAuthorizer(DataServiceIpAccessRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  public void authorize(Long apiId, String clientIp) {
    IpAccessMode mode = repository.findMode(apiId);
    if (mode == IpAccessMode.NONE) return;

    String normalizedIp = IpNetwork.tryNormalizeAddress(clientIp);
    if (normalizedIp == null) {
      throw new DataServiceForbiddenException("无法确认调用来源 IP，访问策略拒绝本次调用");
    }

    IpAccessRuleType activeType = mode == IpAccessMode.ALLOWLIST
        ? IpAccessRuleType.ALLOWLIST
        : IpAccessRuleType.DENYLIST;
    LocalDateTime now = LocalDateTime.now();
    List<DataServiceIpAccessRule> rules = repository.findRules(apiId).stream()
        .filter(rule -> rule.ruleType() == activeType)
        .filter(rule -> rule.active(now))
        .toList();
    boolean matched = rules.stream()
        .anyMatch(rule -> IpNetwork.contains(rule.networkCidr(), normalizedIp));

    if (mode == IpAccessMode.ALLOWLIST && !matched) {
      throw new DataServiceForbiddenException("当前来源 IP 不在数据服务白名单中");
    }
    if (mode == IpAccessMode.DENYLIST && matched) {
      throw new DataServiceForbiddenException("当前来源 IP 已被数据服务访问策略拒绝");
    }
  }
}

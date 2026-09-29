package io.yak.ops.business.dataservice.query;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceIpAccessRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read model for the standalone Data Service access-control management page. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceAccessOverviewReader {
  private static final String RUNTIME_PREFIX = "/api/v1/data-service/runtime";

  private final DataServiceReader dataServiceReader;
  private final DataServiceParameterNameReader parameterNameReader;
  private final DataServiceApiKeyRepository apiKeyRepository;
  private final DataServiceIpAccessRepository ipAccessRepository;

  public List<DataServiceAccessOverviewItem> list() {
    LocalDateTime now = LocalDateTime.now();
    return dataServiceReader.list().stream()
        .map(definition -> summarize(definition, now))
        .toList();
  }

  private DataServiceAccessOverviewItem summarize(
      DataServiceDefinition definition, LocalDateTime now) {
    List<DataServiceApiKey> keys = apiKeyRepository.findByApiId(definition.id());
    int activeApiKeys = (int) keys.stream()
        .filter(key -> key.enabled() && !key.expired(now))
        .count();

    int allowlistRules = 0;
    int activeAllowlistRules = 0;
    int denylistRules = 0;
    int activeDenylistRules = 0;
    for (DataServiceIpAccessRule rule : ipAccessRepository.findRules(definition.id())) {
      if (rule.ruleType() == IpAccessRuleType.ALLOWLIST) {
        allowlistRules++;
        if (rule.active(now)) activeAllowlistRules++;
      } else if (rule.ruleType() == IpAccessRuleType.DENYLIST) {
        denylistRules++;
        if (rule.active(now)) activeDenylistRules++;
      }
    }

    return new DataServiceAccessOverviewItem(
        definition.id(),
        definition.settings().name(),
        definition.settings().path(),
        RUNTIME_PREFIX + definition.settings().path(),
        parameterNameReader.parameterNames(definition.runtimeSnapshot().sql()),
        definition.settings().enabled(),
        definition.authMode(),
        ipAccessRepository.findMode(definition.id()),
        keys.size(),
        activeApiKeys,
        allowlistRules,
        activeAllowlistRules,
        denylistRules,
        activeDenylistRules,
        definition.updateTime());
  }
}

package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceIpAccessRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceIpAccessManager {
  private final DataServiceReader dataServiceReader;
  private final DataServiceIpAccessRepository repository;

  public IpAccessPolicyView getPolicy(Long apiId) {
    dataServiceReader.require(apiId);
    return view(apiId);
  }

  @Transactional
  public IpAccessPolicyView setMode(Long apiId, String rawMode) {
    dataServiceReader.require(apiId);
    IpAccessMode mode;
    try {
      mode = IpAccessMode.parse(rawMode);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 IP 访问模式：" + rawMode, exception);
    }
    repository.saveMode(apiId, mode, LocalDateTime.now());
    return view(apiId);
  }

  @Transactional
  public IpAccessRuleView createRule(Long apiId, IpAccessRuleInput input) {
    dataServiceReader.require(apiId);
    RuleValues values = normalize(input, null);
    ensureUnique(apiId, values.ruleType(), values.networkCidr(), null);
    LocalDateTime now = LocalDateTime.now();
    return ruleView(repository.saveRule(new DataServiceIpAccessRule(
        null, apiId, values.ruleType(), values.networkCidr(), values.description(),
        values.enabled(), values.expiresAt(), now, now)));
  }

  @Transactional
  public IpAccessRuleView updateRule(Long apiId, Long ruleId, IpAccessRuleInput input) {
    DataServiceIpAccessRule current = requireRule(apiId, ruleId);
    RuleValues values = normalize(input, current);
    ensureUnique(apiId, values.ruleType(), values.networkCidr(), ruleId);
    return ruleView(repository.saveRule(new DataServiceIpAccessRule(
        current.id(), current.apiId(), values.ruleType(), values.networkCidr(), values.description(),
        values.enabled(), values.expiresAt(), current.createTime(), LocalDateTime.now())));
  }

  @Transactional
  public void deleteRule(Long apiId, Long ruleId) {
    requireRule(apiId, ruleId);
    if (!repository.deleteRule(ruleId)) throw new IllegalArgumentException("IP 访问规则不存在：" + ruleId);
  }

  @Transactional
  public void deleteForApi(Long apiId) {
    if (apiId != null) repository.deleteByApiId(apiId);
  }

  private IpAccessPolicyView view(Long apiId) {
    return new IpAccessPolicyView(
        repository.findMode(apiId), repository.findRules(apiId).stream().map(this::ruleView).toList());
  }

  private DataServiceIpAccessRule requireRule(Long apiId, Long ruleId) {
    dataServiceReader.require(apiId);
    if (ruleId == null) throw new IllegalArgumentException("IP 访问规则 ID 不能为空");
    DataServiceIpAccessRule rule = repository.findRule(ruleId)
        .orElseThrow(() -> new IllegalArgumentException("IP 访问规则不存在：" + ruleId));
    if (!apiId.equals(rule.apiId())) throw new IllegalArgumentException("IP 访问规则不存在：" + ruleId);
    return rule;
  }

  private RuleValues normalize(IpAccessRuleInput input, DataServiceIpAccessRule current) {
    if (input == null) throw new IllegalArgumentException("IP 访问规则不能为空");
    IpAccessRuleType type = StringUtils.hasText(input.ruleType())
        ? parseType(input.ruleType())
        : current == null ? null : current.ruleType();
    if (type == null) throw new IllegalArgumentException("IP 访问规则类型不能为空");

    String network = StringUtils.hasText(input.networkCidr())
        ? IpNetwork.normalizeNetwork(input.networkCidr())
        : current == null ? null : current.networkCidr();
    if (!StringUtils.hasText(network)) throw new IllegalArgumentException("IP/CIDR 不能为空");

    String description = normalizeDescription(input.description());
    boolean enabled = input.enabled() == null
        ? current == null || current.enabled()
        : input.enabled();
    LocalDateTime expiresAt = input.expiresAt();
    if (expiresAt != null
        && !expiresAt.isAfter(LocalDateTime.now())
        && (current == null || !expiresAt.equals(current.expiresAt()))) {
      throw new IllegalArgumentException("规则过期时间必须晚于当前时间");
    }
    return new RuleValues(type, network, description, enabled, expiresAt);
  }

  private IpAccessRuleType parseType(String value) {
    try {
      return IpAccessRuleType.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 IP 访问规则类型：" + value, exception);
    }
  }

  private String normalizeDescription(String value) {
    String result = value == null ? null : value.trim();
    if (!StringUtils.hasText(result)) return null;
    if (result.length() > 255) throw new IllegalArgumentException("规则说明不能超过 255 个字符");
    return result;
  }

  private void ensureUnique(
      Long apiId, IpAccessRuleType type, String networkCidr, Long excludeId) {
    if (repository.existsRule(apiId, type, networkCidr, excludeId)) {
      throw new IllegalArgumentException("该名单中已存在相同 IP/CIDR：" + networkCidr);
    }
  }

  private IpAccessRuleView ruleView(DataServiceIpAccessRule rule) {
    return new IpAccessRuleView(
        rule.id(), rule.apiId(), rule.ruleType(), rule.networkCidr(), rule.description(),
        rule.enabled(), rule.expiresAt(), rule.createTime(), rule.updateTime());
  }

  private record RuleValues(
      IpAccessRuleType ruleType,
      String networkCidr,
      String description,
      boolean enabled,
      LocalDateTime expiresAt) {}
}

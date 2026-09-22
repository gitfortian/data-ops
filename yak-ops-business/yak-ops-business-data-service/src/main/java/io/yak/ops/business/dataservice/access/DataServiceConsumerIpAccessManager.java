package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceConsumerIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerIpAccessRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceConsumerIpAccessManager {
  private final DataServiceConsumerRepository consumerRepository;
  private final DataServiceConsumerIpAccessRepository repository;

  public ConsumerIpAccessPolicyView getPolicy(Long consumerId) {
    requireConsumer(consumerId);
    return view(consumerId);
  }

  @Transactional
  public ConsumerIpAccessPolicyView setMode(Long consumerId, String rawMode) {
    requireConsumer(consumerId);
    IpAccessMode mode;
    try {
      mode = IpAccessMode.parse(rawMode);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 IP 访问模式：" + rawMode, exception);
    }
    repository.saveMode(consumerId, mode, LocalDateTime.now());
    return view(consumerId);
  }

  @Transactional
  public ConsumerIpAccessRuleView createRule(Long consumerId, IpAccessRuleInput input) {
    requireConsumer(consumerId);
    RuleValues values = normalize(input, null);
    ensureUnique(consumerId, values.ruleType(), values.networkCidr(), null);
    LocalDateTime now = LocalDateTime.now();
    return ruleView(repository.saveRule(new DataServiceConsumerIpAccessRule(
        null, consumerId, values.ruleType(), values.networkCidr(), values.description(),
        values.enabled(), values.expiresAt(), now, now)));
  }

  @Transactional
  public ConsumerIpAccessRuleView updateRule(
      Long consumerId, Long ruleId, IpAccessRuleInput input) {
    DataServiceConsumerIpAccessRule current = requireRule(consumerId, ruleId);
    RuleValues values = normalize(input, current);
    ensureUnique(consumerId, values.ruleType(), values.networkCidr(), ruleId);
    return ruleView(repository.saveRule(new DataServiceConsumerIpAccessRule(
        current.id(), current.consumerId(), values.ruleType(), values.networkCidr(),
        values.description(), values.enabled(), values.expiresAt(), current.createTime(),
        LocalDateTime.now())));
  }

  @Transactional
  public void deleteRule(Long consumerId, Long ruleId) {
    requireRule(consumerId, ruleId);
    if (!repository.deleteRule(ruleId)) {
      throw new IllegalArgumentException("IP 访问规则不存在：" + ruleId);
    }
  }

  private void requireConsumer(Long consumerId) {
    if (consumerId == null || consumerRepository.findById(consumerId).isEmpty()) {
      throw new IllegalArgumentException("调用方不存在：" + consumerId);
    }
  }

  private DataServiceConsumerIpAccessRule requireRule(Long consumerId, Long ruleId) {
    requireConsumer(consumerId);
    if (ruleId == null) throw new IllegalArgumentException("IP 访问规则 ID 不能为空");
    DataServiceConsumerIpAccessRule rule = repository.findRule(ruleId)
        .orElseThrow(() -> new IllegalArgumentException("IP 访问规则不存在：" + ruleId));
    if (!consumerId.equals(rule.consumerId())) {
      throw new IllegalArgumentException("IP 访问规则不存在：" + ruleId);
    }
    return rule;
  }

  private ConsumerIpAccessPolicyView view(Long consumerId) {
    return new ConsumerIpAccessPolicyView(
        repository.findMode(consumerId),
        repository.findRules(consumerId).stream().map(this::ruleView).toList());
  }

  private RuleValues normalize(
      IpAccessRuleInput input, DataServiceConsumerIpAccessRule current) {
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
      Long consumerId, IpAccessRuleType type, String networkCidr, Long excludeId) {
    if (repository.existsRule(consumerId, type, networkCidr, excludeId)) {
      throw new IllegalArgumentException("该名单中已存在相同 IP/CIDR：" + networkCidr);
    }
  }

  private ConsumerIpAccessRuleView ruleView(DataServiceConsumerIpAccessRule rule) {
    return new ConsumerIpAccessRuleView(
        rule.id(), rule.consumerId(), rule.ruleType(), rule.networkCidr(), rule.description(),
        rule.enabled(), rule.expiresAt(), rule.createTime(), rule.updateTime());
  }

  private record RuleValues(
      IpAccessRuleType ruleType,
      String networkCidr,
      String description,
      boolean enabled,
      LocalDateTime expiresAt) {}
}

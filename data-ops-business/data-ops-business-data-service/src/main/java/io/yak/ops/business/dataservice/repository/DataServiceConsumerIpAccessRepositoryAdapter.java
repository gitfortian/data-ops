package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceConsumerIpAccessPolicyMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceConsumerIpAccessRuleMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceConsumerIpAccessPolicyPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceConsumerIpAccessRulePO;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumerIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceConsumerIpAccessRepositoryAdapter
    implements DataServiceConsumerIpAccessRepository {
  private final DataServiceConsumerIpAccessPolicyMapper policyMapper;
  private final DataServiceConsumerIpAccessRuleMapper ruleMapper;

  @Override
  public IpAccessMode findMode(Long consumerId) {
    DataServiceConsumerIpAccessPolicyPO po =
        consumerId == null ? null : policyMapper.selectById(consumerId);
    return po == null ? IpAccessMode.NONE : IpAccessMode.parse(po.getMode());
  }

  @Override
  public void saveMode(Long consumerId, IpAccessMode mode, LocalDateTime updateTime) {
    if (consumerId == null) throw new IllegalArgumentException("调用方 ID 不能为空");
    DataServiceConsumerIpAccessPolicyPO po = policyMapper.selectById(consumerId);
    if (po == null) {
      po = new DataServiceConsumerIpAccessPolicyPO();
      po.setConsumerId(consumerId);
      po.setMode((mode == null ? IpAccessMode.NONE : mode).name());
      po.setUpdateTime(updateTime);
      policyMapper.insert(po);
      return;
    }
    po.setMode((mode == null ? IpAccessMode.NONE : mode).name());
    po.setUpdateTime(updateTime);
    policyMapper.updateById(po);
  }

  @Override
  public List<DataServiceConsumerIpAccessRule> findRules(Long consumerId) {
    if (consumerId == null) return List.of();
    return ruleMapper.selectList(Wrappers.<DataServiceConsumerIpAccessRulePO>lambdaQuery()
            .eq(DataServiceConsumerIpAccessRulePO::getConsumerId, consumerId)
            .orderByDesc(DataServiceConsumerIpAccessRulePO::getCreateTime)
            .orderByDesc(DataServiceConsumerIpAccessRulePO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<DataServiceConsumerIpAccessRule> findRule(Long id) {
    return Optional.ofNullable(id == null ? null : ruleMapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public boolean existsRule(
      Long consumerId, IpAccessRuleType type, String networkCidr, Long excludeId) {
    if (consumerId == null || type == null || networkCidr == null) return false;
    Long count = ruleMapper.selectCount(Wrappers.<DataServiceConsumerIpAccessRulePO>lambdaQuery()
        .eq(DataServiceConsumerIpAccessRulePO::getConsumerId, consumerId)
        .eq(DataServiceConsumerIpAccessRulePO::getRuleType, type.name())
        .eq(DataServiceConsumerIpAccessRulePO::getNetworkCidr, networkCidr)
        .ne(excludeId != null, DataServiceConsumerIpAccessRulePO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public DataServiceConsumerIpAccessRule saveRule(DataServiceConsumerIpAccessRule rule) {
    DataServiceConsumerIpAccessRulePO po = toPo(rule);
    if (rule.id() == null) ruleMapper.insert(po); else ruleMapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public boolean deleteRule(Long id) {
    return id != null && ruleMapper.deleteById(id) > 0;
  }

  @Override
  public void deleteByConsumerId(Long consumerId) {
    if (consumerId == null) return;
    ruleMapper.delete(Wrappers.<DataServiceConsumerIpAccessRulePO>lambdaQuery()
        .eq(DataServiceConsumerIpAccessRulePO::getConsumerId, consumerId));
    policyMapper.deleteById(consumerId);
  }

  private DataServiceConsumerIpAccessRule toDomain(DataServiceConsumerIpAccessRulePO po) {
    return new DataServiceConsumerIpAccessRule(
        po.getId(), po.getConsumerId(), IpAccessRuleType.parse(po.getRuleType()),
        po.getNetworkCidr(), po.getDescription(), Boolean.TRUE.equals(po.getEnabled()),
        po.getExpiresAt(), po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceConsumerIpAccessRulePO toPo(DataServiceConsumerIpAccessRule rule) {
    DataServiceConsumerIpAccessRulePO po = new DataServiceConsumerIpAccessRulePO();
    po.setId(rule.id());
    po.setConsumerId(rule.consumerId());
    po.setRuleType(rule.ruleType().name());
    po.setNetworkCidr(rule.networkCidr());
    po.setDescription(rule.description());
    po.setEnabled(rule.enabled());
    po.setExpiresAt(rule.expiresAt());
    po.setCreateTime(rule.createTime());
    po.setUpdateTime(rule.updateTime());
    return po;
  }
}

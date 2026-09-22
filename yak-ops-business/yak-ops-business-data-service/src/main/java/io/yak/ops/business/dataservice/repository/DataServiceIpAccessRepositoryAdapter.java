package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceIpAccessPolicyMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceIpAccessRuleMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceIpAccessPolicyPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceIpAccessRulePO;
import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
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
public class DataServiceIpAccessRepositoryAdapter implements DataServiceIpAccessRepository {
  private final DataServiceIpAccessPolicyMapper policyMapper;
  private final DataServiceIpAccessRuleMapper ruleMapper;

  @Override
  public IpAccessMode findMode(Long apiId) {
    DataServiceIpAccessPolicyPO po = apiId == null ? null : policyMapper.selectById(apiId);
    return po == null ? IpAccessMode.NONE : IpAccessMode.parse(po.getMode());
  }

  @Override
  public void saveMode(Long apiId, IpAccessMode mode, LocalDateTime updateTime) {
    if (apiId == null) throw new IllegalArgumentException("数据服务 API ID 不能为空");
    DataServiceIpAccessPolicyPO po = policyMapper.selectById(apiId);
    if (po == null) {
      po = new DataServiceIpAccessPolicyPO();
      po.setApiId(apiId);
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
  public List<DataServiceIpAccessRule> findRules(Long apiId) {
    if (apiId == null) return List.of();
    return ruleMapper.selectList(
            Wrappers.<DataServiceIpAccessRulePO>lambdaQuery()
                .eq(DataServiceIpAccessRulePO::getApiId, apiId)
                .orderByDesc(DataServiceIpAccessRulePO::getCreateTime)
                .orderByDesc(DataServiceIpAccessRulePO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<DataServiceIpAccessRule> findRule(Long id) {
    return Optional.ofNullable(id == null ? null : ruleMapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public boolean existsRule(
      Long apiId, IpAccessRuleType ruleType, String networkCidr, Long excludeId) {
    if (apiId == null || ruleType == null || networkCidr == null) return false;
    Long count = ruleMapper.selectCount(
        Wrappers.<DataServiceIpAccessRulePO>lambdaQuery()
            .eq(DataServiceIpAccessRulePO::getApiId, apiId)
            .eq(DataServiceIpAccessRulePO::getRuleType, ruleType.name())
            .eq(DataServiceIpAccessRulePO::getNetworkCidr, networkCidr)
            .ne(excludeId != null, DataServiceIpAccessRulePO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public DataServiceIpAccessRule saveRule(DataServiceIpAccessRule rule) {
    DataServiceIpAccessRulePO po = toPo(rule);
    if (rule.id() == null) ruleMapper.insert(po); else ruleMapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public boolean deleteRule(Long id) {
    return id != null && ruleMapper.deleteById(id) > 0;
  }

  @Override
  public void deleteByApiId(Long apiId) {
    if (apiId == null) return;
    ruleMapper.delete(
        Wrappers.<DataServiceIpAccessRulePO>lambdaQuery()
            .eq(DataServiceIpAccessRulePO::getApiId, apiId));
    policyMapper.deleteById(apiId);
  }

  private DataServiceIpAccessRule toDomain(DataServiceIpAccessRulePO po) {
    return new DataServiceIpAccessRule(
        po.getId(), po.getApiId(), IpAccessRuleType.parse(po.getRuleType()), po.getNetworkCidr(),
        po.getDescription(), Boolean.TRUE.equals(po.getEnabled()), po.getExpiresAt(),
        po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceIpAccessRulePO toPo(DataServiceIpAccessRule rule) {
    DataServiceIpAccessRulePO po = new DataServiceIpAccessRulePO();
    po.setId(rule.id());
    po.setApiId(rule.apiId());
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

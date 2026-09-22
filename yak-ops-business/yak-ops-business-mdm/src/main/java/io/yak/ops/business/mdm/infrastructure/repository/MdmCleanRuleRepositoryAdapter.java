package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmCleanRuleMapper;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.common.bean.po.mdm.MdmCleanRulePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the master data cleansing rule (project-scoped). */
@Repository
public class MdmCleanRuleRepositoryAdapter implements MdmCleanRuleRepository {

  private final MdmCleanRuleMapper mapper;
  private final CurrentProject currentProject;

  public MdmCleanRuleRepositoryAdapter(
      MdmCleanRuleMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmCleanRule insert(MdmCleanRule rule, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmCleanRulePO po = toPo(rule);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return rule.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmCleanRule> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmCleanRulePO>()
                    .eq(MdmCleanRulePO::getId, id)
                    .eq(MdmCleanRulePO::getProjectId, projectId)))
        .map(MdmCleanRuleRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmCleanRule> listByEntity(Long entityId, MdmCleanRuleType ruleType) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmCleanRulePO>()
                .eq(MdmCleanRulePO::getProjectId, projectId)
                .eq(MdmCleanRulePO::getEntityId, entityId)
                .eq(ruleType != null, MdmCleanRulePO::getRuleType,
                    ruleType == null ? null : ruleType.name())
                .orderByAsc(MdmCleanRulePO::getSortOrder)
                .orderByAsc(MdmCleanRulePO::getId))
        .stream()
        .map(MdmCleanRuleRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByName(Long entityId, String ruleName, Long excludeId) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmCleanRulePO> wrapper =
        new LambdaQueryWrapper<MdmCleanRulePO>()
            .eq(MdmCleanRulePO::getProjectId, projectId)
            .eq(MdmCleanRulePO::getEntityId, entityId)
            .eq(MdmCleanRulePO::getRuleName, ruleName);
    wrapper.ne(excludeId != null, MdmCleanRulePO::getId, excludeId);
    return mapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean update(MdmCleanRule rule) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmCleanRulePO>()
                .eq(MdmCleanRulePO::getId, rule.id())
                .eq(MdmCleanRulePO::getProjectId, projectId)
                .set(MdmCleanRulePO::getRuleName, rule.ruleName())
                .set(MdmCleanRulePO::getRuleExpr, rule.ruleExpr())
                .set(MdmCleanRulePO::getEnabled, rule.enabled())
                .set(MdmCleanRulePO::getSortOrder, rule.sortOrder()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<MdmCleanRulePO>()
                .eq(MdmCleanRulePO::getId, id)
                .eq(MdmCleanRulePO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmCleanRulePO toPo(MdmCleanRule rule) {
    MdmCleanRulePO po = new MdmCleanRulePO();
    po.setId(rule.id());
    po.setEntityId(rule.entityId());
    po.setRuleType(rule.ruleType() == null ? null : rule.ruleType().name());
    po.setRuleName(rule.ruleName());
    po.setRuleExpr(rule.ruleExpr());
    po.setEnabled(rule.enabled());
    po.setSortOrder(rule.sortOrder());
    return po;
  }

  private static MdmCleanRule toDomain(MdmCleanRulePO po) {
    return new MdmCleanRule(
        po.getId(),
        po.getEntityId(),
        po.getRuleType() == null ? null : MdmCleanRuleType.valueOf(po.getRuleType()),
        po.getRuleName(),
        po.getRuleExpr(),
        Boolean.TRUE.equals(po.getEnabled()),
        po.getSortOrder() == null ? 0 : po.getSortOrder(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}

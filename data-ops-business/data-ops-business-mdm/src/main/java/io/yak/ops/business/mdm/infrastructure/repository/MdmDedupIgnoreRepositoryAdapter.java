package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmDedupIgnoreMapper;
import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import io.yak.ops.business.mdm.dao.model.MdmDedupIgnorePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the dedup "ignore group" ledger (project-scoped). */
@Repository
public class MdmDedupIgnoreRepositoryAdapter implements MdmDedupIgnoreRepository {

  private final MdmDedupIgnoreMapper mapper;
  private final CurrentProject currentProject;

  public MdmDedupIgnoreRepositoryAdapter(
      MdmDedupIgnoreMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public Optional<MdmDedupIgnore> findByKey(Long entityId, Long ruleId, String matchKey) {
    return Optional.ofNullable(
            mapper.selectOne(keyWrapper(requiredProjectId(), entityId, ruleId, matchKey)))
        .map(MdmDedupIgnoreRepositoryAdapter::toDomain);
  }

  @Override
  public MdmDedupIgnore insert(MdmDedupIgnore ignore, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmDedupIgnorePO po = new MdmDedupIgnorePO();
    po.setProjectId(projectId);
    po.setEntityId(ignore.entityId());
    po.setRuleId(ignore.ruleId());
    po.setMatchKey(ignore.matchKey());
    po.setMatchBasis(ignore.matchBasis());
    po.setReason(ignore.reason());
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    mapper.insert(po);
    return new MdmDedupIgnore(
        po.getId(), ignore.entityId(), ignore.ruleId(), ignore.matchKey(),
        ignore.matchBasis(), ignore.reason(), operator, now);
  }

  @Override
  public List<MdmDedupIgnore> listByRule(Long entityId, Long ruleId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmDedupIgnorePO>()
                .eq(MdmDedupIgnorePO::getProjectId, requiredProjectId())
                .eq(MdmDedupIgnorePO::getEntityId, entityId)
                .eq(MdmDedupIgnorePO::getRuleId, ruleId)
                .orderByDesc(MdmDedupIgnorePO::getId))
        .stream()
        .map(MdmDedupIgnoreRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean delete(Long id) {
    return mapper.delete(
            new LambdaQueryWrapper<MdmDedupIgnorePO>()
                .eq(MdmDedupIgnorePO::getId, id)
                .eq(MdmDedupIgnorePO::getProjectId, requiredProjectId()))
        > 0;
  }

  @Override
  public void deleteByRule(Long ruleId) {
    mapper.delete(
        new LambdaQueryWrapper<MdmDedupIgnorePO>()
            .eq(MdmDedupIgnorePO::getProjectId, requiredProjectId())
            .eq(MdmDedupIgnorePO::getRuleId, ruleId));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static LambdaQueryWrapper<MdmDedupIgnorePO> keyWrapper(
      Long projectId, Long entityId, Long ruleId, String matchKey) {
    return new LambdaQueryWrapper<MdmDedupIgnorePO>()
        .eq(MdmDedupIgnorePO::getProjectId, projectId)
        .eq(MdmDedupIgnorePO::getEntityId, entityId)
        .eq(MdmDedupIgnorePO::getRuleId, ruleId)
        .eq(MdmDedupIgnorePO::getMatchKey, matchKey);
  }

  private static MdmDedupIgnore toDomain(MdmDedupIgnorePO po) {
    return new MdmDedupIgnore(
        po.getId(),
        po.getEntityId(),
        po.getRuleId(),
        po.getMatchKey(),
        po.getMatchBasis(),
        po.getReason(),
        po.getCreatedBy(),
        po.getCreateTime());
  }
}

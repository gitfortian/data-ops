package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmMergeLogMapper;
import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
import io.yak.ops.business.mdm.dao.model.MdmMergeLogPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the master data merge log (project-scoped, append-only). */
@Repository
public class MdmMergeLogRepositoryAdapter implements MdmMergeLogRepository {

  private final MdmMergeLogMapper mapper;
  private final CurrentProject currentProject;

  public MdmMergeLogRepositoryAdapter(
      MdmMergeLogMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmMergeLog insert(MdmMergeLog log, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmMergeLogPO po = toPo(log);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    mapper.insert(po);
    return new MdmMergeLog(
        po.getId(), log.entityId(), log.ruleId(), log.masterRecordId(),
        log.mergedRecordIds(), log.result(), operator, now);
  }

  @Override
  public List<MdmMergeLog> listByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmMergeLogPO>()
                .eq(MdmMergeLogPO::getProjectId, projectId)
                .eq(MdmMergeLogPO::getEntityId, entityId)
                .orderByDesc(MdmMergeLogPO::getId))
        .stream()
        .map(MdmMergeLogRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByRuleId(Long ruleId) {
    return mapper.existsByRuleId(requiredProjectId(), ruleId);
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmMergeLogPO toPo(MdmMergeLog log) {
    MdmMergeLogPO po = new MdmMergeLogPO();
    po.setId(log.id());
    po.setEntityId(log.entityId());
    po.setRuleId(log.ruleId());
    po.setMasterRecordId(log.masterRecordId());
    po.setMergedRecordIds(log.mergedRecordIds());
    po.setResult(log.result());
    return po;
  }

  private static MdmMergeLog toDomain(MdmMergeLogPO po) {
    return new MdmMergeLog(
        po.getId(),
        po.getEntityId(),
        po.getRuleId(),
        po.getMasterRecordId(),
        po.getMergedRecordIds(),
        po.getResult(),
        po.getCreatedBy(),
        po.getCreateTime());
  }
}

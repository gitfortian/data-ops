package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the master data distribution config (project-scoped, ticket 58). */
@Repository
public class MdmDistributionRepositoryAdapter implements MdmDistributionRepository {

  private final MdmDistributionMapper mapper;
  private final CurrentProject currentProject;

  public MdmDistributionRepositoryAdapter(
      MdmDistributionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmDistribution insert(MdmDistribution distribution, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmDistributionPO po = toPo(distribution);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return distribution.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmDistribution> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmDistributionPO>()
                    .eq(MdmDistributionPO::getId, id)
                    .eq(MdmDistributionPO::getProjectId, projectId)))
        .map(MdmDistributionRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmDistribution> listByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmDistributionPO>()
                .eq(MdmDistributionPO::getProjectId, projectId)
                .eq(MdmDistributionPO::getEntityId, entityId)
                .orderByAsc(MdmDistributionPO::getId))
        .stream()
        .map(MdmDistributionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByTarget(Long entityId, String targetSystem, String mode, Long excludeId) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmDistributionPO> wrapper =
        new LambdaQueryWrapper<MdmDistributionPO>()
            .eq(MdmDistributionPO::getProjectId, projectId)
            .eq(MdmDistributionPO::getEntityId, entityId)
            .eq(MdmDistributionPO::getTargetSystem, targetSystem)
            .eq(MdmDistributionPO::getDistributeMode, mode);
    wrapper.ne(excludeId != null, MdmDistributionPO::getId, excludeId);
    return mapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean update(MdmDistribution distribution) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmDistributionPO>()
                .eq(MdmDistributionPO::getId, distribution.id())
                .eq(MdmDistributionPO::getProjectId, projectId)
                .set(MdmDistributionPO::getTargetName, distribution.targetName())
                .set(MdmDistributionPO::getDistributeMode, distribution.mode().name())
                .set(MdmDistributionPO::getDistributeFreq, distribution.frequency())
                .set(MdmDistributionPO::getDistributeScope, distribution.scope())
                .set(MdmDistributionPO::getStatus, distribution.status().name()))
        > 0;
  }

  @Override
  public boolean updateResult(MdmDistribution distribution) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmDistributionPO>()
                .eq(MdmDistributionPO::getId, distribution.id())
                .eq(MdmDistributionPO::getProjectId, projectId)
                .set(MdmDistributionPO::getLastDistributeTime, distribution.lastDistributeTime())
                .set(MdmDistributionPO::getLastDistributeCount, distribution.lastDistributeCount())
                .set(MdmDistributionPO::getLastDistributeFail, distribution.lastDistributeFail()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<MdmDistributionPO>()
                .eq(MdmDistributionPO::getId, id)
                .eq(MdmDistributionPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public long countActiveByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MdmDistributionPO>()
            .eq(MdmDistributionPO::getProjectId, projectId)
            .eq(MdmDistributionPO::getEntityId, entityId)
            .eq(MdmDistributionPO::getStatus, MdmDistributionStatus.ACTIVE.name()));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmDistributionPO toPo(MdmDistribution d) {
    MdmDistributionPO po = new MdmDistributionPO();
    po.setId(d.id());
    po.setEntityId(d.entityId());
    po.setTargetSystem(d.targetSystem());
    po.setTargetName(d.targetName());
    po.setDistributeMode(d.mode() == null ? null : d.mode().name());
    po.setDistributeFreq(d.frequency());
    po.setDistributeScope(d.scope());
    po.setStatus(d.status() == null ? null : d.status().name());
    po.setLastDistributeTime(d.lastDistributeTime());
    po.setLastDistributeCount(d.lastDistributeCount());
    po.setLastDistributeFail(d.lastDistributeFail());
    return po;
  }

  private static MdmDistribution toDomain(MdmDistributionPO po) {
    return new MdmDistribution(
        po.getId(),
        po.getEntityId(),
        po.getTargetSystem(),
        po.getTargetName(),
        po.getDistributeMode() == null
            ? null : MdmDistributionMode.valueOf(po.getDistributeMode()),
        po.getDistributeFreq(),
        po.getDistributeScope(),
        po.getStatus() == null
            ? null : MdmDistributionStatus.valueOf(po.getStatus()),
        po.getLastDistributeTime(),
        po.getLastDistributeCount() == null ? 0 : po.getLastDistributeCount(),
        po.getLastDistributeFail() == null ? 0 : po.getLastDistributeFail(),
        po.getCreatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}

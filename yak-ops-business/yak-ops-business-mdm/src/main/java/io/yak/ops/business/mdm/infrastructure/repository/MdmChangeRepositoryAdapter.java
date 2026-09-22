package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.dao.mapper.MdmChangeMapper;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.approval.MdmChangeType;
import io.yak.ops.common.bean.po.mdm.MdmChangePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis adapter for the master data change/approval (project-scoped, ticket 60). */
@Repository
public class MdmChangeRepositoryAdapter implements MdmChangeRepository {

  private final MdmChangeMapper mapper;
  private final CurrentProject currentProject;

  public MdmChangeRepositoryAdapter(MdmChangeMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmChange insert(MdmChange change) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmChangePO po = toPo(change);
    po.setProjectId(projectId);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return change.withPersisted(po.getId(), now);
  }

  @Override
  public Optional<MdmChange> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmChangePO>()
                    .eq(MdmChangePO::getId, id)
                    .eq(MdmChangePO::getProjectId, projectId)))
        .map(MdmChangeRepositoryAdapter::toDomain);
  }

  @Override
  public PageData<MdmChange> page(
      Long entityId, String applicant, MdmApprovalStatus status, int pageNo, int pageSize) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmChangePO> wrapper =
        new LambdaQueryWrapper<MdmChangePO>()
            .eq(MdmChangePO::getProjectId, projectId)
            .eq(entityId != null, MdmChangePO::getEntityId, entityId)
            .eq(StringUtils.hasText(applicant), MdmChangePO::getApplicant, applicant)
            .eq(status != null, MdmChangePO::getApprovalStatus, status != null ? status.name() : null);
    wrapper.orderByDesc(MdmChangePO::getCreateTime).orderByDesc(MdmChangePO::getId);
    Page<MdmChangePO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords().stream().map(MdmChangeRepositoryAdapter::toDomain).toList(),
        result.getTotal(),
        result.getPages(),
        (long) pageNo,
        (long) pageSize);
  }

  @Override
  public List<MdmChange> listByMaster(Long entityId, String masterId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmChangePO>()
                .eq(MdmChangePO::getProjectId, projectId)
                .eq(MdmChangePO::getEntityId, entityId)
                .eq(MdmChangePO::getMasterId, masterId)
                .orderByDesc(MdmChangePO::getCreateTime))
        .stream()
        .map(MdmChangeRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public long countPending(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MdmChangePO>()
            .eq(MdmChangePO::getProjectId, projectId)
            .eq(entityId != null, MdmChangePO::getEntityId, entityId)
            .eq(MdmChangePO::getApprovalStatus, MdmApprovalStatus.PENDING.name()));
  }

  @Override
  public boolean update(MdmChange change) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmChangePO>()
                .eq(MdmChangePO::getId, change.id())
                .eq(MdmChangePO::getProjectId, projectId)
                .set(MdmChangePO::getApprovalStatus, change.approvalStatus().name())
                .set(MdmChangePO::getApprover, change.approver())
                .set(MdmChangePO::getApprovalComment, change.approvalComment())
                .set(MdmChangePO::getApprovalTime, change.approvalTime())
                .set(MdmChangePO::getInstanceId, change.instanceId()))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmChangePO toPo(MdmChange c) {
    MdmChangePO po = new MdmChangePO();
    po.setId(c.id());
    po.setEntityId(c.entityId());
    po.setMasterId(c.masterId());
    po.setChangeType(c.changeType().name());
    po.setChangeContent(c.changeContent());
    po.setApprovalLevel(c.approvalLevel());
    po.setApprovalStatus(c.approvalStatus().name());
    po.setApplicant(c.applicant());
    po.setApprover(c.approver());
    po.setApprovalComment(c.approvalComment());
    po.setApprovalTime(c.approvalTime());
    po.setInstanceId(c.instanceId());
    return po;
  }

  private static MdmChange toDomain(MdmChangePO po) {
    return new MdmChange(
        po.getId(), po.getEntityId(), po.getMasterId(),
        MdmChangeType.valueOf(po.getChangeType()),
        po.getChangeContent(),
        po.getApprovalLevel() == null ? MdmChange.LEVEL_ONE : po.getApprovalLevel(),
        MdmApprovalStatus.valueOf(po.getApprovalStatus()),
        po.getApplicant(), po.getApprover(), po.getApprovalComment(),
        po.getApprovalTime(), po.getInstanceId(), po.getCreateTime(), po.getUpdateTime());
  }
}

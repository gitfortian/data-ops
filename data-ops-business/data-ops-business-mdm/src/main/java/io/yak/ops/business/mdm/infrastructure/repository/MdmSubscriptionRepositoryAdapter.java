package io.yak.ops.business.mdm.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.mdm.dao.mapper.MdmSubscriptionMapper;
import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import io.yak.ops.business.mdm.dao.model.MdmSubscriptionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the master data subscription (project-scoped, ticket 59). */
@Repository
public class MdmSubscriptionRepositoryAdapter implements MdmSubscriptionRepository {

  private final MdmSubscriptionMapper mapper;
  private final CurrentProject currentProject;

  public MdmSubscriptionRepositoryAdapter(
      MdmSubscriptionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmSubscription insert(MdmSubscription sub, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MdmSubscriptionPO po = toPo(sub);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return sub.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<MdmSubscription> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MdmSubscriptionPO>()
                    .eq(MdmSubscriptionPO::getId, id)
                    .eq(MdmSubscriptionPO::getProjectId, projectId)))
        .map(MdmSubscriptionRepositoryAdapter::toDomain);
  }

  @Override
  public List<MdmSubscription> listByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmSubscriptionPO>()
                .eq(MdmSubscriptionPO::getProjectId, projectId)
                .eq(MdmSubscriptionPO::getEntityId, entityId)
                .orderByAsc(MdmSubscriptionPO::getId))
        .stream()
        .map(MdmSubscriptionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MdmSubscription> listActiveByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper
        .selectList(
            new LambdaQueryWrapper<MdmSubscriptionPO>()
                .eq(MdmSubscriptionPO::getProjectId, projectId)
                .eq(MdmSubscriptionPO::getEntityId, entityId)
                .eq(MdmSubscriptionPO::getStatus, MdmSubscription.STATUS_ACTIVE)
                .orderByAsc(MdmSubscriptionPO::getId))
        .stream()
        .map(MdmSubscriptionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsBySubscriber(Long entityId, String subscriberCode, Long excludeId) {
    Long projectId = requiredProjectId();
    LambdaQueryWrapper<MdmSubscriptionPO> wrapper =
        new LambdaQueryWrapper<MdmSubscriptionPO>()
            .eq(MdmSubscriptionPO::getProjectId, projectId)
            .eq(MdmSubscriptionPO::getEntityId, entityId)
            .eq(MdmSubscriptionPO::getSubscriberCode, subscriberCode);
    wrapper.ne(excludeId != null, MdmSubscriptionPO::getId, excludeId);
    return mapper.selectCount(wrapper) > 0;
  }

  @Override
  public boolean update(MdmSubscription sub) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<MdmSubscriptionPO>()
                .eq(MdmSubscriptionPO::getId, sub.id())
                .eq(MdmSubscriptionPO::getProjectId, projectId)
                .set(MdmSubscriptionPO::getSubscriberName, sub.subscriberName())
                .set(MdmSubscriptionPO::getNotifyMode, sub.notifyMode())
                .set(MdmSubscriptionPO::getStatus, sub.status()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<MdmSubscriptionPO>()
                .eq(MdmSubscriptionPO::getId, id)
                .eq(MdmSubscriptionPO::getProjectId, projectId))
        > 0;
  }

  @Override
  public long countActiveByEntity(Long entityId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MdmSubscriptionPO>()
            .eq(MdmSubscriptionPO::getProjectId, projectId)
            .eq(MdmSubscriptionPO::getEntityId, entityId)
            .eq(MdmSubscriptionPO::getStatus, MdmSubscription.STATUS_ACTIVE));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MdmSubscriptionPO toPo(MdmSubscription s) {
    MdmSubscriptionPO po = new MdmSubscriptionPO();
    po.setId(s.id());
    po.setEntityId(s.entityId());
    po.setSubscriberCode(s.subscriberCode());
    po.setSubscriberName(s.subscriberName());
    po.setNotifyMode(s.notifyMode());
    po.setStatus(s.status());
    return po;
  }

  private static MdmSubscription toDomain(MdmSubscriptionPO po) {
    return new MdmSubscription(
        po.getId(), po.getEntityId(), po.getSubscriberCode(), po.getSubscriberName(),
        po.getNotifyMode(), po.getStatus(), po.getCreatedBy(),
        po.getCreateTime(), po.getUpdateTime());
  }
}

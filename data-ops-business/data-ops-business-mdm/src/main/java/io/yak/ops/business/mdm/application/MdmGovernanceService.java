package io.yak.ops.business.mdm.application;

import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import org.springframework.stereotype.Service;

/**
 * 主数据治理(ticket 61):质量/血缘/权限的聚合视图。
 * 最小化设计:质量/血缘复用 quality/lineage 模块,MDM 只做聚合展示;
 * 实体 owner 为轻量权限控制。
 */
@Service
public class MdmGovernanceService {

  private final MdmEntityService entityService;
  private final MdmSourceRepository sourceRepository;
  private final MdmRecordRepository recordRepository;
  private final MdmDistributionRepository distributionRepository;
  private final MdmSubscriptionRepository subscriptionRepository;
  private final MdmChangeRepository changeRepository;

  public MdmGovernanceService(
      MdmEntityService entityService,
      MdmSourceRepository sourceRepository,
      MdmRecordRepository recordRepository,
      MdmDistributionRepository distributionRepository,
      MdmSubscriptionRepository subscriptionRepository,
      MdmChangeRepository changeRepository) {
    this.entityService = entityService;
    this.sourceRepository = sourceRepository;
    this.recordRepository = recordRepository;
    this.distributionRepository = distributionRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.changeRepository = changeRepository;
  }

  /** 实体治理概览:上游来源数、ACTIVE 记录数、下游分发/订阅目标数、待审批数。 */
  public GovernanceSummary getSummary(Long entityId) {
    entityService.get(entityId);
    long sourceCount = safeCount(() -> sourceRepository.listByEntity(entityId).size());
    long activeRecords = safeCount(() -> recordRepository.countActiveByEntity(entityId));
    long distributionTargets = safeCount(() -> distributionRepository.countActiveByEntity(entityId));
    long subscribers = safeCount(() -> subscriptionRepository.countActiveByEntity(entityId));
    long pendingChanges = safeCount(() -> changeRepository.countPending(entityId));
    return new GovernanceSummary(
        entityId, sourceCount, activeRecords, distributionTargets, subscribers, pendingChanges);
  }

  /** 实体 owner 检查:owner 匹配或操作者为空时放行。 */
  public boolean isOwner(Long entityId, String operator) {
    MdmEntity entity = entityService.get(entityId);
    if (entity.owner() == null || operator == null) {
      return true;
    }
    return entity.owner().equals(operator);
  }

  private static long safeCount(java.util.function.LongSupplier supplier) {
    try {
      return supplier.getAsLong();
    } catch (RuntimeException ignored) {
      return -1L;
    }
  }

  /** 治理概览 DTO(每张卡片独立容错:-1 表示查不到)。 */
  public record GovernanceSummary(
      Long entityId,
      long sourceCount,
      long activeRecords,
      long distributionTargets,
      long subscribers,
      long pendingChanges) {}
}

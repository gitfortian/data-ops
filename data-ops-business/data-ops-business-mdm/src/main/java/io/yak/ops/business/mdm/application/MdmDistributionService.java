package io.yak.ops.business.mdm.application;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.business.mdm.schedule.MdmDistributionScheduleEngineBridge;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 主数据分发服务(ticket 58):配置 CRUD + 手动分发执行。
 * 最小化设计:API 方式复用 data-service(后续增量);MESSAGE/FILE 为占位,执行返回"暂不支持"。
 */
@Service
public class MdmDistributionService {

  private static final int MAX_TARGET_SYSTEM_LENGTH = 64;
  private static final int MAX_TARGET_NAME_LENGTH = 128;
  private static final List<String> FREQUENCIES = List.of("MANUAL", "DAILY", "HOURLY");

  private final MdmDistributionRepository repository;
  private final MdmEntityService entityService;
  private final BusinessAuditService auditService;
  private final MdmDistributionPublishService publishService;
  private final MdmRecordRepository recordRepository;
  private final MdmDistributionScheduleEngineBridge scheduleBridge;
  private final MdmNotifier notifier;

  public MdmDistributionService(
      MdmDistributionRepository repository,
      MdmEntityService entityService,
      BusinessAuditService auditService,
      MdmDistributionPublishService publishService,
      MdmRecordRepository recordRepository,
      MdmDistributionScheduleEngineBridge scheduleBridge,
      MdmNotifier notifier) {
    this.repository = repository;
    this.entityService = entityService;
    this.auditService = auditService;
    this.publishService = publishService;
    this.recordRepository = recordRepository;
    this.scheduleBridge = scheduleBridge;
    this.notifier = notifier;
  }

  // ==== 查询 ====

  public MdmDistribution get(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发配置不存在: " + id));
  }

  public List<MdmDistribution> listByEntity(Long entityId) {
    return repository.listByEntity(entityId);
  }

  public long countActiveByEntity(Long entityId) {
    return repository.countActiveByEntity(entityId);
  }

  // ==== CRUD ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmDistribution create(
      Long entityId, String targetSystem, String targetName,
      MdmDistributionMode mode, String frequency, String scope, String operator) {
    entityService.get(entityId);
    validateConfig(entityId, targetSystem, targetName, mode, frequency);
    if (repository.existsByTarget(entityId, targetSystem, mode.name(), null)) {
      throw new MdmException(
          MdmErrorCode.DISTRIBUTE_FAILED,
          "该实体已存在相同方式的分发配置: " + targetSystem + "/" + mode);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DISTRIBUTION_CREATE",
                "Create distribution config",
                "MDM_DISTRIBUTION",
                null,
                targetSystem,
                "APPLICATION",
                Map.of(
                    "entityId", String.valueOf(entityId),
                    "mode", mode.name())));
    try {
      MdmDistribution inserted =
          repository.insert(
              new MdmDistribution(
                  null, entityId, targetSystem,
                  targetName == null ? "" : targetName,
                  mode,
                  frequency == null ? "MANUAL" : frequency,
                  scope == null ? "FULL" : scope,
                  MdmDistributionStatus.DRAFT,
                  null, 0, 0, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Distribution config created",
          Map.of(), "Distribution config created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_DISTRIBUTION_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(
      Long id, String targetName, MdmDistributionMode mode,
      String frequency, String scope) {
    MdmDistribution existing = get(id);
    requireFrequency(frequency);
    MdmDistribution updated = existing.withEditable(targetName, mode, frequency, scope);
    if (mode != null && !mode.equals(existing.mode())) {
      if (repository.existsByTarget(
          existing.entityId(), existing.targetSystem(), mode.name(), id)) {
        throw new MdmException(
            MdmErrorCode.DISTRIBUTE_FAILED,
            "该实体已存在相同方式的分发配置: " + existing.targetSystem() + "/" + mode);
      }
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DISTRIBUTION_UPDATE",
                "Update distribution config",
                "MDM_DISTRIBUTION",
                String.valueOf(id),
                existing.targetSystem(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      if (!repository.update(updated)) {
        throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发配置更新失败");
      }
      scheduleBridge.sync(id);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Distribution config updated",
          Map.of(), "Distribution config updated");
    } catch (RuntimeException exception) {
      audit.failure("MDM_DISTRIBUTION_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void setStatus(Long id, MdmDistributionStatus status) {
    MdmDistribution existing = get(id);
    validateStatusTransition(existing.status(), status);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DISTRIBUTION_STATUS",
                "Toggle distribution status",
                "MDM_DISTRIBUTION",
                String.valueOf(id),
                existing.targetSystem(),
                "APPLICATION",
                Map.of("status", status.name())));
    try {
      if (!repository.update(existing.withStatus(status))) {
        throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发配置状态更新失败");
      }
      scheduleBridge.sync(id);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Distribution status changed",
          Map.of(), "Distribution status changed");
    } catch (RuntimeException exception) {
      audit.failure("MDM_DISTRIBUTION_STATUS_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    MdmDistribution existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DISTRIBUTION_DELETE",
                "Delete distribution config",
                "MDM_DISTRIBUTION",
                String.valueOf(id),
                existing.targetSystem(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      if (!repository.deleteById(id)) {
        throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发配置删除失败");
      }
      scheduleBridge.deleteIfPresent(id);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED, "Distribution config deleted",
          Map.of(), "Distribution config deleted");
    } catch (RuntimeException exception) {
      audit.failure("MDM_DISTRIBUTION_DELETE_FAILED", exception);
      throw exception;
    }
  }

  // ==== 分发执行(R5:API=发布/刷新数据服务,外部调用方实时读表;MESSAGE/FILE 未接入) ====

  /**
   * 执行分发(review P0-1.8 语义修正):
   * API 模式=幂等推送至数据服务发布态(publish/republish/启用)+ 回写当前可供数条数;
   * MESSAGE/FILE 明确抛"未接入",不落任何执行结果——占位假成功比失败更危险。
   * 任何一步失败异常透出、不写时间戳。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DistributionResult execute(Long id, String operator) {
    MdmDistribution config = get(id);
    if (config.status() != MdmDistributionStatus.ACTIVE) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "仅生效状态的分发配置可执行");
    }
    if (entityService.get(config.entityId()).status() != MdmEntityStatus.ACTIVE) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "主数据实体未生效，不能发布或刷新供数 API");
    }
    if (config.mode() != MdmDistributionMode.API) {
      throw new MdmException(
          MdmErrorCode.DISTRIBUTE_FAILED,
          config.mode() + " 分发通道未接入(后续增量复用 alert/storage),本次不执行也不记录分发结果");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DISTRIBUTION_EXECUTE",
                "Execute distribution: " + config.mode().name().toLowerCase(Locale.ROOT),
                "MDM_DISTRIBUTION",
                String.valueOf(id),
                config.targetSystem(),
                "APPLICATION",
                Map.of(
                    "entityId", String.valueOf(config.entityId()),
                    "mode", config.mode().name())));
    try {
      MdmDistributionPublishService.PublishOutcome outcome = publishService.online(config);
      DataServiceView api = outcome.view();
      int count =
          (int) recordRepository.countByEntity(config.entityId(), MdmRecordStatus.ACTIVE);
      LocalDateTime now = LocalDateTime.now();
      MdmDistribution updated = config.withResult(now, count, 0);
      repository.updateResult(updated);
      if (outcome.changed()) {
        notifier.distributionPublished(
            config.entityId(), config.targetSystem(), count, api.path());
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED,
          "Distribution published: " + count + " records servable",
          Map.of("apiId", String.valueOf(api.id()), "path", String.valueOf(api.path())),
          "Distribution executed");
      return new DistributionResult(id, config.mode().name(), count, 0, now, api.id(), api.path());
    } catch (RuntimeException exception) {
      audit.failure("MDM_DISTRIBUTION_EXECUTE_FAILED", exception);
      throw exception;
    }
  }

  // ==== 校验 ====

  private void validateConfig(
      Long entityId, String targetSystem, String targetName, MdmDistributionMode mode,
      String frequency) {
    if (!StringUtils.hasText(targetSystem) || targetSystem.length() > MAX_TARGET_SYSTEM_LENGTH) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "目标系统编码必填且不超过 64 字符");
    }
    if (targetName != null && targetName.length() > MAX_TARGET_NAME_LENGTH) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "目标系统名称不超过 128 字符");
    }
    if (mode == null) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发方式不能为空");
    }
    requireFrequency(frequency);
  }

  /** 频率决定闹钟是否真实存在:写错值等于静默不调度,必须在入口拒掉(P0-1.8)。 */
  private void requireFrequency(String frequency) {
    if (StringUtils.hasText(frequency) && !FREQUENCIES.contains(frequency)) {
      throw new MdmException(
          MdmErrorCode.DISTRIBUTE_FAILED,
          "分发频率仅支持 " + String.join("/", FREQUENCIES) + ": " + frequency);
    }
  }

  private void validateStatusTransition(
      MdmDistributionStatus current, MdmDistributionStatus target) {
    boolean valid =
        switch (current) {
          case DRAFT -> target == MdmDistributionStatus.ACTIVE;
          case ACTIVE -> target == MdmDistributionStatus.DISABLED;
          case DISABLED -> target == MdmDistributionStatus.ACTIVE;
        };
    if (!valid) {
      throw new MdmException(
          MdmErrorCode.DISTRIBUTE_FAILED,
          "状态流转不合法: " + current + " → " + target);
    }
  }

  // ==== DTO ====

  public record DistributionResult(
      Long id,
      String mode,
      int count,
      int failCount,
      LocalDateTime executeTime,
      Long apiId,
      String apiPath) {}
}

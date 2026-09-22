package io.yak.ops.business.lifecycle.dispatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lifecycle.binding.ModelTtlBindingService;
import io.yak.ops.business.lifecycle.binding.ModelTtlResolution;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleDispatchRecordMapper;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.business.lifecycle.preview.ConfirmTokenService;
import io.yak.ops.business.lifecycle.schedule.LifecycleScheduleEngineBridge;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.bean.po.lifecycle.LifecycleDispatchRecordPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.DispatchStatus;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.TriggerType;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 策略下发 + 失败重试(ticket 85/86)。D7:每次尝试都落 yak_lc_dispatch_record,
 * Quartz 闹钟丢任务也不丢重试;重试只重放记录内语句(策略变更由 DRIFT 状态驱动再次下发)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtlDispatchService {

  private final io.yak.ops.core.project.CurrentProject currentProject;

  static final int MAX_ATTEMPTS = 5;
  static final int RETRY_INTERVAL_MINUTES = 30;
  static final int EXECUTE_TIMEOUT_SECONDS = 30;

  public record DispatchOutcome(
      Long modelId,
      String modelName,
      boolean attempted,
      boolean success,
      Long recordId,
      String message) {}

  private final LifecycleDispatchRecordMapper recordMapper;
  private final ModelTtlBindingService bindingService;
  private final TtlSqlGateway gateway;
  private final ConfirmTokenService confirmTokenService;
  private final BusinessAuditService auditService;
  private final LifecycleScheduleEngineBridge scheduleBridge;

  /** 批量下发(向导确认路径):逐模型独立成败,一表失败不阻断其余。 */
  public List<DispatchOutcome> dispatch(List<Long> modelIds, String confirmToken, String operator) {
    if (modelIds == null || modelIds.isEmpty()) {
      throw new LifecycleException(LifecycleErrorCode.MODEL_NOT_FOUND, "未选择模型");
    }
    confirmTokenService.validate(currentProject.requireProjectId(), modelIds, confirmToken);
    scheduleBridge.ensureProjectAlarms(currentProject.requireProjectId());
    List<DispatchOutcome> outcomes = new ArrayList<>();
    int ok = 0;
    int fail = 0;
    for (Long modelId : modelIds) {
      outcomes.add(dispatchOne(modelId, TriggerType.MANUAL, operator, outcomes));
      DispatchOutcome last = outcomes.get(outcomes.size() - 1);
      if (!last.attempted()) {
        continue;
      }
      if (last.success()) {
        ok++;
      } else {
        fail++;
      }
    }
    if (ok + fail > 0) {
      AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
          "TTL_DISPATCH", "Dispatch TTL statements", "TTL_DISPATCH",
          null, "batch-" + modelIds.size(), "APPLICATION",
          Map.of("modelIds", modelIds, "success", ok, "failed", fail)));
      AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
          "TTL 下发 " + ok + " 成功 / " + fail + " 失败", Map.of(), null);
    }
    return outcomes;
  }

  /** 调度批量(未下发/漂移模型),跳过确认令牌;由 bootstrap/monitor 挑选目标后调用。 */
  public List<DispatchOutcome> dispatchBatch(List<Long> modelIds, String operator) {
    List<DispatchOutcome> outcomes = new ArrayList<>();
    for (Long modelId : modelIds) {
      outcomes.add(dispatchOne(modelId, TriggerType.BATCH, operator, outcomes));
    }
    return outcomes;
  }

  private DispatchOutcome dispatchOne(Long modelId, TriggerType trigger, String operator,
      List<DispatchOutcome> accumulated) {
    ModelTtlResolution r = bindingService.resolve(modelId);
    if (r.policy() == null) {
      return new DispatchOutcome(modelId, r.model().name(), false, false, null,
          "未配置 TTL 策略,跳过");
    }
    if (!r.previewable()) {
      return new DispatchOutcome(modelId, r.model().name(), false, false, null,
          r.notPreviewableReason());
    }
    LifecycleDispatchRecordPO record = new LifecycleDispatchRecordPO();
    record.setProjectId(currentProject.requireProjectId());
    record.setModelId(modelId);
    record.setPolicyId(r.policy().getId());
    record.setPolicyUpdatedAt(r.policy().getUpdateTime());
    record.setTriggerType(trigger.name());
    record.setDatasourceId(r.layer().datasourceId());
    record.setDatabaseName(r.layer().databaseName());
    record.setTableName(r.model().tableName());
    record.setStorageType(r.statement().storageType().name());
    record.setStatement(r.statement().statement());
    record.setStatus(DispatchStatus.SUCCESS.name());
    record.setAttempts(1);
    record.setOperator(operator);
    record.setCreateTime(LocalDateTime.now());
    record.setFinishTime(LocalDateTime.now());
    try {
      gateway.execute(r.layer().datasourceId(), r.statement().statement(), EXECUTE_TIMEOUT_SECONDS);
      recordMapper.insert(record);
      return new DispatchOutcome(modelId, r.model().name(), true, true, record.getId(),
          r.statement().note());
    } catch (Exception e) {
      record.setStatus(DispatchStatus.FAILED.name());
      record.setNextRetryTime(LocalDateTime.now().plusMinutes(RETRY_INTERVAL_MINUTES));
      record.setErrorMessage(abbrev(e.getMessage()));
      recordMapper.insert(record);
      log.warn("TTL dispatch failed, modelId={}", modelId, e);
      return new DispatchOutcome(modelId, r.model().name(), true, false, record.getId(),
          LifecycleErrorCode.DISPATCH_FAILED.getMessage() + ":" + record.getErrorMessage());
    }
  }

  /** 手工/闹钟重试:重放记录中的语句,退避 30min×attempts,上限 5 次。 */
  public DispatchOutcome retry(Long recordId, String operator, TriggerType trigger) {
    LifecycleDispatchRecordPO record = requireRecord(recordId);
    if (DispatchStatus.SUCCESS.name().equals(record.getStatus())) {
      return new DispatchOutcome(record.getModelId(), null, false, true, recordId, "该记录已成功下发");
    }
    record.setAttempts((record.getAttempts() == null ? 1 : record.getAttempts()) + 1);
    record.setTriggerType(trigger.name());
    record.setOperator(operator);
    record.setFinishTime(LocalDateTime.now());
    try {
      gateway.execute(record.getDatasourceId(), record.getStatement(), EXECUTE_TIMEOUT_SECONDS);
      record.setStatus(DispatchStatus.SUCCESS.name());
      record.setErrorMessage(null);
      record.setNextRetryTime(null);
    } catch (Exception e) {
      boolean exhausted = record.getAttempts() >= MAX_ATTEMPTS;
      record.setStatus(exhausted ? DispatchStatus.EXHAUSTED.name() : DispatchStatus.RETRYING.name());
      record.setErrorMessage(abbrev(e.getMessage()));
      record.setNextRetryTime(exhausted ? null
          : LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * record.getAttempts()));
      log.warn("TTL retry failed, recordId={} attempt={}", recordId, record.getAttempts(), e);
    }
    recordMapper.updateById(record);
    return new DispatchOutcome(record.getModelId(), null, true,
        DispatchStatus.SUCCESS.name().equals(record.getStatus()), recordId,
        record.getErrorMessage() == null ? "重试成功" : record.getErrorMessage());
  }

  /** 调度闹钟:到期失败记录逐条重放。返回处理条数。 */
  public int retryDue() {
    List<LifecycleDispatchRecordPO> due = recordMapper.selectList(
        new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
            .eq(LifecycleDispatchRecordPO::getProjectId, currentProject.requireProjectId())
            .in(LifecycleDispatchRecordPO::getStatus,
                DispatchStatus.FAILED.name(), DispatchStatus.RETRYING.name())
            .lt(LifecycleDispatchRecordPO::getAttempts, MAX_ATTEMPTS)
            .isNotNull(LifecycleDispatchRecordPO::getNextRetryTime)
            .le(LifecycleDispatchRecordPO::getNextRetryTime, LocalDateTime.now())
            .last("LIMIT 50"));
    for (LifecycleDispatchRecordPO record : due) {
      try {
        retry(record.getId(), "system", TriggerType.RETRY);
      } catch (RuntimeException e) {
        log.warn("TTL retry alarm skipped record {}", record.getId(), e);
      }
    }
    return due.size();
  }

  public PageData<LifecycleDispatchRecordPO> pageRecords(int pageNo, int pageSize,
      String status, Long modelId) {
    Page<LifecycleDispatchRecordPO> page = recordMapper.selectPage(
        new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
            .eq(LifecycleDispatchRecordPO::getProjectId, currentProject.requireProjectId())
            .eq(status != null && !status.isBlank(),
                LifecycleDispatchRecordPO::getStatus, status)
            .eq(modelId != null, LifecycleDispatchRecordPO::getModelId, modelId)
            .orderByDesc(LifecycleDispatchRecordPO::getId));
    return new PageData<>(page.getRecords(), page.getTotal(), page.getPages(),
        (int) page.getCurrent(), (int) page.getSize());
  }

  /** 异常告警区:最近失败/耗尽记录(监控页用)。 */
  public List<LifecycleDispatchRecordPO> recentProblems(int limit) {
    return recordMapper.selectList(new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
        .eq(LifecycleDispatchRecordPO::getProjectId, currentProject.requireProjectId())
        .in(LifecycleDispatchRecordPO::getStatus,
            DispatchStatus.FAILED.name(), DispatchStatus.RETRYING.name(),
            DispatchStatus.EXHAUSTED.name())
        .orderByDesc(LifecycleDispatchRecordPO::getId)
        .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
  }

  private LifecycleDispatchRecordPO requireRecord(Long id) {
    LifecycleDispatchRecordPO record = recordMapper.selectOne(
        new LambdaQueryWrapper<LifecycleDispatchRecordPO>()
            .eq(LifecycleDispatchRecordPO::getProjectId, currentProject.requireProjectId())
            .eq(LifecycleDispatchRecordPO::getId, id));
    if (record == null) {
      throw new LifecycleException(LifecycleErrorCode.DISPATCH_FAILED, "下发记录不存在 id=" + id);
    }
    return record;
  }

  private static String abbrev(String msg) {
    if (msg == null) {
      return "未知错误";
    }
    return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
  }
}

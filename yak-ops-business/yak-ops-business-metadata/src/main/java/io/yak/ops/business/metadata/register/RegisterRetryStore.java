package io.yak.ops.business.metadata.register;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.dao.mapper.MdRegisterRetryMapper;
import io.yak.ops.common.bean.po.metadata.MdRegisterRetryPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryOperation;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * {@code yak_md_register_retry}：写时登记的 outbox（ticket 130，plan §3.2c）。
 *
 * <p>形状逐字沿用本仓库 data-development 的血缘 outbox（{@code due → claim → complete/fail}），
 * 只加自己的一条：{@code attempts≥12} 转 {@code DEAD} 终态。退避
 * {@code min(3600, 1L<<min(12,attempts))} 秒封顶一小时——再密也追不上人，再稀也没到一天。
 *
 * <p>唯一键 {@code uk_yak_md_retry_change} 建在<b>变更</b>上
 * {@code (project_id, type_name, asset_key, source_updated_at)}：同一次变更并发排队天然合并成一条，
 * 而建在 {@code status} 上会让一行 DONE 之后再也排不进新变更。撞键被 catch 成
 * {@link EnqueueResult#ALREADY_QUEUED}，对调用方是成功。
 *
 * <p>重放不回查源域：{@code payload} 存整份命令。排队之后再回查，查到的可能是"更新的现在"，
 * 保序判断（§3.2c 必须 3）就失去了原始变更时刻这一坐标。
 */
@Component
public class RegisterRetryStore {

  /** 第 12 次失败即 DEAD：确定性错误重放一万次也不会成功，出口是对账通道（135）每轮捞回。 */
  static final int MAX_ATTEMPTS = 12;
  /** worker 单轮捞取数（与 DevelopmentLineageWorker 同值）。 */
  static final int POLL_BATCH = 20;
  private static final int ERROR_MAX = 2000;

  private final MdRegisterRetryMapper mapper;
  private final ObjectMapper objectMapper;

  public RegisterRetryStore(MdRegisterRetryMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  /** 排队结局。两者对源域都是"失败已妥善安置"。 */
  public enum EnqueueResult {
    QUEUED,
    ALREADY_QUEUED
  }

  /** worker 眼里的一条待办。{@code attempts} 是<b>认领前</b>的读值，claim 会在库里 +1。 */
  public record Task(
      String taskId,
      long projectId,
      String typeName,
      String sourceId,
      String assetKey,
      RetryOperation operation,
      int attempts,
      String payload) {}

  /** 登记命令整份排队；键列缺失的命令连队列都进不去（NOT NULL），异常上抛给门面落日志。 */
  public EnqueueResult enqueueRegister(long projectId, RegisterCommand command, Throwable failure) {
    requireQueueable(command.getTypeName(), command.getAssetKey(), command.getSourceId());
    if (command.getSourceUpdatedAt() == null) {
      throw new IllegalArgumentException("命令缺少 sourceUpdatedAt（去重键列），无法排队");
    }
    MdRegisterRetryPO row = blankRow(projectId, failure, command.getSourceUpdatedAt());
    row.setTypeName(command.getTypeName());
    row.setAssetKey(command.getAssetKey());
    row.setSourceId(command.getSourceId());
    row.setOperation(RetryOperation.REGISTER.name());
    try {
      row.setPayload(objectMapper.writeValueAsString(command));
    } catch (JsonProcessingException exception) {
      // 序列化不掉的命令重放也拼不回来——这是"进不了队列"的正当形状，交给门面留日志。
      throw new IllegalStateException("RegisterCommand 无法序列化，无法排队", exception);
    }
    return insert(row);
  }

  /** 撤销排队：行上四列已足够重放，payload 留 NULL。 */
  public EnqueueResult enqueueUnregister(
      long projectId, String typeName, String sourceId, String assetKey, Throwable failure) {
    requireQueueable(typeName, assetKey, sourceId);
    MdRegisterRetryPO row = blankRow(projectId, failure, LocalDateTime.now());
    row.setTypeName(typeName);
    row.setAssetKey(assetKey);
    row.setSourceId(sourceId);
    row.setOperation(RetryOperation.UNREGISTER.name());
    return insert(row);
  }

  public List<Task> due(int limit) {
    return mapper.selectDue(limit).stream().map(RegisterRetryStore::toTask).toList();
  }

  /** 原子认领：并发 worker 只有一个改得到状态，其余返回 false 直接跳过。 */
  public boolean claim(Task task) {
    return mapper.claim(task.taskId()) == 1;
  }

  public void complete(Task task) {
    mapper.complete(task.taskId());
  }

  /** 失败回写：退避后回 PENDING；认领后累计到 12 次转 DEAD（终态）。 */
  public void fail(Task task, Throwable failure) {
    // claim 已在库里 attempts+1，所以"下一次是第几次"= 读值 + 1。
    boolean dead = task.attempts() + 1 >= MAX_ATTEMPTS;
    long delay = Math.min(3600, 1L << Math.min(12, task.attempts()));
    String message =
        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    mapper.fail(
        task.taskId(),
        message.substring(0, Math.min(ERROR_MAX, message.length())),
        delay,
        (dead ? RetryStatus.DEAD : RetryStatus.PENDING).name());
  }

  /** 反序列化登记命令（worker 重放入口）。 */
  public RegisterCommand deserialize(String payload) {
    if (payload == null || payload.isBlank()) {
      throw new IllegalStateException("REGISTER 任务缺少 payload，无法重放");
    }
    try {
      return objectMapper.readValue(payload, RegisterCommand.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("payload 无法反序列化为 RegisterCommand", exception);
    }
  }

  private EnqueueResult insert(MdRegisterRetryPO row) {
    try {
      mapper.insert(row);
      return EnqueueResult.QUEUED;
    } catch (DuplicateKeyException exception) {
      // 同一次变更已有队列行——可能就是并发的另一个实例排的那条。这就是幂等，不是失败。
      return EnqueueResult.ALREADY_QUEUED;
    }
  }

  private static void requireQueueable(String typeName, String assetKey, String sourceId) {
    if (typeName == null || typeName.isBlank()
        || assetKey == null || assetKey.isBlank()
        || sourceId == null || sourceId.isBlank()) {
      throw new IllegalArgumentException(
          "命令缺少队列行的 NOT NULL 列（typeName/assetKey/sourceId），无法排队");
    }
  }

  private static MdRegisterRetryPO blankRow(
      long projectId, Throwable failure, LocalDateTime sourceUpdatedAt) {
    MdRegisterRetryPO row = new MdRegisterRetryPO();
    row.setTaskId(UUID.randomUUID().toString());
    row.setProjectId(projectId);
    row.setStatus(RetryStatus.PENDING.name());
    row.setAttempts(0);
    row.setNextAttemptTime(LocalDateTime.now());
    String message = failure == null ? null : failure.getMessage();
    if (message != null) {
      row.setLastError(message.substring(0, Math.min(ERROR_MAX, message.length())));
    }
    row.setSourceUpdatedAt(sourceUpdatedAt);
    LocalDateTime now = LocalDateTime.now();
    row.setCreateTime(now);
    row.setUpdateTime(now);
    return row;
  }

  private static Task toTask(MdRegisterRetryPO row) {
    return new Task(
        row.getTaskId(),
        row.getProjectId(),
        row.getTypeName(),
        row.getSourceId(),
        row.getAssetKey(),
        RetryOperation.valueOf(row.getOperation()),
        row.getAttempts(),
        row.getPayload());
  }
}

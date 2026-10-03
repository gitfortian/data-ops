package io.yak.ops.business.metadata.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 采集/对账闹钟到 Yak Schedule 的适配层（ticket 116，plan §3.7）。
 *
 * <p><b>形态沿用 lifecycle，不参考 OM</b>：OM 的 {@code AppScheduler} 是 Quartz 在 JVM 内，
 * 本仓库已有同构且更适配的 {@link YakScheduleGateway}（模板 {@code LifecycleScheduleEngineBridge}）。
 * 一个启用中的任务一个闹钟，payload 只带 {@code projectId + jobId}——闹钟只负责"到点来看一眼"，
 * 事实源始终是 {@code yak_md_collect_job} 那一行。
 *
 * <p><b>为什么登记是 save 而不是"有则跳过"</b>：{@code cron_expression} 是任务上可改的列，
 * 短路会让改完的调度时间永不生效（写路径看起来成功、引擎里还是老时刻）。
 * 幂等短路只用在启动补齐那条路径上——那里的定义刚由本方法写过，重存一遍只是把引擎搅一遍。
 *
 * <p><b>调度器未装配时静默跳过</b>（plan §3.7）：本平台 Quartz 是内存存储，{@code yak.schedule.enabled=false}
 * 或插件缺席都是常态。此时手工 {@code POST …/collect-jobs/{id}/run} 是唯一通道，
 * 启动绝不能因为"没闹钟"而起不来。
 */
@Slf4j
@Component
public class MetadataScheduleEngineBridge {

  static final String NAMESPACE = YakScheduleNamespaces.DATA_METADATA;
  static final String HANDLER = "metadataCollectScheduleHandler";

  private final YakScheduleGateway gateway;
  private final MdCollectJobMapper jobMapper;

  public MetadataScheduleEngineBridge(
      ObjectProvider<ScheduleManager> scheduleManagers, MdCollectJobMapper jobMapper) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
    this.jobMapper = jobMapper;
  }

  /** 调度器在场与否。前端可用它解释"为什么定时没跑"，测试用它验证静默路径。 */
  public boolean available() {
    return gateway.available();
  }

  /** 登记/更新一个任务的闹钟。调度器未装配、或该通道今天还跑不了时静默返回。 */
  public void register(MdCollectJobPO job) {
    if (job == null || !schedulable(job) || !gateway.available()) {
      return;
    }
    gateway.save(definition(job));
  }

  public void pauseIfPresent(Long jobId) {
    if (jobId != null) {
      gateway.pauseIfPresent(name(jobId));
    }
  }

  public void resumeIfPresent(Long jobId) {
    if (jobId != null) {
      gateway.resumeIfPresent(name(jobId));
    }
  }

  public void deleteIfPresent(Long jobId) {
    if (jobId != null) {
      gateway.deleteIfPresent(name(jobId));
    }
  }

  /**
   * 启动后按业务表补齐闹钟。
   *
   * <p>内存存储在重启后既不补跑、也<b>不保留</b>登记（plan §3.7），于是"任务行是启用状态、引擎里却没有
   * 闹钟"是重启后的默认现场，且没有任何报错。单个任务的登记失败只跳过它自己——一个坏 cron 不该让
   * 其余任务的闹钟都不再登记。
   */
  @Order(40)
  @EventListener(ApplicationReadyEvent.class)
  public void registerEnabledJobs() {
    if (!gateway.available()) {
      log.info("调度器未装配，跳过采集闹钟登记：手工触发仍是可用通道");
      return;
    }
    for (MdCollectJobPO job : enabledJobs()) {
      if (gateway.snapshot(name(job.getId())).isPresent()) {
        continue;
      }
      try {
        register(job);
      } catch (RuntimeException failure) {
        log.warn("采集闹钟登记失败 job={} cron={}", job.getJobCode(), job.getCronExpression(), failure);
      }
    }
  }

  /**
   * 这个任务今天能不能定时跑。
   *
   * <p>只有物理采集通道有实现：{@code REGISTERED} 的执行体是投影对账，由 ticket 135 落地。
   * 现在就给它登记闹钟，等于每天定时撞进一条"通道未落地"的失败，把一个排期事实伪装成运行故障。
   */
  static boolean schedulable(MdCollectJobPO job) {
    return ProviderType.HARVESTED.name().equals(job.getProviderType());
  }

  ScheduleDefinition definition(MdCollectJobPO job) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("projectId", job.getProjectId());
    payload.put("jobId", job.getId());
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("projectId", String.valueOf(job.getProjectId()));
    metadata.put("jobId", String.valueOf(job.getId()));
    metadata.put("providerType", job.getProviderType());
    return new ScheduleDefinition(
        gateway.key(name(job.getId())),
        job.getJobName() + "(job " + job.getId() + ")",
        ScheduleTrigger.cron(job.getCronExpression().trim(), ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        true,
        metadata);
  }

  private static String name(Long jobId) {
    return String.valueOf(jobId);
  }

  /**
   * 跨项目的启动扫描。<b>不带 project_id 条件是故意的</b>：这条查询发生在任何 HTTP 请求之外，
   * 没有可信项目上下文可窄化，而漏掉别的项目的闹钟等于那个项目的采集永久停摆。
   */
  private List<MdCollectJobPO> enabledJobs() {
    List<MdCollectJobPO> rows =
        jobMapper.selectList(
            new LambdaQueryWrapper<MdCollectJobPO>()
                .eq(MdCollectJobPO::getEnabled, true)
                .eq(MdCollectJobPO::getDeleted, false));
    return rows == null ? List.of() : rows;
  }
}

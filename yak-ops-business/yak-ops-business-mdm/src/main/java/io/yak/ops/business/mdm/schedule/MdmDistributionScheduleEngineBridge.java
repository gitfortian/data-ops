package io.yak.ops.business.mdm.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
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
 * 分发配置到 Yak Schedule 的适配层(R5,review P0-1.8:频率字段不再配置即摆设)。
 *
 * <p>形态沿用 metadata/quality 桥接:一个可调度配置一个闹钟,payload 只带
 * {@code projectId + distributionId},事实源是 {@code yak_mdm_distribution} 那一行;
 * 频率用静态 cron(一期无自定义时刻诉求,不加列)。
 * 调度器未装配时静默跳过——手动「执行分发」仍是可用通道,启动绝不因"没闹钟"起不来。
 */
@Slf4j
@Component
public class MdmDistributionScheduleEngineBridge {

  static final String NAMESPACE = YakScheduleNamespaces.MDM_DISTRIBUTION;
  static final String HANDLER = "mdmDistributionScheduleHandler";

  private final YakScheduleGateway gateway;
  private final MdmDistributionMapper distributionMapper;

  public MdmDistributionScheduleEngineBridge(
      ObjectProvider<ScheduleManager> scheduleManagers, MdmDistributionMapper distributionMapper) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
    this.distributionMapper = distributionMapper;
  }

  public boolean available() {
    return gateway.available();
  }

  /** 按当前行状态同步闹钟:可调度→save(upsert 幂等,改频率即时生效);否则确保删除。 */
  public void sync(Long distributionId) {
    if (distributionId == null || !gateway.available()) {
      return;
    }
    MdmDistributionPO config = distributionMapper.selectById(distributionId);
    if (!schedulable(config)) {
      gateway.deleteIfPresent(name(distributionId));
      return;
    }
    gateway.save(definition(config));
  }

  public void deleteIfPresent(Long distributionId) {
    if (distributionId != null) {
      gateway.deleteIfPresent(name(distributionId));
    }
  }

  /** 内存存储重启即失,启动后按业务表补登记(单条失败只 warn,坏配置不拖垮其余闹钟)。 */
  @Order(45)
  @EventListener(ApplicationReadyEvent.class)
  public void registerActiveConfigs() {
    if (!gateway.available()) {
      log.info("调度器未装配,跳过分发闹钟登记:手动执行分发仍是可用通道");
      return;
    }
    List<MdmDistributionPO> rows =
        distributionMapper.selectList(
            new LambdaQueryWrapper<MdmDistributionPO>()
                .eq(MdmDistributionPO::getStatus, MdmDistributionStatus.ACTIVE.name())
                .eq(MdmDistributionPO::getDistributeMode, MdmDistributionMode.API.name())
                .in(MdmDistributionPO::getDistributeFreq, List.of("DAILY", "HOURLY")));
    for (MdmDistributionPO row : rows == null ? List.<MdmDistributionPO>of() : rows) {
      if (gateway.snapshot(name(row.getId())).isPresent()) {
        continue;
      }
      try {
        gateway.save(definition(row));
      } catch (RuntimeException failure) {
        log.warn("分发闹钟登记失败 distribution={}", row.getId(), failure);
      }
    }
  }

  /** 只有「API 模式 + ACTIVE + DAILY/HOURLY」可调度;MANUAL 由人点,MESSAGE/FILE 通道未接入。 */
  static boolean schedulable(MdmDistributionPO config) {
    return config != null
        && MdmDistributionMode.API.name().equals(config.getDistributeMode())
        && MdmDistributionStatus.ACTIVE.name().equals(config.getStatus())
        && cron(config.getDistributeFreq()) != null;
  }

  static String cron(String frequency) {
    return switch (frequency == null ? "" : frequency.trim().toUpperCase(java.util.Locale.ROOT)) {
      case "DAILY" -> "0 0 2 * * ?";
      case "HOURLY" -> "0 0 * * * ?";
      default -> null;
    };
  }

  ScheduleDefinition definition(MdmDistributionPO config) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("projectId", config.getProjectId());
    payload.put("distributionId", config.getId());
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("projectId", String.valueOf(config.getProjectId()));
    metadata.put("distributionId", String.valueOf(config.getId()));
    return new ScheduleDefinition(
        gateway.key(name(config.getId())),
        "MDM分发-" + config.getTargetSystem() + "(config " + config.getId() + ")",
        ScheduleTrigger.cron(cron(config.getDistributeFreq()), ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        true,
        metadata);
  }

  private static String name(Long distributionId) {
    return String.valueOf(distributionId);
  }
}

package io.yak.ops.business.quality.alert;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEventSpec;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.repository.QualityAlertRepository;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Records alert evidence when an execution result requires notification. */
@Component
@ConditionalOnQualityEnabled
public class QualityAlertRecorder {
  private static final Logger LOGGER = LoggerFactory.getLogger(QualityAlertRecorder.class);
  private final QualityAlertRepository repository;
  private final NotificationRouter notificationRouter;

  @org.springframework.beans.factory.annotation.Autowired
  public QualityAlertRecorder(
      QualityAlertRepository repository,
      ObjectProvider<NotificationRouter> notificationRouters) {
    this.repository = repository;
    this.notificationRouter = notificationRouters.getIfAvailable();
  }

  /** Focused tests retain the lightweight constructor. */
  public QualityAlertRecorder(QualityAlertRepository repository) {
    this.repository = repository;
    this.notificationRouter = null;
  }

  public void recordIfNecessary(
      QualityExecutionPlan plan,
      CheckResult result,
      int passed,
      int failed,
      int errors) {
    if (result == CheckResult.PASSED
        || result == CheckResult.RUNNING
        || result == CheckResult.NOT_RUN) {
      return;
    }

    boolean recordConfiguredAlert = plan.notifyEnabled();
    boolean publishMessage = result == CheckResult.ERROR
        || (plan.notifyEnabled() && plan.notifyChannel() == NotifyChannel.MESSAGE);
    if (!recordConfiguredAlert && !publishMessage) return;

    String message = "质量监控“" + plan.monitor().name() + "”执行结果为 " + result
        + "，通过 " + passed + " 条，未通过 " + failed + " 条，异常 " + errors + " 条。";

    if (recordConfiguredAlert) {
      recordConfiguredAlert(plan, result, message);
    }
    if (publishMessage) {
      publishMessageNotification(plan, result, message);
    }
  }

  private void recordConfiguredAlert(
      QualityExecutionPlan plan,
      CheckResult result,
      String message) {
    try {
      String target = normalizeTarget(plan);
      String status = plan.notifyChannel() == NotifyChannel.MESSAGE ? "RECORDED" : "PENDING";
      repository.insertAlertEvent(new AlertEventSpec(
          plan.monitor().id(), plan.executionNo(), result, plan.alertLevel(), plan.notifyChannel(),
          target, status, message, null, LocalDateTime.now()));
      LOGGER.warn(
          "Quality alert recorded: monitor={}, execution={}, result={}, channel={}, target={}",
          plan.monitor().id(), plan.executionNo(), result, plan.notifyChannel(), target);
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to record quality alert for monitor {} and execution {}",
          plan.monitor().id(), plan.executionNo(), exception);
    }
  }

  private void publishMessageNotification(
      QualityExecutionPlan plan,
      CheckResult result,
      String message) {
    if (notificationRouter == null) return;
    try {
      boolean error = result == CheckResult.ERROR || plan.alertLevel() == AlertLevel.CRITICAL;
      String monitorName = StringUtils.hasText(plan.monitor().name())
          ? plan.monitor().name().trim()
          : "质量监控 #" + plan.monitor().id();
      String table = StringUtils.hasText(plan.monitor().tableName())
          ? plan.monitor().tableName().trim()
          : null;
      String summary = table == null ? monitorName : monitorName + " · " + table;
      String title = result == CheckResult.ERROR
          ? "数据质量执行异常"
          : error ? "数据质量检查发现严重问题" : "数据质量检查发现问题";

      notificationRouter.publish(
          new NotificationIntent(
              plan.projectId(),
              NotificationIntent.Type.QUALITY,
              error ? NotificationIntent.Level.ERROR : NotificationIntent.Level.WARNING,
              title,
              summary,
              message,
              "DATA_QUALITY_EXECUTION",
              plan.executionNo(),
              "/data-quality/execution/" + plan.executionNo()));
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to publish quality notification intent: monitor={}, execution={}",
          plan.monitor().id(),
          plan.executionNo(),
          exception);
    }
  }

  private String normalizeTarget(QualityExecutionPlan plan) {
    if (plan.notifyTarget() != null && !plan.notifyTarget().isBlank()) return plan.notifyTarget().trim();
    return plan.monitor().owner();
  }
}

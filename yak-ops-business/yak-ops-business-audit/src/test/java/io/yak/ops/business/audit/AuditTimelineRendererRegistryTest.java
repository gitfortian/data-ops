package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditTimelineRendererRegistryTest {

  private final AuditTimelineRendererRegistry registry = new AuditTimelineRendererRegistry();

  @Test
  void authorizationDecisionUsesBusinessCopyAndStableReasonLabel() {
    AuditEventPresentation presentation =
        registry.render(
            "AUTHORIZATION_DECISION",
            "FAILURE",
            "PROJECT_MEMBERSHIP_REQUIRED",
            "Authorization denied",
            Map.of("permission", "PROJECT_ACCESS", "decision", "DENY"));

    assertThat(presentation.title()).isEqualTo("权限检查拒绝");
    assertThat(presentation.description()).contains("项目空间访问").contains("当前用户不是项目成员");
  }

  @Test
  void knownBusinessEventUsesRegistryWithoutLeakingTechnicalTypeAsTitle() {
    AuditEventPresentation presentation =
        registry.render("TASK_QUEUED", "INFO", null, "Queued", Map.of());

    assertThat(presentation.title()).isEqualTo("任务已进入队列");
    assertThat(presentation.description()).isEqualTo("Queued");
  }

  @Test
  void resourceUpdateUsesStableBusinessChangeTypeWhenAvailable() {
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow version published",
                    Map.of("changeType", "VERSION_PUBLISHED"))
                .title())
        .isEqualTo("版本已发布");
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow enabled",
                    Map.of("changeType", "RESOURCE_ENABLED"))
                .title())
        .isEqualTo("资源已启用");
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow disabled",
                    Map.of("changeType", "RESOURCE_DISABLED"))
                .title())
        .isEqualTo("资源已停用");
  }

  @Test
  void workflowExecutionStateChangesUseBusinessCopyWithoutGrowingEventVocabulary() {
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "INFO",
                null,
                "Workflow execution started",
                Map.of("changeType", "EXECUTION_STARTED"))
            .title())
        .isEqualTo("执行开始");
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "SUCCESS",
                null,
                "Workflow execution succeeded",
                Map.of("changeType", "EXECUTION_SUCCEEDED"))
            .title())
        .isEqualTo("执行成功");
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "FAILURE",
                "WORKFLOW_EXECUTION_FAILED",
                "Workflow execution failed",
                Map.of("changeType", "EXECUTION_FAILED"))
            .title())
        .isEqualTo("执行失败");
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "FAILURE",
                "WORKFLOW_EXECUTION_CANCELED",
                "Workflow execution canceled",
                Map.of("changeType", "EXECUTION_CANCELED"))
            .title())
        .isEqualTo("执行已取消");
  }

  @Test
  void scheduleAndBackfillChangesUseBusinessCopy() {
    assertThat(registry
            .render(
                "RESOURCE_CREATED",
                "SUCCESS",
                null,
                "created",
                Map.of("changeType", "SCHEDULE_CREATED"))
            .title())
        .isEqualTo("调度已创建");
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "SUCCESS",
                null,
                "enabled",
                Map.of("changeType", "SCHEDULE_ENABLED"))
            .title())
        .isEqualTo("调度已启用");
    assertThat(registry
            .render(
                "RESOURCE_CREATED",
                "SUCCESS",
                null,
                "created",
                Map.of("changeType", "BACKFILL_CREATED"))
            .title())
        .isEqualTo("Backfill 已创建");
    assertThat(registry
            .render(
                "RESOURCE_CREATED",
                "SUCCESS",
                null,
                "created",
                Map.of("changeType", "BUSINESS_DATE_RERUN_CREATED"))
            .title())
        .isEqualTo("运维补跑已创建");
    assertThat(registry
            .render(
                "RESOURCE_UPDATED",
                "SUCCESS",
                null,
                "canceled",
                Map.of("changeType", "BACKFILL_CANCELED"))
            .title())
        .isEqualTo("Backfill 已取消");
  }

  @Test
  void unknownEventHasSafeFallback() {
    AuditEventPresentation presentation =
        registry.render("CUSTOM_EVENT", "INFO", null, null, Map.of());

    assertThat(presentation.title()).isEqualTo("custom event");
  }
}

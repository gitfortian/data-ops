package io.yak.ops.business.mdm.notification;

import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 主数据事件站内信派发(R6):审批生效 / 合并完成 / 分发发布三处业务终点各发一条意图。
 *
 * <p>本组件只负责"发生了什么、说给人听"，收件人与通道交给
 * {@link MdmNotificationPolicyResolver}；路由器缺席或任何异常都不允许影响业务主流程
 * (核心路由器本身已把投递推迟到事务提交后)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmNotifier {

  public static final String SOURCE_CHANGE_APPLIED = "MDM_CHANGE_APPLIED";
  public static final String SOURCE_MERGE_COMPLETED = "MDM_MERGE_COMPLETED";
  public static final String SOURCE_DISTRIBUTION_PUBLISHED = "MDM_DISTRIBUTION_PUBLISHED";

  /** 本模块派发的来源类型；策略解析器据此接管。 */
  public static final Set<String> SOURCE_TYPES =
      Set.of(SOURCE_CHANGE_APPLIED, SOURCE_MERGE_COMPLETED, SOURCE_DISTRIBUTION_PUBLISHED);

  private final ObjectProvider<NotificationRouter> routers;
  private final ObjectProvider<CurrentProject> currentProjects;
  private final MdmEntityService entityService;

  /** 变更单审批通过并已落到主数据记录。 */
  public void changeApplied(Long entityId, MdmRecord applied, Long changeId, String approver) {
    if (entityId == null || applied == null) {
      return;
    }
    boolean deleted = applied.status() == MdmRecordStatus.DELETED;
    publish(
        entityId,
        SOURCE_CHANGE_APPLIED,
        NotificationIntent.Level.SUCCESS,
        deleted ? "主数据记录已删除生效" : "主数据变更已生效",
        entityLabel(entityId) + " · 记录 " + applied.masterId()
            + (deleted ? " · 已下线" : " · 版本 v" + applied.version()),
        "变更单 #" + changeId + " 已由 " + safe(approver, "审批人") + " 通过，"
            + (deleted ? "记录已转为已删除状态。" : "记录升至 v" + applied.version() + "。"));
  }

  /** 一次主数据合并完成(主记录吸收 N 条重复记录)。 */
  public void mergeCompleted(Long entityId, String masterId, int mergedCount) {
    if (entityId == null) {
      return;
    }
    publish(
        entityId,
        SOURCE_MERGE_COMPLETED,
        NotificationIntent.Level.INFO,
        "主数据合并完成",
        entityLabel(entityId) + " · 主记录 " + masterId + " · 合并 " + mergedCount + " 条",
        "本次合并已把 " + mergedCount + " 条重复记录归并到主记录 " + masterId + "，被合并记录转为已合并状态。");
  }

  /** 分发配置发布/重发布成功，外部调用方可经数据服务取数。 */
  public void distributionPublished(
      Long entityId, String targetSystem, int recordCount, String apiPath) {
    if (entityId == null) {
      return;
    }
    publish(
        entityId,
        SOURCE_DISTRIBUTION_PUBLISHED,
        NotificationIntent.Level.SUCCESS,
        "主数据分发已发布",
        entityLabel(entityId) + " → " + safe(targetSystem, "目标系统") + " · " + recordCount + " 条生效记录",
        "分发 API 已发布至数据服务：" + safe(apiPath, "-") + "，订阅方可持 API Key 实时取数。");
  }

  private void publish(
      Long entityId,
      String sourceType,
      NotificationIntent.Level level,
      String title,
      String summary,
      String content) {
    NotificationRouter router = routers.getIfAvailable();
    if (router == null) {
      return;
    }
    try {
      Optional<ProjectContext> project =
          Optional.ofNullable(currentProjects.getIfAvailable()).flatMap(CurrentProject::current);
      if (project.isEmpty()) {
        log.warn("缺少项目上下文,跳过主数据站内信: source={}, entity={}", sourceType, entityId);
        return;
      }
      router.publish(
          new NotificationIntent(
              project.get().projectId(),
              NotificationIntent.Type.SYSTEM,
              level,
              title,
              summary,
              content,
              sourceType,
              String.valueOf(entityId),
              "/mdm/modeling/" + entityId));
    } catch (RuntimeException failure) {
      log.warn("主数据站内信派发失败: source={}, entity={}", sourceType, entityId, failure);
    }
  }

  private String entityLabel(Long entityId) {
    try {
      MdmEntity entity = entityService.get(entityId);
      return "「" + entity.name() + "(" + entity.code() + ")」";
    } catch (RuntimeException failure) {
      return "实体 #" + entityId;
    }
  }

  private static String safe(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}

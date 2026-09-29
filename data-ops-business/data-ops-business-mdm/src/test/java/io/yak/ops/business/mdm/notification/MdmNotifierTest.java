package io.yak.ops.business.mdm.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 主数据站内信派发测试(R6):事件文案/深链正确,且任何通知侧故障(路由器缺席、无项目上下文、
 * 反查抛错)都不得影响业务主流程——发信失败不是审批或分发的失败。
 */
class MdmNotifierTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<NotificationRouter> routers = mock(ObjectProvider.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<CurrentProject> currentProjects = mock(ObjectProvider.class);

  private NotificationRouter router;
  private CurrentProject currentProject;
  private MdmEntityService entityService;
  private MdmNotifier notifier;

  @BeforeEach
  void setUp() {
    router = mock(NotificationRouter.class);
    currentProject = mock(CurrentProject.class);
    entityService = mock(MdmEntityService.class);
    when(routers.getIfAvailable()).thenReturn(router);
    when(currentProjects.getIfAvailable()).thenReturn(currentProject);
    when(currentProject.current()).thenReturn(Optional.of(new ProjectContext(1L, "默认空间")));
    when(entityService.get(7L)).thenReturn(
        new MdmEntity(7L, "customer", "客户", MdmEntityStatus.ACTIVE, null, null, "root",
            LocalDateTime.now(), LocalDateTime.now()));
    notifier = new MdmNotifier(routers, currentProjects, entityService);
  }

  private NotificationIntent captured() {
    ArgumentCaptor<NotificationIntent> captor = ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    return captor.getValue();
  }

  private static MdmRecord record(MdmRecordStatus status, int version) {
    return new MdmRecord(
        1L, 7L, "C001", "{\"name\":\"客户1\"}", "{}", status, version,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  void changeAppliedPublishesVersionCopyDeepLinkedToEntity() {
    notifier.changeApplied(7L, record(MdmRecordStatus.ACTIVE, 4), 88L, "root");

    NotificationIntent intent = captured();
    assertEquals(1L, intent.projectId());
    assertEquals(NotificationIntent.Type.SYSTEM, intent.type());
    assertEquals(MdmNotifier.SOURCE_CHANGE_APPLIED, intent.sourceType());
    assertEquals("7", intent.sourceId());
    assertEquals("/mdm/modeling/7", intent.actionPath());
    assertEquals("主数据变更已生效", intent.title());
    assertEquals("「客户(customer)」 · 记录 C001 · 版本 v4", intent.summary());
    assertEquals("变更单 #88 已由 root 通过，记录升至 v4。", intent.content());
  }

  @Test
  void deleteChangeWordingDoesNotClaimAVersionBump() {
    notifier.changeApplied(7L, record(MdmRecordStatus.DELETED, 5), 89L, "root");

    NotificationIntent intent = captured();
    assertEquals("主数据记录已删除生效", intent.title());
    assertEquals("「客户(customer)」 · 记录 C001 · 已下线", intent.summary());
  }

  @Test
  void mergeCompletedAndDistributionPublishedUseTheirOwnSourceTypes() {
    notifier.mergeCompleted(7L, "C001", 2);
    notifier.distributionPublished(7L, "CRM", 3060, "/mdm/customer/7");

    ArgumentCaptor<NotificationIntent> captor = ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router, times(2)).publish(captor.capture());
    NotificationIntent merge = captor.getAllValues().get(0);
    NotificationIntent distribution = captor.getAllValues().get(1);

    assertEquals(MdmNotifier.SOURCE_MERGE_COMPLETED, merge.sourceType());
    assertEquals("「客户(customer)」 · 主记录 C001 · 合并 2 条", merge.summary());
    assertEquals(MdmNotifier.SOURCE_DISTRIBUTION_PUBLISHED, distribution.sourceType());
    assertEquals("「客户(customer)」 → CRM · 3060 条生效记录", distribution.summary());
    assertEquals("分发 API 已发布至数据服务：/mdm/customer/7，订阅方可持 API Key 实时取数。",
        distribution.content());
  }

  /** 实体名反查失败只影响文案，不能连带整条通知丢掉。 */
  @Test
  void entityLookupFailureStillDeliversWithIdLabel() {
    when(entityService.get(7L)).thenThrow(new IllegalStateException("entity gone"));

    notifier.mergeCompleted(7L, "C001", 1);

    assertEquals("实体 #7 · 主记录 C001 · 合并 1 条", captured().summary());
  }

  @Test
  void absentRouterSkipsSilently() {
    when(routers.getIfAvailable()).thenReturn(null);

    assertDoesNotThrow(() -> notifier.mergeCompleted(7L, "C001", 1));
    verify(router, never()).publish(any());
  }

  /** 后台线程没有请求上下文:跳过而不是拿 0 号项目构造非法 intent 炸掉分发事务。 */
  @Test
  void missingProjectContextSkips() {
    when(currentProject.current()).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> notifier.changeApplied(7L, record(MdmRecordStatus.ACTIVE, 2), 1L, "root"));
    verify(router, never()).publish(any());
  }

  @Test
  void routerFailureNeverPropagatesToBusinessFlow() {
    doThrow(new RuntimeException("sink down"))
        .when(router).publish(any());

    assertDoesNotThrow(() -> notifier.distributionPublished(7L, "CRM", 10, "/mdm/customer/7"));
  }

  @Test
  void nullEntityIdIsNoOp() {
    notifier.changeApplied(null, record(MdmRecordStatus.ACTIVE, 1), 1L, "root");
    notifier.mergeCompleted(null, "C001", 1);
    notifier.distributionPublished(null, "CRM", 1, "/x");

    verify(router, never()).publish(any());
  }
}

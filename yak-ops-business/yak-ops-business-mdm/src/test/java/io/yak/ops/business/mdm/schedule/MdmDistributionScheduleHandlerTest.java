package io.yak.ops.business.mdm.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.ops.business.mdm.application.MdmDistributionService;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 定时分发 handler 单元测试(R5):到点只做「恢复项目上下文 + 复用手动执行链路」,
 * 行没了就自清闹钟,不可调度就忽略,真失败必须报 accepted=false(不伪装成功)。
 */
class MdmDistributionScheduleHandlerTest {

  private static final ScheduleKey KEY =
      new ScheduleKey("yak-ops-mdm", "10");

  private MdmDistributionMapper mapper;
  private MdmDistributionService distributionService;
  private MdmDistributionScheduleEngineBridge engine;
  private RecordingProjectScope projectScope;
  private MdmDistributionScheduleHandler handler;

  @BeforeEach
  void setUp() {
    mapper = mock(MdmDistributionMapper.class);
    distributionService = mock(MdmDistributionService.class);
    engine = mock(MdmDistributionScheduleEngineBridge.class);
    projectScope = new RecordingProjectScope();
    handler =
        new MdmDistributionScheduleHandler(mapper, distributionService, engine, projectScope);
  }

  private static ScheduleExecutionContext context() {
    return new ScheduleExecutionContext(
        "trigger-1",
        KEY,
        "quartz",
        "mdmDistributionScheduleHandler",
        Map.of("projectId", 7L, "distributionId", 10L),
        Instant.now(),
        Instant.now(),
        false,
        1);
  }

  private static MdmDistributionPO schedulable() {
    MdmDistributionPO po = new MdmDistributionPO();
    po.setId(10L);
    po.setProjectId(7L);
    po.setEntityId(1L);
    po.setTargetSystem("CRM");
    po.setDistributeMode(MdmDistributionMode.API.name());
    po.setDistributeFreq("DAILY");
    po.setStatus(MdmDistributionStatus.ACTIVE.name());
    return po;
  }

  @Test
  void fireRestoresProjectAndReusesManualExecuteChain() {
    when(mapper.selectById(10L)).thenReturn(schedulable());
    when(distributionService.execute(10L, "system"))
        .thenReturn(
            new MdmDistributionService.DistributionResult(
                10L, "API", 3060, 0, LocalDateTime.now(), 66L, "/mdm/customer/10"));

    ScheduleExecutionResult result = handler.execute(context());

    assertTrue(result.accepted());
    assertEquals("10", result.businessExecutionId());
    assertTrue(result.message().contains("3060"));
    assertEquals(7L, projectScope.context.get().projectId());
    verify(distributionService).execute(10L, "system");
  }

  @Test
  void deletedConfigCleansUpItsOwnAlarm() {
    when(mapper.selectById(10L)).thenReturn(null);

    ScheduleExecutionResult result = handler.execute(context());

    assertTrue(result.accepted());
    verify(engine).deleteIfPresent(10L);
    verify(distributionService, never()).execute(anyLong(), any());
  }

  @Test
  void configThatStoppedBeingSchedulableIsIgnoredAndResynced() {
    MdmDistributionPO po = schedulable();
    po.setDistributeFreq("MANUAL");
    when(mapper.selectById(10L)).thenReturn(po);

    ScheduleExecutionResult result = handler.execute(context());

    assertTrue(result.accepted());
    verify(engine).sync(10L);
    verify(distributionService, never()).execute(anyLong(), any());
  }

  @Test
  void publishFailureReportsNotAcceptedInsteadOfFakeSuccess() {
    when(mapper.selectById(10L)).thenReturn(schedulable());
    when(distributionService.execute(10L, "system"))
        .thenThrow(new MdmException(MdmErrorCode.DATA_SERVICE_DISABLED, "数据服务模块未启用"));

    ScheduleExecutionResult result = handler.execute(context());

    assertFalse(result.accepted());
    assertTrue(result.message().contains("数据服务模块未启用"));
  }

  private static final class RecordingProjectScope implements ProjectContextScope {
    private final AtomicReference<ProjectContext> context = new AtomicReference<>();

    @Override
    public <T> T call(ProjectContext project, Supplier<T> action) {
      context.set(project);
      return action.get();
    }
  }
}

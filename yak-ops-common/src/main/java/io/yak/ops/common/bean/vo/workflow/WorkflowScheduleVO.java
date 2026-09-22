package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.util.Map;

/** 工作流调度定义展示对象。 */
public record WorkflowScheduleVO(
    String id,
    String workflowId,
    String name,
    String triggerType,
    String cronExpression,
    String timezone,
    Instant startTime,
    Instant endTime,
    String status,
    String executionStrategy,
    String misfireStrategy,
    Map<String, Object> input,
    Instant lastFireTime,
    Instant nextFireTime,
    Instant createTime,
    Instant updateTime) {

  public WorkflowScheduleVO {
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}

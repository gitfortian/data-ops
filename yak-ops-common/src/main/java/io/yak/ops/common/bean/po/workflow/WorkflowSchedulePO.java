package io.yak.ops.common.bean.po.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 工作流调度定义持久化对象。 */
@Data
@TableName("yak_workflow_schedule")
public class WorkflowSchedulePO {
  @TableId(type = IdType.INPUT)
  private String id;
  private Long projectId;
  private String workflowId;
  private String name;
  private String triggerType;
  private String cronExpression;
  private String timezone;
  private Instant startTime;
  private Instant endTime;
  private String status;
  private String executionStrategy;
  private String misfireStrategy;
  private String inputJson;
  private Instant lastFireTime;
  private Instant nextFireTime;
  private Instant createTime;
  private Instant updateTime;
}

package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_realtime_job_event")
public class RealtimeJobEventPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long definitionId;
  private Long deploymentId;
  private String eventType;
  private String fromState;
  private String toState;
  private String message;
  private String payloadJson;
  private LocalDateTime createTime;
}

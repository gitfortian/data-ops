package io.yak.ops.common.bean.po.sync.offline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 离线同步执行状态事件。 */
@Data
@TableName("yak_offline_execution_event")
public class OfflineExecutionEventPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long executionId;
  private Long stateVersion;
  private String fromStatus;
  private String toStatus;
  private String eventType;
  private String message;
  private String payloadJson;
  private LocalDateTime createTime;
}

package io.yak.ops.common.bean.vo.sync.offline;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 离线同步执行状态事件响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineExecutionEventVO {
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

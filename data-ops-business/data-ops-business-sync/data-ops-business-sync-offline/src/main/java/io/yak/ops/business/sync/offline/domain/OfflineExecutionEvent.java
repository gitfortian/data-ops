package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 离线同步执行状态事件领域模型。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineExecutionEvent {
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

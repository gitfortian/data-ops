package io.yak.ops.common.bean.vo.sync.offline;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Link-Up 固定执行引擎健康状态响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineEngineHealthVO {
  private String nodeId;
  private String nodeName;
  private String instanceId;
  private String version;
  private String status;
  private Long startedAtMillis;
  private Boolean offlineOnly;
  private Integer maxConcurrentJobs;
  private Integer maxQueuedJobs;
  private Integer runningJobs;
  private Integer queuedJobs;
  private Integer activeJobs;
  private JsonNode lifecycle;
}

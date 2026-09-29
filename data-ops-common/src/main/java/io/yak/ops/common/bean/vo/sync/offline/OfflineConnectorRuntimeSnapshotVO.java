package io.yak.ops.common.bean.vo.sync.offline;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前固定 Link-Up Worker 的 Connector 发现快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineConnectorRuntimeSnapshotVO {
  private Boolean reachable;
  private Boolean stale;
  private Long syncedAtMillis;
  private Long checkedAtMillis;
  private String errorMessage;
  private List<OfflineConnectorRoleRuntimeVO> connectors;
  private List<OfflineConnectorRuntimeProfileVO> profiles;
}

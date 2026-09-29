package io.yak.ops.common.bean.vo.sync.offline;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Link-Up Worker 中一个 Connector Role 的运行时发现结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineConnectorRoleRuntimeVO {
  private String connectorId;
  private String role;
  private Boolean available;
  private String schemaVersion;
  private String schemaFingerprint;
  private String implementationVersion;
  private List<String> capabilities;
}

package io.yak.ops.common.bean.vo.sync.offline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yak Ops 离线同步 Profile 与当前 Link-Up Worker 能力的合并视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineConnectorRuntimeProfileVO {
  private String profileId;
  private String dbType;
  private String displayName;
  private String pluginName;
  private Boolean defaultProfile;
  private OfflineConnectorRoleRuntimeVO source;
  private OfflineConnectorRoleRuntimeVO sink;
}

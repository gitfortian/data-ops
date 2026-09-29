package io.yak.ops.business.sync.offline.engine.connector;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Yak Ops 离线同步产品数据源与 Link-Up 执行 Connector 的稳定映射。
 *
 * <p>Profile 描述控制面默认选择，不代表 Worker 当前一定已加载对应 Connector；
 * Worker 运行时能力与可用性由后续 Connector Schema / runtime discovery 阶段负责。</p>
 *
 * @author weifuwan
 */
public record OfflineSyncConnectorProfile(
    String profileId,
    DataSourceDbType dbType,
    String sourceConnectorId,
    String sinkConnectorId,
    String pluginName,
    boolean defaultProfile) {

  public OfflineSyncConnectorProfile {
    if (!StringUtils.hasText(profileId)) {
      throw new IllegalArgumentException("profileId 不能为空");
    }
    dbType = Objects.requireNonNull(dbType, "dbType");
    if (!StringUtils.hasText(sourceConnectorId)) {
      throw new IllegalArgumentException("sourceConnectorId 不能为空");
    }
    if (!StringUtils.hasText(sinkConnectorId)) {
      throw new IllegalArgumentException("sinkConnectorId 不能为空");
    }
    if (!StringUtils.hasText(pluginName)) {
      throw new IllegalArgumentException("pluginName 不能为空");
    }

    profileId = profileId.trim();
    sourceConnectorId = sourceConnectorId.trim().toLowerCase(java.util.Locale.ROOT);
    sinkConnectorId = sinkConnectorId.trim().toLowerCase(java.util.Locale.ROOT);
    pluginName = pluginName.trim();
  }
}

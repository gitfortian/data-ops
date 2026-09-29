package io.yak.ops.business.dashboard.domain;

import java.time.Instant;

/** Dashboard 不可变版本元数据。 */
public record DashboardVersion(
    long id,
    long dashboardId,
    int versionNo,
    String name,
    String description,
    Long activeDatasetId,
    Instant createTime) {
}

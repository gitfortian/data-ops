package io.yak.ops.business.dashboard.domain;

import java.time.Instant;

/** Dashboard 稳定业务身份。 */
public record DashboardAsset(
    long id,
    String name,
    String description,
    Long currentVersionId,
    int currentVersionNo,
    Long publishedVersionId,
    int publishedVersionNo,
    Instant publishedTime,
    Instant createTime,
    Instant updateTime) {
}

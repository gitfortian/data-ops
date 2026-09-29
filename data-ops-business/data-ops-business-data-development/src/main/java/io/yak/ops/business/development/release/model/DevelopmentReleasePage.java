package io.yak.ops.business.development.release.model;

import java.util.List;

/** Paged release-center projection; it is not a domain truth owner. */
public record DevelopmentReleasePage(
    List<DevelopmentReleaseSummary> records,
    long total,
    int pageNo,
    int pageSize,
    long onlineCount,
    long offlineCount,
    long disabledCount) {}

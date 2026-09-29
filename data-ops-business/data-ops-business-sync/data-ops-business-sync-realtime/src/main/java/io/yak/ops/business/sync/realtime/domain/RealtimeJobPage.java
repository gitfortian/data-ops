package io.yak.ops.business.sync.realtime.domain;

import java.util.List;

public record RealtimeJobPage(
    List<RealtimeJobView> records, long total, int pageNo, int pageSize) {}

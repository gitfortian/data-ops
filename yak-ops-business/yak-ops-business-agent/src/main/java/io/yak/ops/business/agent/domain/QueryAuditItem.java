package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/** 查询审计条目（只读投影）：一条 = 一次 run_dataset_query 执行留痕。 */
public record QueryAuditItem(
    long id,
    String sessionId,
    long datasetId,
    String queryId,
    String status,
    String errorMessage,
    Integer returnedRows,
    Boolean truncated,
    Long elapsedMillis,
    LocalDateTime createTime) {}

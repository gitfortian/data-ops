package io.yak.ops.business.semantic.catalog;

import java.time.LocalDateTime;
import java.util.Map;

/** 标准的一个历史版本快照(修改前完整状态;payload 为全部字段的 JSON 视图)。 */
public record StandardVersion(
    Long standardId,
    int version,
    String operatedBy,
    LocalDateTime createTime,
    Map<String, Object> payload) {}

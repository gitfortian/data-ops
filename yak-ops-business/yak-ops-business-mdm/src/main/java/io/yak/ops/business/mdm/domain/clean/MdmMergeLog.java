package io.yak.ops.business.mdm.domain.clean;

import java.time.LocalDateTime;

/** 主数据合并日志(追加式);mergedRecordIds 为被合并记录 ID 列表(JSON 数组)。 */
public record MdmMergeLog(
    Long id,
    Long entityId,
    Long ruleId,
    Long masterRecordId,
    String mergedRecordIds,
    String result,
    String createdBy,
    LocalDateTime createTime) {}

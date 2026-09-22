package io.yak.ops.business.mdm.domain.record;

import java.time.LocalDateTime;

/**
 * 主数据记录版本快照(R4):每次变更生效后全量落一行,与 {@code yak_mdm_record.version} 对齐;
 * 首次变更同时补记变更前基线(changeId=null),使 v(n-1)/v(n) diff 始终可查。
 */
public record MdmRecordVersion(
    Long id,
    Long entityId,
    String masterId,
    int version,
    String attributes,
    MdmRecordStatus status,
    Long changeId,
    String operator,
    LocalDateTime createTime) {}

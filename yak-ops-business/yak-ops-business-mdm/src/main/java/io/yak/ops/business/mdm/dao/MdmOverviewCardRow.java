package io.yak.ops.business.mdm.dao;

/** 总览实体卡片行:一次 SQL 出 limit 行,相关子查询取代逐实体 count。 */
public record MdmOverviewCardRow(
    Long entityId,
    String entityCode,
    String entityName,
    long activeRecords,
    long distributionTargets,
    long subscribers,
    long pendingChanges) {}

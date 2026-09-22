package io.yak.ops.business.mdm.dao;

/** 总览六卡计数行(单条聚合 SQL 返回,项目内全量口径)。 */
public record MdmOverviewTotalsRow(
    long entities,
    long activeRecords,
    long pendingChanges,
    long cleanRules,
    long distributionTargets,
    long subscribers,
    long collectLinks,
    long mergeLogs,
    long failingDistributions) {}

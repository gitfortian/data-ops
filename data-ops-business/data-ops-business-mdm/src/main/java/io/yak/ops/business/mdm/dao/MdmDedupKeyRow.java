package io.yak.ops.business.mdm.dao;

/** 去重发现 DB 聚合结果行:匹配键 + 组内记录数(GROUP BY ... HAVING count >= 2)。 */
public record MdmDedupKeyRow(String matchKey, long matchCount) {}

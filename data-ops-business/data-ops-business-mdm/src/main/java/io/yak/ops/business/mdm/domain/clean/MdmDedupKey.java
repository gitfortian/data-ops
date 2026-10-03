package io.yak.ops.business.mdm.domain.clean;

/** 去重发现 DB 聚合结果行:匹配键 + 组内记录数(GROUP BY ... HAVING count >= 2)。 */
public record MdmDedupKey(String matchKey, long matchCount) {}

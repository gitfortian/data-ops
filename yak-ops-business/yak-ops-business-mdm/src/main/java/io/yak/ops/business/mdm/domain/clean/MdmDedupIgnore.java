package io.yak.ops.business.mdm.domain.clean;

import java.time.LocalDateTime;

/**
 * 主数据去重「忽略组」:管理员判定某个重复键「已知非重复」后登记,去重发现不再返回该组,
 * 可撤销。归属到具体规则——匹配键的含义随规则字段变化,跨规则共享会误屏蔽。
 */
public record MdmDedupIgnore(
    Long id,
    Long entityId,
    Long ruleId,
    String matchKey,
    String matchBasis,
    String reason,
    String createdBy,
    LocalDateTime createTime) {}

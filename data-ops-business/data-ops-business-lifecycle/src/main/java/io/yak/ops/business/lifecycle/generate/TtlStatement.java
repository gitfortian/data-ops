package io.yak.ops.business.lifecycle.generate;

import io.yak.ops.common.enums.lifecycle.LifecycleEnums.StorageType;

/**
 * 一条生成好的 TTL 语句。
 *
 * @param storageType 目标语句族
 * @param qualifiedTable 目标表(db.table 或 table)
 * @param statement 可复制/可下发的完整语句(UNSUPPORTED/永久-Paimon 时为说明性文本)
 * @param writable 是否允许平台下发;false=仅可复制手工执行
 * @param note 面向用户的补充说明
 */
public record TtlStatement(
    StorageType storageType,
    String qualifiedTable,
    String statement,
    boolean writable,
    String note) {}

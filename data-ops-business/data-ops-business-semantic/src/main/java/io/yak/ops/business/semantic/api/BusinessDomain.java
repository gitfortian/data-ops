package io.yak.ops.business.semantic.api;

import java.time.LocalDateTime;

/** 一个业务域节点(树形);编码为项目内稳定键,创建后不可改。 */
public record BusinessDomain(
    Long id,
    String code,
    String name,
    Long parentId,
    String owner,
    String description,
    int sortOrder,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final long ROOT_PARENT_ID = 0L;

  public BusinessDomain withPersisted(Long id, String operator, LocalDateTime time) {
    return new BusinessDomain(id, code, name, parentId, owner, description, sortOrder, operator,
        time, time);
  }
}

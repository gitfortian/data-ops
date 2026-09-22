package io.yak.ops.business.mdm.domain.entity;

import java.time.LocalDateTime;

/** 主数据实体;编码为项目内稳定键,创建后不可改。 */
public record MdmEntity(
    Long id,
    String code,
    String name,
    MdmEntityStatus status,
    String owner,
    String description,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public MdmEntity withPersisted(Long id, String operator, LocalDateTime time) {
    return new MdmEntity(id, code, name, status, owner, description, operator, time, time);
  }

  public MdmEntity withStatus(MdmEntityStatus newStatus) {
    return new MdmEntity(
        id, code, name, newStatus, owner, description, createdBy, createTime, updateTime);
  }

  public MdmEntity withEditable(String name, String owner, String description) {
    return new MdmEntity(
        id, code, name, status, owner, description, createdBy, createTime, updateTime);
  }
}

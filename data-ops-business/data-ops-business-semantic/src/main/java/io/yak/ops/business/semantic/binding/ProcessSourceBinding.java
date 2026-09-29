package io.yak.ops.business.semantic.binding;

import java.time.LocalDateTime;

/** 一条业务过程-源表绑定;datasource 为 datasource 模块松散 ID。 */
public record ProcessSourceBinding(
    Long id,
    Long processId,
    Long datasourceId,
    String sourceTable,
    String tableRole,
    String joinCondition,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static boolean isValidTableRole(String role) {
    return "MAIN".equals(role) || "DETAIL".equals(role) || "DIM".equals(role);
  }
}

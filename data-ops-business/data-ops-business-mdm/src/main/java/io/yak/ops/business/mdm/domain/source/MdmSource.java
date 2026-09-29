package io.yak.ops.business.mdm.domain.source;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 主数据来源绑定(数据源/实体为松散 ID);采集配置列随 54 追加。
 * fieldMapping = 属性编码 → 源列名,NULL/空 = 约定回退"属性编码与源列同名"。
 */
public record MdmSource(
    Long id,
    Long entityId,
    Long datasourceId,
    String database,
    String schema,
    String table,
    Map<String, String> fieldMapping,
    MdmSourceRole role,
    String status,
    int sortOrder,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final String STATUS_ENABLED = "ENABLED";

  public MdmSource withPersisted(Long id, String operator, LocalDateTime time) {
    return new MdmSource(
        id, entityId, datasourceId, database, schema, table, fieldMapping, role, status,
        sortOrder, operator, time, time);
  }
}

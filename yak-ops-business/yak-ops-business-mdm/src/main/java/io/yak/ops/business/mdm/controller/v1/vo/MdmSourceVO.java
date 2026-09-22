package io.yak.ops.business.mdm.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.Map;

/** 主数据来源视图对象(实体/数据源展示名由服务端经跨模块契约解析)。 */
public record MdmSourceVO(
    Long id,
    Long entityId,
    String entityCode,
    String entityName,
    Long datasourceId,
    String datasourceName,
    String database,
    String schema,
    String table,
    Map<String, String> fieldMapping,
    String role,
    String status,
    LocalDateTime createTime) {}

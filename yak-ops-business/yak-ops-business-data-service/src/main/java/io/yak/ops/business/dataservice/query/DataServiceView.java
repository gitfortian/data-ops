package io.yak.ops.business.dataservice.query;

import java.time.LocalDateTime;
import java.util.List;

/** Management/read projection preserving the existing Data Service JSON shape. */
public record DataServiceView(
    Long id,
    String name,
    String path,
    String runtimePath,
    Long dataSourceId,
    String sql,
    List<String> parameterNames,
    Integer maxRows,
    Integer timeoutSeconds,
    Boolean enabled,
    String authMode,
    String description,
    String sourceType,
    String sourceRef,
    Long sourceRevisionId,
    Integer sourceRevisionNo,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    Boolean paginationEnabled) {}

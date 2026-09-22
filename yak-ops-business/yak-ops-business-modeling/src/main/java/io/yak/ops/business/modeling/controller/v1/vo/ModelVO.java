package io.yak.ops.business.modeling.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 数仓建模模型视图对象。 */
public record ModelVO(
    Long id,
    String code,
    String name,
    String dialect,
    String description,
    String status,
    String owner,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    String layerCode,
    Long processId,
    String processName,
    String databaseName,
    Long directoryId,
    List<Long> tagIds,
    String deletedBy,
    LocalDateTime deletedTime,
    /** 来源绑定(08 逆向导入写入;供模型详情展示与 44 反查 ODS 模型)。 */
    Long sourceDatasourceId,
    String sourceDatabase,
    String sourceTable,
    /** 统计周期(51;DWS/ADS)。 */
    String statPeriod,
    /** 应用/报表绑定(52;ADS)。 */
    String appCode,
    String appName,
    /** 字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS。 */
    String importMode,
    /** 来源模型ID(import_mode=MODEL时记录,血缘追溯)。 */
    Long sourceModelId,
    /** 当前发布版本ID(未发布时为 null)。 */
    Long publishedVersionId,
    /** 最新版本号(0=未发布)。 */
    Integer latestVersionNo,
    /** 业务域(semantic 松散 ID,新建模型向导写入)。 */
    Long domainId,
    /** 业务域名称(服务端经 ProcessApi 解析)。 */
    String domainName,
    /** 最后更新人(V20;历史行为 null 时前端显示「-」)。 */
    String updatedBy) {}

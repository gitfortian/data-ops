package io.yak.ops.business.semantic.layer;

/** 平台级分层模板(无项目归属);初始化默认分层的复制源,携带库/命名/分区/存储/生命周期默认值。 */
public record LayerTemplate(
    Long id,
    String layerCode,
    String layerName,
    String description,
    String databaseName,
    String stdNamingCode,
    String defaultPartition,
    String storageFormat,
    Integer lifecycleDays,
    int sortOrder,
    boolean stdMandatory) {}

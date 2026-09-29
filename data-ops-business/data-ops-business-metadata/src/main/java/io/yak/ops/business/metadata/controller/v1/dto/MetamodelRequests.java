package io.yak.ops.business.metadata.controller.v1.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 元模型（类型 / 字段定义）请求 DTO 集合（ticket 129）。 */
public final class MetamodelRequests {

  private MetamodelRequests() {}

  /**
   * 类型定义。这里只给**形状**约束（长度/必填），取值与语义自洽性一律由
   * {@code MetamodelValidationService} 判——因为它要看库里的其它行（key_prefix 全局唯一、
   * field_type 是否解析得到、lineage_asset_type 是否是 lineage 的常量名）。
   */
  @Data
  public static class TypeDefDTO {
    @NotBlank(message = "typeName 必填")
    @Size(max = 64, message = "typeName 不能超过 64 个字符")
    private String typeName;

    @NotBlank(message = "category 必填（ENTITY|FIELD）")
    @Size(max = 16)
    private String category;

    @Size(max = 64)
    private String nameSpace;

    @NotBlank(message = "displayName 必填")
    @Size(max = 128)
    private String displayName;

    @Size(max = 256)
    private String parentTypes;

    @Size(max = 256)
    private String fqnPattern;

    @Size(max = 8)
    private String keySeparator;

    @Size(max = 128)
    private String providerBean;

    @Size(max = 32)
    private String lineageAssetType;

    @Size(max = 64)
    private String keyPrefix;

    private Boolean collectible;

    @DecimalMin(value = "0.0", inclusive = false, message = "searchDefaultWeight 必须为正")
    private Float searchDefaultWeight;

    private Boolean searchIncludeByDefault;

    @Size(max = 256)
    private String iconUrl;

    @Size(max = 24)
    private String color;

    @Size(max = 16)
    private String status;

    @Size(max = 512)
    private String description;
  }

  /** 某实体类型的一个扩展字段定义。{@code storageSlot} 空 = 只展示、不可筛（plan §4.5）。 */
  @Data
  public static class FieldDefDTO {
    @NotBlank(message = "fieldName 必填")
    @Size(max = 64, message = "fieldName 不能超过 64 个字符")
    private String fieldName;

    @NotBlank(message = "fieldType 必填")
    @Size(max = 64)
    private String fieldType;

    @NotBlank(message = "displayName 必填")
    @Size(max = 128)
    private String displayName;

    @Size(max = 512)
    private String description;

    private Boolean required;

    private Boolean isNull;

    @NotBlank(message = "baseType 必填")
    @Size(max = 24)
    private String baseType;

    @Size(max = 64)
    private String entityTypeRef;

    private String constraintDef;

    @Size(max = 256)
    private String defaultValue;

    private Boolean searchable;

    @Size(max = 16)
    private String matchType;

    private Float boost;

    private Boolean facetable;

    @Size(max = 32)
    private String storageSlot;

    private Integer ordinal;

    private Boolean showInList;

    private Boolean deprecated;
  }
}

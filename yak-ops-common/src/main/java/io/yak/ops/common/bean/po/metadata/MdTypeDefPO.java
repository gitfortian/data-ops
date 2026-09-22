package io.yak.ops.common.bean.po.metadata;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 元模型：类型定义（实体类型 + 字段类型）。类型是行不是代码。 */
@Data
@TableName("yak_md_type_def")
public class MdTypeDefPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 实体类型名或字段类型名，全局唯一。 */
  private String typeName;
  /** ENTITY|FIELD。 */
  private String category;
  /** platform|custom。 */
  private String nameSpace;
  private String displayName;
  /** 多父以逗号分隔；物理层级用 refersTo 类型对。 */
  private String parentTypes;
  /** 展示用 FQN 生成式（占位符由 MetadataKeyCodec 渲染）。 */
  private String fqnPattern;
  /** 只用于展示用 FQN 的拼接；asset_key 的分隔符沿用既有 ":"。 */
  private String keySeparator;
  /** 属性 schema，JSON 文本。 */
  private String schemaDef;
  /** 对账副通道用的 EntityProvider bean 名；采集型为 NULL。 */
  private String providerBean;
  /** 映射到 LineageAssetType 常量名；NULL=待 ticket 134 给该枚举加值。 */
  private String lineageAssetType;
  /** asset_key 的固定前缀，用于校验 provider 交出的键。 */
  private String keyPrefix;
  /** 1=主动采集(物理)；0=源域写时登记 + 定时对账(投影)。 */
  private Boolean collectible;
  private Float searchDefaultWeight;
  private Boolean searchIncludeByDefault;
  private String iconUrl;
  private String color;
  /** ACTIVE|DEPRECATED：永不物理删。 */
  private String status;
  private Integer version;
  private String description;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

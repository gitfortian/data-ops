package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 元模型：某实体类型的扩展字段定义（含是否可搜、提槽到哪）。 */
@Data
@TableName("yak_md_field_def")
public class MdFieldDefPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属实体类型 yak_md_type_def.id。 */
  private Long typeId;
  /** 属性键，进 md_attributes 的 key。 */
  private String fieldName;
  /** 字段类型名，须解析到 category=FIELD 的 type_def。 */
  private String fieldType;
  private String displayName;
  private String description;
  private Boolean required;
  private Boolean isNull;
  /** STRING|INTEGER|NUMBER|BOOLEAN|DATE|DATETIME|ENTITY_REFERENCE|JSON|ARRAY。 */
  private String baseType;
  /** base_type=ENTITY_REFERENCE 时允许的类型名，逗号分隔。 */
  private String entityTypeRef;
  /** 枚举集/正则/min-max/format，JSON 文本。 */
  private String constraintDef;
  private String defaultValue;
  /** 1=参与 q 全文检索；默认 0。 */
  private Boolean searchable;
  /** text|exact|like|range。 */
  private String matchType;
  private Float boost;
  private Boolean facetable;
  /** 提槽到哪个生成列；NULL=只在 md_attributes 里不可查。 */
  private String storageSlot;
  private Integer ordinal;
  private Boolean showInList;
  private Boolean deprecated;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

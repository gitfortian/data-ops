package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Logical attribute; stdFieldId refers to the Semantic standard field owner. */
@Data
@TableName("yak_modeling_logical_attribute")
public class LogicalAttributePO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long entityId;
  private Long stdFieldId;
  private String code;
  private String name;
  private String logicalType;
  private String description;
  private Boolean primaryFlag;
  private Boolean nullable;
  private Integer sort;
}

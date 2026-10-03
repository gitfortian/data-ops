package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_modeling_logical_attribute_mapping")
public class LogicalAttributeMappingPO {

  private Long id;

  private Long logicalAttributeId;

  private Long physicalColumnId;

  private String mappingExpression;

  private String status;
}

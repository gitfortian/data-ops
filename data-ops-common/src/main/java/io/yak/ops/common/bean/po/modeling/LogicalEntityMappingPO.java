package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_modeling_logical_entity_mapping")
public class LogicalEntityMappingPO {

  private Long id;

  private Long logicalEntityId;

  private Long physicalTableId;

  private String mappingType;

  private String status;

  private String description;
}

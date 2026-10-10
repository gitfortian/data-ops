package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Relationship whose endpoints must both belong to the same scoped logical model. */
@Data
@TableName("yak_modeling_entity_relation")
public class LogicalRelationPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long sourceEntityId;
  private Long targetEntityId;
  private String relationType;
  private String cardinality;
  private String description;
}

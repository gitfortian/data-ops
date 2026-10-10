package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Logical business entity, always accessed through project-scoped logical model. */
@Data
@TableName("yak_modeling_logical_entity")
public class LogicalEntityPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long logicalModelId;
  private String code;
  private String name;
  private String businessName;
  private String description;
  private String owner;
  private String status;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

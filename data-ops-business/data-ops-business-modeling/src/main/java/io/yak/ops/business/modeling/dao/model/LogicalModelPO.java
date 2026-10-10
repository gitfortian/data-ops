package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Existing logical-model table; project-owned root of its child entities/versions. */
@Data
@TableName("yak_modeling_logical_model")
public class LogicalModelPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long projectId;
  private Long processId;
  private String code;
  private String name;
  private String description;
  private Long domainId;
  private String owner;
  private String status;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

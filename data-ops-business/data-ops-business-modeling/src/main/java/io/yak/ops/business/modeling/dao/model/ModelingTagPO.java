package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模模型标签持久化对象。 */
@Data
@TableName("yak_modeling_tag")
public class ModelingTagPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 标签名称,项目空间内唯一。 */
  private String name;

  /** 创建时间。 */
  private LocalDateTime createTime;
}

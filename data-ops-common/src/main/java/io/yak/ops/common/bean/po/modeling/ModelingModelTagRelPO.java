package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模模型-标签关联持久化对象。 */
@Data
@TableName("yak_modeling_model_tag_rel")
public class ModelingModelTagRelPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 模型 id。 */
  private Long modelId;

  /** 标签 id。 */
  private Long tagId;

  /** 创建时间。 */
  private LocalDateTime createTime;
}

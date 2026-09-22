package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模模型索引持久化对象。 */
@Data
@TableName("yak_modeling_model_index")
public class ModelingModelIndexPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 所属模型。 */
  private Long modelId;

  /** 索引名,模型内唯一(不区分大小写)。 */
  private String indexName;

  /** 是否唯一索引。 */
  private Boolean uniqueIndex;

  /** 索引类型(按方言,可空)。 */
  private String indexType;

  /** 索引列名 JSON 数组。 */
  private String columnNames;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

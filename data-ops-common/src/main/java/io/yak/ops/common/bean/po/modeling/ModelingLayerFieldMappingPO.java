package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 字段分层映射持久化对象(标准字段 x 层落地;44 自动写入 + 补录)。 */
@Data
@TableName("yak_modeling_layer_field_mapping")
public class ModelingLayerFieldMappingPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 所属模型。 */
  private Long modelId;

  /** 标准字段(semantic 松散 ID)。 */
  private Long processFieldId;

  /** 分层(semantic 松散 ID)。 */
  private Long layerId;

  /** 该层落地字段名。 */
  private String layerFieldName;

  /** 该层落地类型。 */
  private String layerDataType;

  /** 来源字段。 */
  private String sourceField;

  /** 转换表达式。 */
  private String transformExpr;

  /** 聚合层字段角色:DIMENSION 分组键 / MEASURE 度量(51)。 */
  private String fieldRole;

  /** 聚合函数(MEASURE:SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG,51)。 */
  private String aggregateFunc;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

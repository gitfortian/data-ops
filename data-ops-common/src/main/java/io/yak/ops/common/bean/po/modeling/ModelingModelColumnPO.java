package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模模型字段持久化对象。 */
@Data
@TableName("yak_modeling_model_column")
public class ModelingModelColumnPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 所属模型。 */
  private Long modelId;

  /** 字段名,模型内唯一(不区分大小写)。 */
  private String columnName;

  /** 数据类型。 */
  private String dataType;

  /** 长度或精度。 */
  private Integer length;

  /** 小数位数。 */
  private Integer scale;

  /** 是否可空。 */
  private Boolean nullable;

  /** 默认值。 */
  private String defaultValue;

  /** 字段注释。 */
  private String columnComment;

  /** 业务描述。 */
  private String businessDescription;

  /** 类型标准引用(semantic 松散 ID)。 */
  private Long stdTypeId;

  /** 命名标准引用(semantic 松散 ID)。 */
  private Long stdNamingId;

  /** 码集编码引用(CODE 类标准的 code_set_code,松散引用)。 */
  private String stdCodeSetCode;

  /** 单位标准引用(semantic 松散 ID)。 */
  private Long stdUnitId;

  /** 口径标准引用(semantic 松散 ID)。 */
  private Long stdCaliberId;

  /** 安全标准引用(semantic 松散 ID)。 */
  private Long stdSecurityId;

  /** 标准字段(semantic 松散 ID;38 导入匹配 / 44 派生继承写入,字段↔标准字段的权威落点)。 */
  private Long stdFieldId;

  /** 聚合层字段角色:DIMENSION 分组键 / MEASURE 度量(DWS/ADS)。 */
  private String fieldRole;

  /** 聚合函数(MEASURE:SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)。 */
  private String aggregateFunc;

  /** 口径/转换表达式(聚合层可校验、可生成加工 SQL)。 */
  private String transformExpr;

  /** 字段顺序,0 起。 */
  private Integer sortOrder;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

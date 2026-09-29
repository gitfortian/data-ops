package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模型字段来源映射持久化对象(每目标列至多一条)。 */
@Data
@TableName("yak_modeling_column_mapping")
public class ModelingColumnMappingPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 所属模型。 */
  private Long modelId;

  /** 模型目标字段名。 */
  private String targetColumn;

  /** 源数据源(datasource 松散引用)。 */
  private Long sourceDatasourceId;

  /** 源库。 */
  private String sourceDatabase;

  /** 源表。 */
  private String sourceTable;

  /** 源字段。 */
  private String sourceColumn;

  /** 转换表达式(语法校验后存储)。 */
  private String transformExpr;

  /** 标准字段引用(semantic 松散 ID,M4 预留)。 */
  private Long stdProcessFieldId;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

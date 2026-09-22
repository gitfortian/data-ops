package io.yak.ops.common.bean.po.modeling;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓建模物理模型持久化对象。 */
@Data
@TableName("yak_modeling_model")
public class ModelingModelPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 模型编码,项目空间内唯一。 */
  private String modelCode;

  /** 模型名称。 */
  private String modelName;

  /** 目标数据库方言。 */
  private String dialect;

  /** 业务过程(semantic 松散引用,44 派生写入)。 */
  private Long processId;

  /** 业务域(semantic 松散引用,新建模型向导写入)。 */
  private Long domainId;

  /** 目标分层编码(37 分层,44 派生写入)。 */
  private String layerCode;

  /** 来源数据源(datasource 松散引用,08 逆向导入写入;44 据此反查 ODS 模型)。 */
  private Long sourceDatasourceId;

  /** 来源库。 */
  private String sourceDatabase;

  /** 来源表名。 */
  private String sourceTable;
  
  /** 字段导入方式(血缘追溯):MANUAL/SOURCE_TABLE/MODEL/BUSINESS_PROCESS。 */
  private String importMode;
  
  /** 来源模型ID(import_mode=MODEL时记录,血缘追溯)。 */
  private Long sourceModelId;

  /** 统计周期(DWS/ADS 表级周期约定,51)。 */
  private String statPeriod;

  /** 应用/报表编码(ADS 松散引用,52)。 */
  private String appCode;

  /** 应用/报表名称(ADS 展示用,52)。 */
  private String appName;

  /** 模型描述。 */
  private String description;

  /** 模型状态:DRAFT/PUBLISHED/DISABLED。 */
  private String status;

  /** 当前发布版本ID(未发布时为 null)。 */
  private Long publishedVersionId;

  /** 最新版本号(0=未发布)。 */
  private Integer latestVersionNo;

  /** 所属目录,0 表示未分类。 */
  private Long directoryId;

  /** 物理表名,空则按 model_code 兜底。 */
  private String tableName;

  /** 表注释。 */
  private String tableComment;

  /** 主键列名 JSON 数组,空=无主键。 */
  private String pkColumns;

  /** 分区类型(按方言)。 */
  private String partitionType;

  /** 分区列名 JSON 数组。 */
  private String partitionColumns;

  /** 分区表达式。 */
  private String partitionExpr;

  /** 表属性 JSON 对象。 */
  private String tableProperties;

  /** 创建人。 */
  private String createdBy;

  /** 最后更新人(应用侧写入;V20)。 */
  private String updatedBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;

  /** 软删除标记:1=回收站。 */
  private Boolean deleted;

  /** 软删除前的原始编码,存活行为 NULL。 */
  private String originalCode;

  /** 执行软删除的用户。 */
  private String deletedBy;

  /** 软删除时间。 */
  private LocalDateTime deletedTime;
}

package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数仓分层配置持久化对象。 */
@Data
@TableName("yak_semantic_layer")
public class SemanticLayerPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 分层编码,项目内唯一,创建后不可改。 */
  private String layerCode;

  /** 分层名称。 */
  private String layerName;

  /** 该层对应库名。 */
  private String databaseName;

  /** 该层对应数据源(datasource 松散引用)。 */
  private Long datasourceId;

  /** 命名标准引用(NAMING 类标准 ID)。 */
  private Long stdNamingId;

  /** 默认分区表达式。 */
  private String defaultPartition;

  /** 存储格式。 */
  private String storageFormat;

  /** 生命周期(天),空=永久。 */
  private Integer lifecycleDays;

  /** 描述。 */
  private String description;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 是否强制字段落标(M2-5 定标闸门口径);缺省视为强制。 */
  private Boolean stdMandatory;

  /** 预置标识。 */
  private Boolean isPreset;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

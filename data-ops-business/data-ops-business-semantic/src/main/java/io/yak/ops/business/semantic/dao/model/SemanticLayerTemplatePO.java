package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 数仓分层默认模板持久化对象(平台级,无 project_id)。 */
@Data
@TableName("yak_semantic_layer_template")
public class SemanticLayerTemplatePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 分层编码。 */
  private String layerCode;

  /** 分层名称。 */
  private String layerName;

  /** 说明。 */
  private String description;

  /** 默认库名。 */
  private String databaseName;

  /** 命名标准编码(初始化时解析为项目内 NAMING 标准 ID)。 */
  private String stdNamingCode;

  /** 默认分区表达式。 */
  private String defaultPartition;

  /** 存储格式。 */
  private String storageFormat;

  /** 生命周期(天),空=永久。 */
  private Integer lifecycleDays;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 初始化默认值:是否强制字段落标(M2-5)。 */
  private Boolean stdMandatory;
}

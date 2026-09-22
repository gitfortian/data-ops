package io.yak.ops.business.semantic.controller.v1.vo;

import lombok.Data;

/** 数仓分层视图对象。 */
@Data
public class WarehouseLayerVO {

  /** 主键。 */
  private Long id;

  /** 分层编码。 */
  private String code;

  /** 分层名称。 */
  private String name;

  /** 库名。 */
  private String databaseName;

  /** 数据源引用。 */
  private Long datasourceId;

  /** 命名标准引用。 */
  private Long stdNamingId;

  /** 默认分区。 */
  private String defaultPartition;

  /** 存储格式。 */
  private String storageFormat;

  /** 生命周期(天)。 */
  private Integer lifecycleDays;

  /** 描述。 */
  private String description;

  /** 排序。 */
  private Integer sortOrder;

  /** 状态。 */
  private String status;

  /** 是否强制字段落标(M2-5 定标闸门口径)。 */
  private Boolean stdMandatory;

  /** 预置标识(默认分层)。 */
  private Boolean preset;

  /** 被引用模型数(modeling 侧 LayerUsageReader 提供,2026-09-16)。 */
  private Long modelCount;

  /** 该层模型字段总数(M2-5 定标观察期,LayerStdBindingReader;无统计时为空)。 */
  private Long stdColumnTotal;

  /** 该层已绑定标准字段数(M2-5 定标观察期)。 */
  private Long stdBoundColumns;
}

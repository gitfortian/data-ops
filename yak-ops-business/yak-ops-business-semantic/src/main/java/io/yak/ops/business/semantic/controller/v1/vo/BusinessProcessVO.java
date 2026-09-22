package io.yak.ops.business.semantic.controller.v1.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 业务过程视图对象。 */
@Data
public class BusinessProcessVO {

  /** 主键。 */
  private Long id;

  /** 业务过程编码。 */
  private String code;

  /** 业务过程名称。 */
  private String name;

  /** 所属业务域。 */
  private Long domainId;

  /** 粒度。 */
  private String grain;

  /** 类型:FACT/DIMENSION。 */
  private String bizType;

  /** 负责人。 */
  private String owner;

  /** 描述。 */
  private String description;

  /** 排序。 */
  private Integer sortOrder;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}

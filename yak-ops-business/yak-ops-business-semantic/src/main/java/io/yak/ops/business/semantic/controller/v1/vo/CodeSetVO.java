package io.yak.ops.business.semantic.controller.v1.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 码集聚合视图对象(列表展示用)。 */
@Data
public class CodeSetVO {

  /** 码集编码。 */
  private String codeSetCode;

  /** 码集名称。 */
  private String name;

  /** 码值数。 */
  private int valueCount;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 版本(组内 MAX)。 */
  private Integer version;

  /** 预置标识。 */
  private Boolean preset;

  /** 排序。 */
  private Integer sortOrder;

  /** 描述。 */
  private String description;

  /** 最新更新时间。 */
  private LocalDateTime updateTime;
}

package io.yak.ops.business.semantic.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/** 标准历史版本视图(修改前完整状态)。 */
@Data
public class StandardVersionVO {

  /** 所属标准。 */
  private Long standardId;

  /** 快照对应的版本号(修改前的版本)。 */
  private Integer version;

  /** 执行修改的用户。 */
  private String operatedBy;

  /** 快照时间。 */
  private LocalDateTime createTime;

  /** 修改前完整状态 JSON 视图。 */
  private Map<String, Object> payload;
}

package io.yak.ops.business.semantic.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 业务过程分页查询数据传输对象。 */
@Data
public class SemanticProcessQueryDTO {

  /** 当前页码。 */
  @Min(value = 1, message = "页码必须大于 0")
  private int pageNo = 1;

  /** 每页条数。 */
  @Min(value = 1, message = "每页条数必须大于 0")
  @Max(value = 200, message = "每页条数不能超过 200")
  private int pageSize = 10;

  /** 业务域过滤;null 表示全部。 */
  private Long domainId;

  /** 编码/名称统一搜索词。 */
  @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
  private String keyword;

  /** 类型过滤:FACT/DIMENSION;null 表示全部。 */
  @Size(max = 16, message = "类型不能超过 16 个字符")
  private String bizType;
}

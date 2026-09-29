package io.yak.ops.business.mdm.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 主数据实体分页查询数据传输对象。 */
@Data
public class MdmEntityQueryDTO {

  /** 当前页码。 */
  @Min(value = 1, message = "页码必须大于 0")
  private int pageNo = 1;

  /** 每页条数。 */
  @Min(value = 1, message = "每页条数必须大于 0")
  @Max(value = 200, message = "每页条数不能超过 200")
  private int pageSize = 10;

  /** 编码/名称统一搜索词。 */
  @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
  private String keyword;

  /** 状态过滤;null 表示全部。 */
  @Size(max = 16, message = "状态不能超过 16 个字符")
  private String status;
}

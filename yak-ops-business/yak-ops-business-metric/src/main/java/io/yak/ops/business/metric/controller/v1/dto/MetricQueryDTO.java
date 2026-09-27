package io.yak.ops.business.metric.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 指标分页查询 DTO。 */
@Data
public class MetricQueryDTO {

  @Min(value = 1, message = "页码必须大于 0")
  private int pageNo = 1;

  @Min(value = 1, message = "每页条数必须大于 0")
  @Max(value = 200, message = "每页条数不能超过 200")
  private int pageSize = 20;

  private Long domainId;

  private Long processId;

  @Size(max = 16, message = "指标类型不能超过 16 个字符")
  private String metricType;

  @Size(max = 16, message = "状态不能超过 16 个字符")
  private String status;

  @Size(max = 64, message = "负责人不能超过 64 个字符")
  private String owner;

  @Size(max = 20, message = "标签筛选最多 20 个")
  private java.util.List<Long> tagIds;

  @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
  private String keyword;
}

package io.yak.ops.business.modeling.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 数仓建模模型分页查询数据传输对象。 */
@Data
public class ModelingModelQueryDTO {

  /** 当前页码。 */
  @Min(value = 1, message = "页码必须大于 0")
  private int pageNo = 1;

  /** 每页条数。 */
  @Min(value = 1, message = "每页条数必须大于 0")
  @Max(value = 200, message = "每页条数不能超过 200")
  private int pageSize = 10;

  /** 模型名称/编码统一搜索词。 */
  @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
  private String keyword;

  /** 目录过滤；null 表示全部，0 表示未分类。 */
  private Long directoryId;

  /** 标签过滤（命中任一标签）。 */
  private java.util.List<Long> tagIds;

  /** 分层过滤(ODS/DWD/DWS/ADS,2026-09-17 工作台筛选)。 */
  @Size(max = 32, message = "分层编码不能超过 32 个字符")
  private String layerCode;

  /** 业务过程过滤(44 派生写入的 process_id,2026-09-17)。 */
  private Long processId;

  /** 业务域过滤:该域下全部业务过程(目录树按业务域视图,2026-09-17)。 */
  private java.util.List<Long> processIds;

  /** 业务域过滤(domain_id 直存,新建向导写入)。 */
  private Long domainId;

  /** 状态过滤(DRAFT;发布态随 13 落地)。 */
  @Size(max = 16, message = "状态不能超过 16 个字符")
  private String status;
}

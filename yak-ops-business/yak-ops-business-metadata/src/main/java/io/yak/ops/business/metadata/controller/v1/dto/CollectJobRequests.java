package io.yak.ops.business.metadata.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 采集/对账任务请求 DTO（ticket 116，页面见 ticket 122）。 */
public final class CollectJobRequests {

  private CollectJobRequests() {}

  /** 新建/修改共用同一形状：{@code PUT} 是整行替换，表单载入后整份提交。 */
  @Data
  public static class JobUpsertDTO {
    @Size(max = 64, message = "任务编码不能超过 64 个字符")
    private String jobCode;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过 128 个字符")
    private String jobName;

    /** HARVESTED=物理采集 / REGISTERED=投影对账；新建留空即 HARVESTED。 */
    @Size(max = 16)
    private String providerType;

    @Size(max = 64, message = "实体类型不能超过 64 个字符")
    private String typeName;

    private Long dataSourceId;

    @Size(max = 128, message = "库名不能超过 128 个字符")
    private String databaseName;

    @Size(max = 128, message = "schema 不能超过 128 个字符")
    private String schemaName;

    @Size(max = 255, message = "表名匹配式不能超过 255 个字符")
    private String tablePattern;

    private Boolean collectColumns;

    @Size(max = 64, message = "cron 不能超过 64 个字符")
    private String cronExpression;

    private Integer collapseThresholdPct;

    private Integer missingRounds;
  }

  @Data
  public static class JobQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    @Size(max = 16)
    private String providerType;

    private Boolean enabled;

    @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
    private String keyword;
  }

  @Data
  public static class EnabledDTO {
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
  }

  @Data
  public static class RunQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    /** 只看某个任务的历史；留空 = 本项目全部任务的运行历史。 */
    private Long jobId;
  }
}

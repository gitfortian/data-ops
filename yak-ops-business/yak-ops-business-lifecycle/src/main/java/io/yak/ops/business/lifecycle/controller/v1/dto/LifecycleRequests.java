package io.yak.ops.business.lifecycle.controller.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 生命周期模块分页/操作 DTO 集合。 */
public final class LifecycleRequests {

  private LifecycleRequests() {}

  @Data
  public static class PolicyQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    @Size(max = 24, message = "适用范围不能超过 24 个字符")
    private String scopeType;

    @Size(max = 32, message = "分层编码不能超过 32 个字符")
    private String layerCode;

    @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
    private String keyword;
  }

  @Data
  public static class PolicyUpsertDTO {
    @Size(max = 64, message = "策略编码不能超过 64 个字符")
    private String policyCode;

    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称不能超过 128 个字符")
    private String policyName;

    @NotBlank(message = "适用范围不能为空")
    private String scopeType;

    private String layerCode;

    private String partitionGranularity;

    private Integer hotDays;

    private Integer coldDays;

    private Integer destroyDays;

    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String remark;
  }

  @Data
  public static class StatusDTO {
    @NotBlank(message = "状态不能为空")
    private String status;
  }

  @Data
  public static class BindingDTO {
    @NotNull(message = "策略 ID 不能为空")
    private Long policyId;
  }

  @Data
  public static class ModelIdsDTO {
    @NotEmpty(message = "请至少选择一个模型")
    @Size(max = 200, message = "单次最多操作 200 个模型")
    private List<Long> modelIds;
  }

  @Data
  public static class DispatchDTO {
    @NotEmpty(message = "请至少选择一个模型")
    @Size(max = 200, message = "单次最多下发 200 个模型")
    private List<Long> modelIds;

    @NotBlank(message = "确认令牌不能为空,请先预览")
    private String confirmToken;
  }

  @Data
  public static class RecordQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    @Size(max = 16, message = "状态不能超过 16 个字符")
    private String status;

    private Long modelId;
  }

  @Data
  public static class MonitorModelQueryDTO {
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    private int pageSize = 20;

    @Size(max = 16, message = "状态不能超过 16 个字符")
    private String state;

    @Size(max = 32, message = "分层编码不能超过 32 个字符")
    private String layerCode;

    @Size(max = 128, message = "搜索关键词不能超过 128 个字符")
    private String keyword;
  }

  @Data
  public static class StorageSettingDTO {
    @NotBlank(message = "单价不能为空")
    @Size(max = 32, message = "单价不能超过 32 个字符")
    private String pricePerGbMonth;
  }
}

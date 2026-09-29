package io.yak.ops.business.mdm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request contracts for the master data distribution (ticket 58). */
public final class MdmDistributionApi {

  private MdmDistributionApi() {}

  /** 新建分发配置:实体 + 目标系统 + 方式 + 频率 + 范围。 */
  public record DistributionSaveRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotBlank(message = "目标系统编码不能为空")
          @Size(max = 64, message = "目标系统编码不能超过 64 个字符")
          String targetSystem,
      @Size(max = 128, message = "目标系统名称不能超过 128 个字符") String targetName,
      @NotBlank(message = "分发方式不能为空") String distributeMode,
      String distributeFreq,
      String distributeScope) {}

  /** 更新分发配置:名称 + 方式 + 频率 + 范围(目标系统不可改)。 */
  public record DistributionUpdateRequest(
      @Size(max = 128, message = "目标系统名称不能超过 128 个字符") String targetName,
      String distributeMode,
      String distributeFreq,
      String distributeScope) {}

  /** 状态切换:生效/停用。 */
  public record StatusToggleRequest(
      @NotBlank(message = "状态不能为空") String status) {}
}

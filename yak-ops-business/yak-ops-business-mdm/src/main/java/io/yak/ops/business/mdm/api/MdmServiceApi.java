package io.yak.ops.business.mdm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request contracts for the master data service: query API + subscription (ticket 59). */
public final class MdmServiceApi {

  private MdmServiceApi() {}

  /** 条件搜索请求。 */
  public record SearchRequest(
      String keyword,
      Integer pageNo,
      Integer pageSize) {}

  /** 新建订阅请求。 */
  public record SubscriptionSaveRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotBlank(message = "订阅方编码不能为空")
          @Size(max = 64, message = "订阅方编码不能超过 64 个字符")
          String subscriberCode,
      @Size(max = 128, message = "订阅方名称不能超过 128 个字符") String subscriberName,
      String notifyMode) {}

  /** 更新订阅请求。 */
  public record SubscriptionUpdateRequest(
      @Size(max = 128, message = "订阅方名称不能超过 128 个字符") String subscriberName,
      String notifyMode) {}

  /** 订阅状态切换。 */
  public record SubscriptionStatusRequest(
      @NotBlank(message = "状态不能为空") String status) {}
}

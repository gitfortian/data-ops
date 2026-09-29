package io.yak.ops.business.mdm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request contracts for the master data approval (ticket 60; R4: approval decisions move to the approval center). */
public final class MdmApprovalApi {

  private MdmApprovalApi() {}

  /** 变更申请请求。 */
  public record SubmitRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotBlank(message = "master_id 不能为空") String masterId,
      @NotBlank(message = "变更类型不能为空") String changeType,
      @NotBlank(message = "变更内容不能为空") String changeContent,
      Integer approvalLevel) {}
}

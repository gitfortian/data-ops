package io.yak.ops.business.approval.controller.v1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;

/** 审批模块请求 DTO。 */
public final class ApprovalRequests {

  private ApprovalRequests() {}

  @Data
  public static class FlowStepDTO {
    /** 同级审批人,任一人可决;1~10 人,服务端 trim + 同级去重。 */
    @NotEmpty(message = "每级审批人至少 1 人")
    private List<String> approvers;
  }

  @Data
  public static class FlowCreateDTO {
    @NotBlank(message = "流程编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "流程编码须为大写字母开头的大写字母/数字/下划线组合")
    private String flowCode;
    @NotBlank(message = "流程名称不能为空")
    private String flowName;
    private String description;
    @NotEmpty(message = "审批级数至少 1 级")
    @Valid
    private List<FlowStepDTO> steps;
  }

  /** flowCode 不可变,编辑只改名称/描述/审批人配置。 */
  @Data
  public static class FlowUpdateDTO {
    @NotBlank(message = "流程名称不能为空")
    private String flowName;
    private String description;
    @NotEmpty(message = "审批级数至少 1 级")
    @Valid
    private List<FlowStepDTO> steps;
  }

  @Data
  public static class PageQueryDTO {
    private int pageNo = 1;
    private int pageSize = 20;
  }

  @Data
  public static class MineQueryDTO {
    private int pageNo = 1;
    private int pageSize = 20;
    /** PENDING/APPROVED/REJECTED/CANCELED,空=全部。 */
    private String status;
  }

  @Data
  public static class ApproveDTO {
    private String comment;
  }

  @Data
  public static class RejectDTO {
    /** 必填校验留给服务层,保证错误码是 49006 而不是通用参数错。 */
    private String comment;
  }

  @Data
  public static class CancelDTO {
    private String reason;
  }
}

package io.yak.ops.business.mdm.api;

import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request contracts of the master data entity (ticket 51). */
public final class MdmEntityApi {

  private MdmEntityApi() {}

  /** 创建实体:编码创建后不可改;初始状态 DRAFT。 */
  public record CreateRequest(
      @NotBlank(message = "实体编码不能为空")
          @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "实体编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "实体名称不能为空") @Size(max = 128, message = "实体名称不能超过 128 个字符")
          String name,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description) {}

  /** 编辑实体:编码不可改。 */
  public record UpdateRequest(
      @NotBlank(message = "实体名称不能为空") @Size(max = 128, message = "实体名称不能超过 128 个字符")
          String name,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description) {}

  /** 状态流转:DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用。 */
  public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {

    public MdmEntityStatus toStatus() {
      return MdmEntityStatus.valueOf(status);
    }
  }
}

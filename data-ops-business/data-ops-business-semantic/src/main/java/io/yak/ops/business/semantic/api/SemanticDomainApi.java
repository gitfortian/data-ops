package io.yak.ops.business.semantic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request contracts of the business-domain tree (ticket 33). */
public final class SemanticDomainApi {

  private SemanticDomainApi() {}

  /** 创建业务域:parentId 为空=根。 */
  public record CreateRequest(
      Long parentId,
      @NotBlank(message = "业务域编码不能为空")
          @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "业务域编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "业务域名称不能为空") @Size(max = 128, message = "业务域名称不能超过 128 个字符")
          String name,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder) {}

  /** 编辑业务域:编码不可改;父级通过拖拽接口调整。 */
  public record UpdateRequest(
      @NotBlank(message = "业务域名称不能为空") @Size(max = 128, message = "业务域名称不能超过 128 个字符")
          String name,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder) {}

  /** 拖拽改父/排序:parentId 为空=移到根。 */
  public record MoveRequest(Long parentId, Integer sortOrder) {}
}

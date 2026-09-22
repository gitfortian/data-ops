package io.yak.ops.business.semantic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request contracts of business-process management (ticket 34). */
public final class SemanticProcessApi {

  private SemanticProcessApi() {}

  /** 创建业务过程。 */
  public record CreateRequest(
      @NotBlank(message = "业务过程编码不能为空")
          @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "业务过程编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "业务过程名称不能为空") @Size(max = 128, message = "业务过程名称不能超过 128 个字符")
          String name,
      @jakarta.validation.constraints.NotNull(message = "所属业务域不能为空") Long domainId,
      @Size(max = 64, message = "粒度不能超过 64 个字符") String grain,
      @NotBlank(message = "类型不能为空") @Pattern(regexp = "^(FACT|DIMENSION)$", message = "类型必须为 FACT 或 DIMENSION")
          String bizType,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder) {}

  /** 编辑业务过程:编码不可改。 */
  public record UpdateRequest(
      @NotBlank(message = "业务过程名称不能为空") @Size(max = 128, message = "业务过程名称不能超过 128 个字符")
          String name,
      @jakarta.validation.constraints.NotNull(message = "所属业务域不能为空") Long domainId,
      @Size(max = 64, message = "粒度不能超过 64 个字符") String grain,
      @NotBlank(message = "类型不能为空") @Pattern(regexp = "^(FACT|DIMENSION)$", message = "类型必须为 FACT 或 DIMENSION")
          String bizType,
      @Size(max = 64, message = "负责人不能超过 64 个字符") String owner,
      @Size(max = 512, message = "描述不能超过 512 个字符") String description,
      Integer sortOrder) {}
}

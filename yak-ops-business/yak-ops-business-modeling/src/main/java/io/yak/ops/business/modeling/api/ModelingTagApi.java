package io.yak.ops.business.modeling.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request contracts for modeling tag management. */
public final class ModelingTagApi {

  private ModelingTagApi() {}

  public record CreateRequest(
      @NotBlank(message = "标签名称不能为空") @Size(max = 128, message = "标签名称不能超过 128 个字符")
          String name) {}
}

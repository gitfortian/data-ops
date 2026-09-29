package io.yak.ops.business.modeling.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request contracts for modeling directory management. */
public final class ModelingDirectoryApi {

  private ModelingDirectoryApi() {}

  /** parentId 为空或 <=0 表示创建根目录。 */
  public record CreateRequest(Long parentId, @NotBlank(message = "目录名称不能为空")
      @Size(max = 128, message = "目录名称不能超过 128 个字符") String name) {}

  public record RenameRequest(@NotBlank(message = "目录名称不能为空")
      @Size(max = 128, message = "目录名称不能超过 128 个字符") String name) {}

  /** parentId 为空或 <=0 表示移动到根。 */
  public record MoveRequest(Long parentId) {}
}

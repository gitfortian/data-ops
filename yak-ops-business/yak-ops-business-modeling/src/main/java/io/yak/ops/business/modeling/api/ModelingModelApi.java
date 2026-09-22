package io.yak.ops.business.modeling.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request contracts for the modeling catalog API. */
public final class ModelingModelApi {

  private ModelingModelApi() {}

  public record CreateRequest(
      @NotBlank(message = "模型名称不能为空") @Size(max = 128, message = "模型名称不能超过 128 个字符")
          String name,
      @NotBlank(message = "模型编码不能为空")
          @Pattern(
              regexp = "^[A-Za-z0-9_][A-Za-z0-9_-]{0,127}$",
              message = "模型编码仅允许字母、数字、下划线和中划线，且以字母、数字或下划线开头")
          String code,
      @NotBlank(message = "目标方言不能为空") String dialect,
      @Size(max = 512, message = "模型描述不能超过 512 个字符") String description,
      Long directoryId,
      String layerCode,
      Long processId,
      /** 业务域(semantic 松散 ID,新建模型向导写入)。 */
      Long domainId,
      /** 来源数据源 ID(逆向导入写入,用于血缘)。 */
      Long sourceDatasourceId,
      /** 来源库名。 */
      String sourceDatabase,
      /** 来源表名。 */
      String sourceTable) {}

  /** directoryId 为空或 <=0 表示移出目录（未分类）。 */
  public record AssignDirectoryRequest(Long directoryId) {}

  /** 全量替换模型的标签集合；空集合表示清空标签。 */
  public record AssignTagsRequest(@NotNull List<Long> tagIds) {}

  /**
   * 基础信息更新（编码不可编辑）。除编码外均可修改；null 语义为"不修改"，
   * 前端编辑弹窗回传完整当前值。改名/归属另有专用端点（directory/tags），互不影响。
   */
  public record UpdateRequest(
      @NotBlank(message = "模型名称不能为空") @Size(max = 128, message = "模型名称不能超过 128 个字符")
          String name,
      @Size(max = 32, message = "目标方言不能超过 32 个字符") String dialect,
      @Size(max = 512, message = "模型描述不能超过 512 个字符") String description,
      @Size(max = 32, message = "分层编码不能超过 32 个字符") String layerCode,
      Long directoryId,
      Long domainId,
      Long processId,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceTable) {}

  /** 血缘追溯:字段导入方式和来源模型。 */
  public record AssignImportLineageRequest(
      @NotBlank(message = "导入方式不能为空") String importMode,
      Long sourceModelId) {}
}

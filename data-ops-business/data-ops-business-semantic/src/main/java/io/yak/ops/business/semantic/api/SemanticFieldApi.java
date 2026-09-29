package io.yak.ops.business.semantic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request contracts of the standard field library (ticket 35 + 评审补充). */
public final class SemanticFieldApi {

  private SemanticFieldApi() {}

  /** 编码规则:字母/数字/下划线,1~64 位;创建后不可改。 */
  public static final String CODE_PATTERN = "^[A-Za-z0-9_]{1,64}$";

  /** 创建标准字段:std_* 必须引用匹配类别的标准/码集(服务层校验)。 */
  public record CreateRequest(
      @NotBlank(message = "字段编码不能为空")
          @Pattern(regexp = CODE_PATTERN, message = "字段编码仅允许字母、数字和下划线,1~64 位")
          String code,
      @NotBlank(message = "字段名称不能为空") @Size(max = 128, message = "字段名称不能超过 128 个字符")
          String name,
      @NotBlank(message = "角色不能为空")
          @Pattern(regexp = "^(PROCESS|DIMENSION|METRIC)$", message = "角色必须为 PROCESS/DIMENSION/METRIC")
          String role,
      @Size(max = 64, message = "生效类型不能超过 64 个字符") String dataType,
      Long stdTypeId,
      Long stdUnitId,
      Long stdCaliberId,
      String stdCodeSetCode,
      Long stdSecurityId,
      @Size(max = 512, message = "业务描述不能超过 512 个字符") String businessDesc) {}

  /** 编辑标准字段:编码不可改;version 乐观锁(约束 3)。 */
  public record UpdateRequest(
      @NotNull(message = "字段 ID 不能为空") Long id,
      @NotNull(message = "版本号不能为空") Integer version,
      @NotBlank(message = "字段名称不能为空") @Size(max = 128, message = "字段名称不能超过 128 个字符")
          String name,
      @NotBlank(message = "角色不能为空")
          @Pattern(regexp = "^(PROCESS|DIMENSION|METRIC)$", message = "角色必须为 PROCESS/DIMENSION/METRIC")
          String role,
      @Size(max = 64, message = "生效类型不能超过 64 个字符") String dataType,
      Long stdTypeId,
      Long stdUnitId,
      Long stdCaliberId,
      String stdCodeSetCode,
      Long stdSecurityId,
      @Size(max = 512, message = "业务描述不能超过 512 个字符") String businessDesc) {}

  /** 启用/停用请求。 */
  public record StatusRequest(
      @NotBlank(message = "状态不能为空")
          @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态必须为 ENABLED 或 DISABLED")
          String status) {}

  /** 过程字段重排序请求。 */
  public record ReorderRequest(@NotNull(message = "字段顺序不能为空") List<Long> orderedFieldIds) {}

  /** 字段分页查询。 */
  public record FieldQuery(
      @jakarta.validation.constraints.Min(value = 1, message = "页码必须大于 0") int pageNo,
      @jakarta.validation.constraints.Min(value = 1, message = "每页条数必须大于 0")
          @jakarta.validation.constraints.Max(value = 200, message = "每页条数不能超过 200")
          int pageSize,
      @Size(max = 16, message = "角色不能超过 16 个字符") String role,
      @Size(max = 128, message = "搜索关键词不能超过 128 个字符") String keyword) {}
}

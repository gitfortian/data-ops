package io.yak.ops.business.modeling.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Request contracts for modeling table structure editing. */
public final class ModelingStructureApi {

  private ModelingStructureApi() {}

  /** Columns are saved with full-replace semantics; order in the list is the stored order. */
  public record SaveStructureRequest(
      @Size(max = 128, message = "物理表名不能超过 128 个字符") String tableName,
      @Size(max = 512, message = "表注释不能超过 512 个字符") String tableComment,
      List<@Valid ColumnInput> columns,
      List<@Pattern(
              regexp = "^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$",
              message = "主键列名不合法") String> primaryKey,
      List<@Valid IndexInput> indexes,
      PartitionInput partition,
      Map<String, String> tableProperties) {}

  public record ColumnInput(
      @NotBlank(message = "字段名不能为空")
          @Pattern(
              regexp = "^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$",
              message = "字段名仅允许字母、数字、下划线和 $，且以字母、数字或下划线开头")
          String columnName,
      @NotBlank(message = "字段类型不能为空") @Size(max = 64, message = "字段类型不能超过 64 个字符")
          String dataType,
      Integer length,
      Integer scale,
      Boolean nullable,
      @Size(max = 256, message = "默认值不能超过 256 个字符") String defaultValue,
      @Size(max = 512, message = "字段注释不能超过 512 个字符") String comment,
      @Size(max = 512, message = "业务描述不能超过 512 个字符") String businessDescription,
      Long stdTypeId,
      Long stdNamingId,
      String stdCodeSetCode,
      Long stdUnitId,
      Long stdCaliberId,
      Long stdSecurityId,
      Long stdFieldId,
      @Size(max = 16, message = "字段角色不能超过 16 个字符") String fieldRole,
      @Size(max = 16, message = "聚合函数不能超过 16 个字符") String aggregateFunc,
      @Size(max = 1024, message = "口径不能超过 1024 个字符") String transformExpr) {

    /** Compatibility view without the standard-field link (pre-44 call sites and tests). */
    public ColumnInput(
        String columnName,
        String dataType,
        Integer length,
        Integer scale,
        Boolean nullable,
        String defaultValue,
        String comment,
        String businessDescription,
        Long stdTypeId,
        Long stdNamingId,
        String stdCodeSetCode,
        Long stdUnitId,
        Long stdCaliberId,
        Long stdSecurityId) {
      this(columnName, dataType, length, scale, nullable, defaultValue, comment,
          businessDescription, stdTypeId, stdNamingId, stdCodeSetCode, stdUnitId, stdCaliberId,
          stdSecurityId, null, null, null, null);
    }

    /** Compatibility view of the pre-M4 arity (std references null). */
    public ColumnInput(
        String columnName,
        String dataType,
        Integer length,
        Integer scale,
        Boolean nullable,
        String defaultValue,
        String comment,
        String businessDescription) {
      this(columnName, dataType, length, scale, nullable, defaultValue, comment,
          businessDescription, null, null, null, null, null, null, null, null, null, null);
    }
  }

  /** indexName 保留名 PRIMARY 禁用;columns 必须存在于保存的字段列表。 */
  public record IndexInput(
      @NotBlank(message = "索引名不能为空") @Size(max = 128, message = "索引名不能超过 128 个字符")
          String indexName,
      Boolean uniqueIndex,
      @Size(max = 64, message = "索引类型不能超过 64 个字符") String indexType,
      List<@NotBlank(message = "索引列不能为空") String> columns) {}

  /** partitionType/columns/expression 按方言标注支持情况;columns 必须存在于字段列表。 */
  public record PartitionInput(
      @Size(max = 64, message = "分区类型不能超过 64 个字符") String type,
      List<@NotBlank(message = "分区列不能为空") String> columns,
      @Size(max = 512, message = "分区表达式不能超过 512 个字符") String expression) {}
}

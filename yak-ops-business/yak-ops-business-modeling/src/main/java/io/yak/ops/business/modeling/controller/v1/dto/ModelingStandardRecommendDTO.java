package io.yak.ops.business.modeling.controller.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 字段标准推荐请求数据传输对象(ticket 39)。 */
@Data
public class ModelingStandardRecommendDTO {

  /** 字段名。 */
  @NotBlank(message = "字段名不能为空")
  @Size(max = 128, message = "字段名不能超过 128 个字符")
  private String columnName;

  /** 字段类型。 */
  @Size(max = 64, message = "字段类型不能超过 64 个字符")
  private String dataType;

  /** 角色倾向:PROCESS/DIMENSION/METRIC;可空=UNKNOWN。 */
  @Pattern(regexp = "^(PROCESS|DIMENSION|METRIC)$", message = "角色必须为 PROCESS/DIMENSION/METRIC")
  private String role;
}

package io.yak.ops.business.modeling.controller.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 沉淀为标准请求数据传输对象(ticket 40)。 */
@Data
public class ModelingStandardCaptureDTO {

  /** 沉淀类别:TYPE/NAMING(首期常用两类;其余类别值同样接受,校验在 semantic)。 */
  @NotBlank(message = "类别不能为空")
  private String kind;

  /** 标准编码(建议用字段名)。 */
  @NotBlank(message = "编码不能为空")
  @Size(max = 64, message = "编码不能超过 64 个字符")
  private String code;

  /** 标准名称。 */
  @NotBlank(message = "名称不能为空")
  @Size(max = 128, message = "名称不能超过 128 个字符")
  private String name;

  /** NAMING:规则表达式。 */
  @Size(max = 1024, message = "规则表达式不能超过 1024 个字符")
  private String ruleExpr;

  /** TYPE:类型编码。 */
  @Size(max = 64, message = "类型编码不能超过 64 个字符")
  private String typeCode;

  /** TYPE:标准类型。 */
  @Size(max = 64, message = "标准类型不能超过 64 个字符")
  private String stdType;

  /** 来源字段名(审计上下文)。 */
  @Size(max = 128, message = "来源字段名不能超过 128 个字符")
  private String columnName;

  /** 业务描述。 */
  @Size(max = 512, message = "业务描述不能超过 512 个字符")
  private String businessDesc;
}

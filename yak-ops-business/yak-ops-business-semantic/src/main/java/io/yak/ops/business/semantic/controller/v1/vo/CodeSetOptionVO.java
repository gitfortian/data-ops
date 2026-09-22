package io.yak.ops.business.semantic.controller.v1.vo;

import lombok.Data;

/** 启用码集选项(标准字段码值引用下拉,value = code_set_code)。 */
@Data
public class CodeSetOptionVO {

  /** 码集编码。 */
  private String codeSetCode;

  /** 码集名称。 */
  private String name;
}

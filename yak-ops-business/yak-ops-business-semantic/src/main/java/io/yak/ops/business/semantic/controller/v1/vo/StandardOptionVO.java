package io.yak.ops.business.semantic.controller.v1.vo;

import lombok.Data;

/** 启用标准选项(标准字段引用下拉按需加载;码值类走专用码集选项端点)。 */
@Data
public class StandardOptionVO {

  /** 标准行 ID(引用列存 ID 的一类:类型/单位/口径/安全)。 */
  private Long id;

  /** 标准类别。 */
  private String kind;

  /** 标准编码。 */
  private String code;

  /** 标准名称。 */
  private String name;

  /** 类型标准:标准类型(生效类型提示回填用)。 */
  private String stdType;

  /** 类型标准:类型编码(单位推荐映射键,仅 TYPE 行)。 */
  private String typeCode;

  /** 单位标准:单位类型(金额/数量/时间…,单位推荐匹配键,仅 UNIT 行)。 */
  private String unitType;
}

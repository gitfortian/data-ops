package io.yak.ops.common.enums.metric;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 指标模块错误码(44001+ 段)。 */
@Getter
@RequiredArgsConstructor
public enum MetricErrorCode implements ErrorCode {

  NOT_FOUND(44001, "指标不存在"),
  DUPLICATE_CODE(44002, "指标编码已存在"),
  INVALID_CALIBER(44003, "口径引用不合法"),
  INVALID_MODEL(44004, "模型引用不合法"),
  INVALID_DOMAIN(44005, "业务域引用不合法"),
  VERSION_CONFLICT(44006, "指标变更冲突,请刷新后重试"),
  INVALID_COMPOSITION(44007, "复合指标子指标引用不合法"),
  CREATE_FAILED(44008, "创建指标失败"),
  UPDATE_FAILED(44009, "更新指标失败"),
  DELETE_FAILED(44010, "删除指标失败"),
  INVALID_TYPE(44011, "指标类型不合法"),
  INVALID_STATUS(44012, "指标状态不合法"),
  METRIC_REFERENCED(44013, "指标已被使用,无法执行该操作"),
  TAG_NOT_FOUND(44014, "标签不存在"),
  TAG_DUPLICATE(44015, "标签编码已存在"),
  TAG_REFERENCED(44016, "标签已被指标使用,无法删除"),
  UNIT_INVALID(44017, "单位引用不合法");

  private final Integer code;
  private final String message;
}

package io.yak.ops.business.semantic.api;

/**
 * Capture-to-standard SPI (ticket 40): turns a field context from any
 * consumer (modeling editor, reverse import review, agent) into a reusable
 * standard. Duplicate (kind, code) is idempotent: the existing standard is
 * returned with created=false. Implementations must not leak internal types.
 */
public interface StandardCaptureApi {

  CaptureResult capture(CaptureRequest request);

  /** 捕获请求:kind 必填;类别专有值按类别提供(校验与 30 一致)。 */
  record CaptureRequest(
      String kind,
      String code,
      String name,
      String ruleExpr,
      String typeCode,
      String stdType,
      String sourceMapping,
      String codeSetCode,
      String codeValue,
      String codeLabel,
      String unitCode,
      String unitType,
      String caliberCode,
      String calRule,
      String businessDesc,
      String levelCode,
      String maskRule,
      String sourceModelRef,
      String sourceColumn) {}

  /** 捕获结果:created=false 表示 (kind, code) 已存在(幂等返回既有标准)。 */
  record CaptureResult(Long standardId, String code, String name, boolean created, String message) {}
}

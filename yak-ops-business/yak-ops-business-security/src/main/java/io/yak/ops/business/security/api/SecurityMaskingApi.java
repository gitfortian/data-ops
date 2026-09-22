package io.yak.ops.business.security.api;

/** 脱敏 SPI:裁决应施加的算法,并提供纯函数式脱敏执行。 */
public interface SecurityMaskingApi {

  /** 按对象自然键解析脱敏指令(读分级 → 匹配脱敏策略);无策略返回 {@link MaskingDirective#none()}。 */
  MaskingDirective resolve(String objectKey);

  /**
   * 对单值施加脱敏。
   *
   * @param value 原值
   * @param algoCode 算法编码(MASK_PARTIAL/HASH/FULL_MASK/NULLIFY/REPLACE/KEEP_FORMAT)
   * @param algoParams 算法参数 JSON 文本(可空,取默认)
   */
  String mask(String value, String algoCode, String algoParams);
}

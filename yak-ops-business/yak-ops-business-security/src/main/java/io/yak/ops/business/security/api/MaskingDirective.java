package io.yak.ops.business.security.api;

/** 脱敏裁决结果:是否需要脱敏 + 算法编码 + 算法参数(JSON 文本)。 */
public record MaskingDirective(boolean mask, String algoCode, String algoParams) {

  public static MaskingDirective none() {
    return new MaskingDirective(false, null, null);
  }
}

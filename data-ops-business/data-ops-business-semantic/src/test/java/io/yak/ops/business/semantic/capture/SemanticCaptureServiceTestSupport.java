package io.yak.ops.business.semantic.capture;

import io.yak.ops.business.semantic.api.Standard;

/** 捕获测试共享的 KindFields 构造。 */
final class SemanticCaptureServiceTestSupport {

  static Standard.KindFields typeFields() {
    return new Standard.KindFields(
        null, null, null, null, "VARCHAR", "VARCHAR(128)", null, null, null, null, null, null,
        null, null, null, null, null);
  }

  private SemanticCaptureServiceTestSupport() {}
}

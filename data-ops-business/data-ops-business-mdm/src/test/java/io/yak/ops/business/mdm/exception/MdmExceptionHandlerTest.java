package io.yak.ops.business.mdm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.framework.common.Result;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import org.junit.jupiter.api.Test;

class MdmExceptionHandlerTest {

  private final MdmExceptionHandler handler = new MdmExceptionHandler();

  @Test
  void mapsBusinessCodeAndUserMessageIntoBody() {
    Result<Void> result =
        handler.handleMdmException(
            new MdmException(MdmErrorCode.NOTIFY_MODE_UNSUPPORTED, "WEBHOOK"));

    assertEquals(MdmErrorCode.NOTIFY_MODE_UNSUPPORTED.getCode(), result.getCode());
    assertEquals(
        MdmErrorCode.NOTIFY_MODE_UNSUPPORTED.getMessage() + "：WEBHOOK", result.getMessage());
  }

  @Test
  void fallsBackToMessageOnlyWhenCodeAbsent() {
    Result<Void> result = handler.handleMdmException(new MdmException(null, null));

    assertEquals(result.getMessage(), "主数据管理操作失败");
  }
}

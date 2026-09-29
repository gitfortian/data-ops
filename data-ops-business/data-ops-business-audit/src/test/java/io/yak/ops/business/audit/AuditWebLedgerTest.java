package io.yak.ops.business.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 合并守卫登记（票 02）：仅请求作用域内打标，线程复用不留脏值。 */
class AuditWebLedgerTest {

  @AfterEach
  void cleanUp() {
    AuditWebLedger.endRequest();
  }

  @Test
  void markingOnlyWorksInsideRequestScope() {
    assertFalse(AuditWebLedger.hasSdkOperation());
    AuditWebLedger.markSdkOperationOpened();
    assertFalse(AuditWebLedger.hasSdkOperation(), "非 HTTP 线程（调度/执行器）打标必须无效");

    AuditWebLedger.beginRequest();
    assertFalse(AuditWebLedger.hasSdkOperation());
    AuditWebLedger.markSdkOperationOpened();
    assertTrue(AuditWebLedger.hasSdkOperation());

    AuditWebLedger.endRequest();
    assertFalse(AuditWebLedger.hasSdkOperation());
  }

  @Test
  void beginRequestResiduesClearedByEndRequest() {
    AuditWebLedger.beginRequest();
    AuditWebLedger.markSdkOperationOpened();
    AuditWebLedger.endRequest();
    AuditWebLedger.beginRequest();
    assertFalse(AuditWebLedger.hasSdkOperation(), "新请求必须从干净状态开始");
  }
}

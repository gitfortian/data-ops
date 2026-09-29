package io.yak.ops.boot.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.boot.YakOpsApplication;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Guards that the governed Consumption module is part of the executable Boot assembly. */
class ConsumptionRuntimeAssemblyTest {

  @Test
  void bootAssemblyIncludesConsumptionControllerUnderApplicationScanRoot() {
    SpringBootApplication application = YakOpsApplication.class.getAnnotation(SpringBootApplication.class);

    assertTrue(application != null, "YakOpsApplication must remain a Spring Boot application");
    assertTrue(
        java.util.List.of(application.scanBasePackages()).contains("io.yak.ops"),
        "Boot component scanning must include the shared io.yak.ops module root");
    assertEquals(
        "io.yak.ops.business.consumption.product.discovery",
        ProductDiscoveryController.class.getPackageName());
  }
}

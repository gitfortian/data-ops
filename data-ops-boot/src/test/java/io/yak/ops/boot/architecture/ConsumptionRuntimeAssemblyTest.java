package io.yak.ops.boot.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.boot.YakOpsApplication;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Guards that the governed Consumption module is part of the executable Boot assembly. */
class ConsumptionRuntimeAssemblyTest {

  @Test
  void bootAssemblyIncludesConsumptionControllerUnderApplicationScanRoot() {
    SpringBootConfiguration application =
        YakOpsApplication.class.getAnnotation(SpringBootConfiguration.class);
    EnableAutoConfiguration autoConfiguration =
        YakOpsApplication.class.getAnnotation(EnableAutoConfiguration.class);
    ComponentScan componentScan = YakOpsApplication.class.getAnnotation(ComponentScan.class);

    assertTrue(application != null, "YakOpsApplication must remain a Spring Boot application");
    assertTrue(autoConfiguration != null, "YakOpsApplication must enable Spring Boot auto-configuration");
    assertTrue(componentScan != null, "YakOpsApplication must declare the shared module scan");
    assertTrue(
        java.util.List.of(componentScan.basePackages()).contains("io.yak.ops"),
        "Boot component scanning must include the shared io.yak.ops module root");
    assertEquals(
        "io.yak.ops.business.consumption.product.discovery",
        ProductDiscoveryController.class.getPackageName());
  }
}

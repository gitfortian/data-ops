package io.yak.ops.business.sync.offline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OfflineConnectorRuntimeContractTest {

  @Test
  void connectorDiscoveryEndpointsStayGlobalInfrastructureReads() throws Exception {
    Method inventory = OfflineJobExecutionController.class.getMethod("connectors");
    Method schema =
        OfflineJobExecutionController.class.getMethod(
            "connectorSchema", String.class, String.class);

    assertThat(inventory.getAnnotation(ProjectScope.class).value())
        .isEqualTo(ProjectMigrationMode.LEGACY_GLOBAL);
    assertThat(schema.getAnnotation(ProjectScope.class).value())
        .isEqualTo(ProjectMigrationMode.LEGACY_GLOBAL);
  }
}

package io.yak.ops.business.dataservice.controller.v1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;

import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimePolicyManager;
import org.junit.jupiter.api.Test;

class DataServiceRuntimeControllerTest {

  @Test
  void exactAuditApiDelegatesWithObservePermissionAndProjectRequired() throws Exception {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceRuntimeController controller = new DataServiceRuntimeController(
        mock(DataServiceRuntimePolicyManager.class), mock(DataServiceInvoker.class), reader);

    controller.invocationEvidence(7L, 9007199254740993L);
    verify(reader).findByApiAndId(7L, 9007199254740993L);

    var method = DataServiceRuntimeController.class.getMethod(
        "invocationEvidence", Long.class, Long.class);
    assertThat(method.getAnnotation(RequiresPermission.class).value())
        .isEqualTo(DataServicePermissionCode.OBSERVE);
    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/{id}/logs/{invocationId}");
    assertThat(DataServiceRuntimeController.class.getAnnotation(ProjectScope.class).value())
        .isEqualTo(ProjectMigrationMode.PROJECT_REQUIRED);
  }

  @Test
  void serviceLogsDelegateToBoundedServiceReader() {
    DataServiceCallLogReader reader = mock(DataServiceCallLogReader.class);
    DataServiceRuntimeController controller = new DataServiceRuntimeController(
        mock(DataServiceRuntimePolicyManager.class),
        mock(DataServiceInvoker.class),
        reader);

    controller.logsByApi(7L, 50);

    verify(reader).recentByApi(7L, 50);
  }
}

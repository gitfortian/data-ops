package io.yak.ops.business.dataservice.controller.v1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimePolicyManager;
import org.junit.jupiter.api.Test;

class DataServiceRuntimeControllerTest {

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

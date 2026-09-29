package io.yak.ops.business.dataservice.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.controller.v1.DataServiceController.PublishDataServiceRequest;
import io.yak.ops.business.dataservice.controller.v1.DataServiceController.UpdateDataServiceRequest;
import io.yak.ops.business.dataservice.documentation.DataServiceDocumentationManager;
import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataServiceControllerTest {
  @Test
  void updateContractContainsOnlyServiceFacingSettings() {
    List<String> components = Arrays.stream(UpdateDataServiceRequest.class.getRecordComponents())
        .map(component -> component.getName()).toList();
    assertThat(components).containsExactly("name", "path", "maxRows", "timeoutSeconds", "enabled", "description");
    assertThat(components).doesNotContain("sql", "dataSourceId");
  }

  @Test
  void updateDelegatesThroughPublicationBoundary() {
    DataServicePublisher publisher = mock(DataServicePublisher.class);
    DataServiceController controller = controller(mock(DataServicePublicationReader.class), publisher);
    when(publisher.updateSettings(eq(9L), any(PublicationSettings.class))).thenReturn(mock(DataServiceView.class));
    controller.update(9L, new UpdateDataServiceRequest("订单查询 API", "/orders", 500, 20, false, "只修改服务侧配置"));
    ArgumentCaptor<PublicationSettings> captor = ArgumentCaptor.forClass(PublicationSettings.class);
    verify(publisher).updateSettings(eq(9L), captor.capture());
    PublicationSettings input = captor.getValue();
    assertThat(input.name()).isEqualTo("订单查询 API");
    assertThat(input.path()).isEqualTo("/orders");
    assertThat(input.maxRows()).isEqualTo(500);
    assertThat(input.timeoutSeconds()).isEqualTo(20);
    assertThat(input.enabled()).isFalse();
    assertThat(input.description()).isEqualTo("只修改服务侧配置");
  }

  @Test
  void genericPublishRejectsSourceManagedAuthoringContexts() {
    DataServicePublicationReader publicationReader = mock(DataServicePublicationReader.class);
    when(publicationReader.managesServiceDefinition("DATA_DEVELOPMENT_DATA_SERVICE")).thenReturn(true);
    DataServiceController controller = controller(publicationReader, mock(DataServicePublisher.class));

    assertThrows(
        IllegalStateException.class,
        () -> controller.publish(new PublishDataServiceRequest(
            "DATA_DEVELOPMENT_DATA_SERVICE", "100", null, null, null, null, true, null)));
  }

  @Test
  void genericRuntimeMutationsRejectSourceManagedServices() {
    DataServicePublisher publisher = mock(DataServicePublisher.class);
    when(publisher.managesServiceDefinition(9L)).thenReturn(true);
    DataServiceController controller = controller(mock(DataServicePublicationReader.class), publisher);

    assertThrows(IllegalStateException.class, () -> controller.republish(9L, null));
    assertThrows(IllegalStateException.class, () -> controller.setEnabled(9L, false));
    assertThrows(IllegalStateException.class, () -> controller.delete(9L));
  }

  private DataServiceController controller(
      DataServicePublicationReader publicationReader,
      DataServicePublisher publisher) {
    return new DataServiceController(
        mock(DataServiceReader.class),
        mock(DataServiceViewFactory.class),
        publicationReader,
        publisher,
        mock(DataServiceManager.class),
        mock(DataServiceDocumentationManager.class));
  }
}

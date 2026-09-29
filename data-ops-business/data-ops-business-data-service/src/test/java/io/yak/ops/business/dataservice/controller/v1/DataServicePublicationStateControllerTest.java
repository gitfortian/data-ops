package io.yak.ops.business.dataservice.controller.v1;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.PublicationState;
import org.junit.jupiter.api.Test;

class DataServicePublicationStateControllerTest {
  @Test
  void stateDelegatesForStandalonePublicationSources() {
    DataServicePublicationReader reader = mock(DataServicePublicationReader.class);
    PublicationState state = new PublicationState(false, false, null, null);
    when(reader.state("LEGACY_TASK", "100")).thenReturn(state);
    DataServicePublicationStateController controller = new DataServicePublicationStateController(reader);

    controller.state("LEGACY_TASK", "100");

    verify(reader).state("LEGACY_TASK", "100");
  }

  @Test
  void managedPublicationStateMustBeReadFromOwnerContext() {
    DataServicePublicationReader reader = mock(DataServicePublicationReader.class);
    when(reader.managesServiceDefinition("DATA_DEVELOPMENT_DATA_SERVICE")).thenReturn(true);
    DataServicePublicationStateController controller = new DataServicePublicationStateController(reader);

    assertThrows(
        IllegalStateException.class,
        () -> controller.state("DATA_DEVELOPMENT_DATA_SERVICE", "100"));
  }
}

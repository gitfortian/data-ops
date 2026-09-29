package io.yak.ops.business.development.dataservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.dataservice.publication.PublishRequest;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentDataServicePublicationServiceTest {

  private DevelopmentNodeRepository nodes;
  private DataServicePublicationReader publicationReader;
  private DataServicePublisher publisher;
  private DataServiceManager manager;
  private DataServiceViewFactory viewFactory;
  private DevelopmentDataServicePublicationService service;

  @BeforeEach
  void setUp() {
    nodes = mock(DevelopmentNodeRepository.class);
    publicationReader = mock(DataServicePublicationReader.class);
    publisher = mock(DataServicePublisher.class);
    manager = mock(DataServiceManager.class);
    viewFactory = mock(DataServiceViewFactory.class);
    service = new DevelopmentDataServicePublicationService(
        nodes, publicationReader, publisher, manager, viewFactory);
    when(nodes.findById(7L)).thenReturn(Optional.of(node(7L, "DATA_SERVICE")));
  }

  @Test
  void unpublishedNodePublishesThroughOwnerContext() {
    when(publicationReader.state(
        DevelopmentDataServicePublicationService.SOURCE_TYPE, "7"))
        .thenReturn(new PublicationState(false, false, null, null));
    DataServiceView published = mock(DataServiceView.class);
    when(publisher.publish(any(PublishRequest.class))).thenReturn(published);

    assertEquals(published, service.online(7L));

    verify(publisher).publish(any(PublishRequest.class));
  }

  @Test
  void updateAvailableRepublishesExistingRuntime() {
    DataServiceView current = mock(DataServiceView.class);
    when(current.id()).thenReturn(99L);
    when(current.enabled()).thenReturn(true);
    when(publicationReader.state(
        DevelopmentDataServicePublicationService.SOURCE_TYPE, "7"))
        .thenReturn(new PublicationState(true, true, null, current));
    DataServiceView republished = mock(DataServiceView.class);
    when(publisher.republish(org.mockito.ArgumentMatchers.eq(99L), any(PublicationSettings.class)))
        .thenReturn(republished);

    assertEquals(republished, service.online(7L));
  }

  @Test
  void offlineDisablesExistingRuntime() {
    DataServiceView current = mock(DataServiceView.class);
    when(current.id()).thenReturn(99L);
    when(current.enabled()).thenReturn(true);
    when(publicationReader.state(
        DevelopmentDataServicePublicationService.SOURCE_TYPE, "7"))
        .thenReturn(new PublicationState(true, false, null, current));
    DataServiceDefinition disabled = mock(DataServiceDefinition.class);
    DataServiceView view = mock(DataServiceView.class);
    when(manager.setEnabled(99L, false)).thenReturn(disabled);
    when(viewFactory.view(disabled)).thenReturn(view);

    assertEquals(view, service.offline(7L));
    verify(manager).setEnabled(99L, false);
  }

  @Test
  void rejectsNonDataServiceNodes() {
    when(nodes.findById(8L)).thenReturn(Optional.of(node(8L, "SQL")));

    assertThrows(IllegalArgumentException.class, () -> service.state(8L));
  }

  private DevelopmentNode node(long id, String type) {
    Instant now = Instant.parse("2026-08-28T00:00:00Z");
    return new DevelopmentNode(id, "node-" + id, type, 3L, null, true, now, now);
  }
}

package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.dataservice.publication.PublishRequest;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.mdm.distribution.MdmDataServiceSourceProvider;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 分发发布编排单元测试(R5):三态收敛 publish/republish/启用 + 数据服务缺席降级。 */
class MdmDistributionPublishServiceTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<DataServicePublisher> publishers = mock(ObjectProvider.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<DataServicePublicationReader> readers = mock(ObjectProvider.class);

  private DataServicePublisher publisher;
  private DataServicePublicationReader reader;
  private MdmDistributionPublishService service;

  @BeforeEach
  void setUp() {
    publisher = mock(DataServicePublisher.class);
    reader = mock(DataServicePublicationReader.class);
    when(publishers.getIfAvailable()).thenReturn(publisher);
    when(readers.getIfAvailable()).thenReturn(reader);
    service = new MdmDistributionPublishService(publishers, readers);
  }

  private static MdmDistribution config(Long id) {
    return new MdmDistribution(
        id, 1L, "CRM", "CRM 系统", MdmDistributionMode.API, "DAILY", "FULL",
        MdmDistributionStatus.ACTIVE, null, 0, 0, "root", LocalDateTime.now(), null);
  }

  @Test
  void unpublishedConfigPublishesByIdempotentRequest() {
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(false, false, null, null));
    DataServiceView published = mock(DataServiceView.class);
    when(publisher.publish(any(PublishRequest.class))).thenReturn(published);

    MdmDistributionPublishService.PublishOutcome outcome = service.online(config(10L));
    assertEquals(published, outcome.view());
    assertTrue(outcome.changed());

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(publisher).publish(captor.capture());
    assertEquals(MdmDataServiceSourceProvider.SOURCE_TYPE, captor.getValue().sourceType());
    assertEquals("10", captor.getValue().sourceRef());
    assertEquals(Boolean.TRUE, captor.getValue().enabled());
  }

  @Test
  void changedSourceDefinitionRepublishes() {
    DataServiceView current = mock(DataServiceView.class);
    when(current.id()).thenReturn(77L);
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(true, true, null, current));

    MdmDistributionPublishService.PublishOutcome outcome = service.online(config(10L));

    assertTrue(outcome.changed(), "口径变更必须标记为已变化,订阅方才会收到通知");
    verify(publisher).republish(eq(77L), any(PublicationSettings.class));
    verify(publisher, never()).publish(any());
  }

  @Test
  void disabledRuntimeIsReEnabledWithoutRepublishWhenDefinitionUnchanged() {
    DataServiceView current = mock(DataServiceView.class);
    when(current.id()).thenReturn(77L);
    when(current.enabled()).thenReturn(false);
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(true, false, null, current));

    assertTrue(service.online(config(10L)).changed(), "重新启用等于恢复供数,应当通知");
    verify(publisher).republish(eq(77L), any(PublicationSettings.class));
  }

  @Test
  void changedDefinitionAndDisabledRuntimeRecombineIntoOneEnabledRepublish() {
    DataServiceView current = mock(DataServiceView.class);
    DataServiceView refreshed = mock(DataServiceView.class);
    when(current.id()).thenReturn(77L);
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(true, true, null, current));
    when(publisher.republish(eq(77L), any(PublicationSettings.class))).thenReturn(refreshed);

    MdmDistributionPublishService.PublishOutcome outcome = service.online(config(10L));

    assertTrue(outcome.changed());
    assertEquals(refreshed, outcome.view());
    ArgumentCaptor<PublicationSettings> settings =
        ArgumentCaptor.forClass(PublicationSettings.class);
    verify(publisher, times(1)).republish(eq(77L), settings.capture());
    assertEquals(Boolean.TRUE, settings.getValue().enabled());
    verify(publisher, never()).publish(any());
  }

  @Test
  void publishedAndUpToDateReturnsCurrentViewAsIs() {
    DataServiceView current = mock(DataServiceView.class);
    when(current.enabled()).thenReturn(true);
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(true, false, null, current));

    MdmDistributionPublishService.PublishOutcome outcome = service.online(config(10L));
    assertEquals(current, outcome.view());
    assertFalse(outcome.changed());
    verify(publisher, never()).publish(any());
    verify(publisher, never()).republish(org.mockito.ArgumentMatchers.anyLong(), any());
  }

  @Test
  void absentDataServiceFailsExplicitlyInsteadOfFakeSuccess() {
    when(publishers.getIfAvailable()).thenReturn(null);
    assertFalse(service.available());

    MdmException exception =
        assertThrows(MdmException.class, () -> service.online(config(10L)));
    assertEquals(MdmErrorCode.DATA_SERVICE_DISABLED, exception.getErrorCode());
  }

  @Test
  void currentViewIsEmptyWhenNotPublishedAndWhenModuleAbsent() {
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(false, false, null, null));
    assertTrue(service.currentView(config(10L)).isEmpty());

    when(readers.getIfAvailable()).thenReturn(null);
    assertFalse(service.available());
    assertTrue(service.currentView(config(10L)).isEmpty());
  }

  @Test
  void currentViewReturnsPublishedDetail() {
    DataServiceView current = mock(DataServiceView.class);
    when(reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, "10"))
        .thenReturn(new PublicationState(true, false, null, current));

    assertEquals(Optional.of(current), service.currentView(config(10L)));
  }
}

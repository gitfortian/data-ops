package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.domain.OfflineExecutionFinalFailureEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class OfflineFailureNotificationListenerTest {

  @Test
  @SuppressWarnings("unchecked")
  void publishesActionableFailureIntent() {
    OfflineJobDefinitionRepository definitions = mock(OfflineJobDefinitionRepository.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(10L);
    definition.setProjectId(7L);
    definition.setJobName("订单离线同步");
    definition.setSourceTable("ods_order");
    definition.setSinkTable("dwd_order");
    when(definitions.findById(10L)).thenReturn(Optional.of(definition));

    OfflineFailureNotificationListener listener =
        new OfflineFailureNotificationListener(definitions, provider);
    listener.onFinalFailure(new OfflineExecutionFinalFailureEvent(99L, 10L, "engine down"));

    ArgumentCaptor<NotificationIntent> captor =
        ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    NotificationIntent intent = captor.getValue();
    assertThat(intent.projectId()).isEqualTo(7L);
    assertThat(intent.type()).isEqualTo(NotificationIntent.Type.TASK);
    assertThat(intent.level()).isEqualTo(NotificationIntent.Level.ERROR);
    assertThat(intent.summary()).contains("订单离线同步", "ods_order", "dwd_order");
    assertThat(intent.content()).isEqualTo("engine down");
    assertThat(intent.sourceType()).isEqualTo("OFFLINE_SYNC_EXECUTION");
    assertThat(intent.sourceId()).isEqualTo("99");
    assertThat(intent.actionPath()).isEqualTo("/sync/batch-link-up/10/detail");
  }
}

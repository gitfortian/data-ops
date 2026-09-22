package io.yak.ops.business.sync.offline.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.definition.OfflineDefinitionSupport.DraftDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.business.sync.offline.notification.OfflineNotificationPolicyCodec;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleLifecycle;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleSupport;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineJobDefinitionServiceNotificationCompatibilityTest {

  @Test
  void olderClientOmittingControlPlaneMetadataPreservesConfiguredValues() {
    OfflineJobDefinitionRepository definitions = mock(OfflineJobDefinitionRepository.class);
    OfflineBatchExecutionRepository batches = mock(OfflineBatchExecutionRepository.class);
    OfflineScheduleRepository schedules = mock(OfflineScheduleRepository.class);
    OfflineDefinitionSupport support = mock(OfflineDefinitionSupport.class);
    OfflineNotificationPolicyCodec codec = mock(OfflineNotificationPolicyCodec.class);
    OfflineEditorMetaCodec editorMetaCodec = mock(OfflineEditorMetaCodec.class);
    OfflineScheduleSupport scheduleSupport = mock(OfflineScheduleSupport.class);
    OfflineScheduleLifecycle lifecycle = mock(OfflineScheduleLifecycle.class);
    OfflineSyncViewMapper viewMapper = mock(OfflineSyncViewMapper.class);

    OfflineJobDefinition existing = new OfflineJobDefinition();
    existing.setId(10L);
    existing.setReleaseState("OFFLINE");
    existing.setNotificationConfigJson("{\"enabled\":false}");
    existing.setEditorMetaJson(
        "{\"icon\":{\"emoji\":\"🚀\",\"background\":\"#DCEEFF\"}}");
    when(definitions.findById(10L)).thenReturn(Optional.of(existing));
    when(definitions.existsByName("订单同步", 10L)).thenReturn(false);

    ObjectNode request = new ObjectMapper().createObjectNode();
    request.set("schedule", new ObjectMapper().createObjectNode());
    when(support.prepareDraft(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new DraftDefinition(
            request,
            "订单同步",
            null,
            "GUIDE_SINGLE",
            "{}",
            "MYSQL",
            "MYSQL"));

    OfflineJobDefinitionService service = new OfflineJobDefinitionService(
        definitions,
        batches,
        schedules,
        support,
        codec,
        editorMetaCodec,
        scheduleSupport,
        lifecycle,
        viewMapper,
        mock(OfflineJobRevisionService.class));

    OfflineJobDefinitionDTO dto = new OfflineJobDefinitionDTO();
    dto.setId(10L);
    // Deliberately leave dto.notification and dto.editorMeta null to simulate an older client.
    service.saveDraft(dto);

    assertThat(existing.getNotificationConfigJson()).isEqualTo("{\"enabled\":false}");
    assertThat(existing.getEditorMetaJson())
        .isEqualTo("{\"icon\":{\"emoji\":\"🚀\",\"background\":\"#DCEEFF\"}}");
  }
}

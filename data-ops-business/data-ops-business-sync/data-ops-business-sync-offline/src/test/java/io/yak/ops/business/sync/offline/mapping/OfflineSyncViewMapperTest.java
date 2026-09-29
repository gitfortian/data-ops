package io.yak.ops.business.sync.offline.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.common.bean.vo.sync.offline.OfflineEngineHealthVO;
import org.junit.jupiter.api.Test;

class OfflineSyncViewMapperTest {

  @Test
  void engineHealthKeepsLinkUpResponseFields() {
    LinkUpNodeResponse node = new LinkUpNodeResponse();
    node.setNodeId("node-1");
    node.setNodeName("link-up");
    node.setInstanceId("instance-1");
    node.setVersion("1.0.0");
    node.setStatus("UP");
    node.setStartedAtMillis(1000L);
    node.setOfflineOnly(true);
    node.setMaxConcurrentJobs(4);
    node.setMaxQueuedJobs(20);
    node.setRunningJobs(2);
    node.setQueuedJobs(3);
    node.setActiveJobs(5);
    node.setLifecycle(new ObjectMapper().createObjectNode().put("phase", "READY"));

    OfflineEngineHealthVO result = new OfflineSyncViewMapper().engineHealth(node);

    assertThat(result.getNodeId()).isEqualTo("node-1");
    assertThat(result.getNodeName()).isEqualTo("link-up");
    assertThat(result.getInstanceId()).isEqualTo("instance-1");
    assertThat(result.getVersion()).isEqualTo("1.0.0");
    assertThat(result.getStatus()).isEqualTo("UP");
    assertThat(result.getStartedAtMillis()).isEqualTo(1000L);
    assertThat(result.getOfflineOnly()).isTrue();
    assertThat(result.getMaxConcurrentJobs()).isEqualTo(4);
    assertThat(result.getMaxQueuedJobs()).isEqualTo(20);
    assertThat(result.getRunningJobs()).isEqualTo(2);
    assertThat(result.getQueuedJobs()).isEqualTo(3);
    assertThat(result.getActiveJobs()).isEqualTo(5);
    assertThat(result.getLifecycle().path("phase").asText()).isEqualTo("READY");
  }
}

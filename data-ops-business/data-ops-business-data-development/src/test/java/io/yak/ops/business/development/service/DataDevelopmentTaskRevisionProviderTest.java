package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.task.DataDevelopmentTaskRevisionProvider;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataDevelopmentTaskRevisionProviderTest {

  @Test
  void resolvesOnlyRevisionOwnedByRequestedDevelopmentNodeAndPropagatesProject() {
    DevelopmentTaskRevisionRepository revisions = mock(DevelopmentTaskRevisionRepository.class);
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DataDevelopmentTaskRevisionProvider provider =
        new DataDevelopmentTaskRevisionProvider(revisions, nodes);
    DevelopmentNode node = new DevelopmentNode(
        12L,
        "Orders",
        "SQL",
        7L,
        null,
        true,
        Instant.parse("2026-08-12T00:00:00Z"),
        Instant.parse("2026-08-12T00:00:00Z"));
    DevelopmentTaskRevision revision = new DevelopmentTaskRevision(
        101L,
        12L,
        3,
        7L,
        new TaskDefinition("SQL", 1, "select 1", "{}"),
        "checksum",
        Instant.parse("2026-08-12T00:00:00Z"));
    when(nodes.findById(12L)).thenReturn(Optional.of(node));
    when(nodes.findById(13L)).thenReturn(Optional.empty());
    when(revisions.findById(101L)).thenReturn(Optional.of(revision));

    assertEquals(TaskAssetSource.DATA_DEVELOPMENT, provider.source());
    var resolved = provider.resolve("12", 101L).orElseThrow();
    assertEquals(3, resolved.revisionNo());
    assertEquals(7L, resolved.sourceProjectId());
    assertTrue(provider.resolve("13", 101L).isEmpty());
  }
}

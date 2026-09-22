package io.yak.ops.business.development.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DevelopmentTaskDomainTruthTest {

  @Test
  void draftAndPublishedRevisionKeepDifferentIdentitySemantics() {
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");
    DevelopmentTaskDraft draft =
        new DevelopmentTaskDraft(1L, definition, 7L, Instant.now(), Instant.now());
    DevelopmentTaskRevision revision =
        new DevelopmentTaskRevision(9L, 1L, 2, 7L, definition, "abc", Instant.now());

    assertTrue(draft.matchesRevision(7L));
    assertFalse(draft.matchesRevision(6L));
    assertTrue(revision.represents(7L, "abc"));
    assertFalse(revision.represents(7L, "changed"));
    assertFalse(revision.represents(8L, "abc"));
  }
}

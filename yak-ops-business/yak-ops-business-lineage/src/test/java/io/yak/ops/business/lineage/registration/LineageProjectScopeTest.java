package io.yak.ops.business.lineage.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterAssetCommand;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterRelationCommand;
import io.yak.ops.business.lineage.repository.LineageRepository;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineageProjectScopeTest {

  @Test
  void sourceProjectCannotOverrideCurrentProject() {
    LineageRepository repository = mock(LineageRepository.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    LineageRegistrationDraftFactory factory =
        new LineageRegistrationDraftFactory(repository, currentProject);

    RegisterAssetCommand command = assetCommand(8L);

    assertThrows(ProjectContextException.class, () -> factory.asset(command, true));
  }

  @Test
  void sourceProjectPropagatesWithoutHttpContext() {
    LineageRepository repository = mock(LineageRepository.class);
    LineageRegistrationDraftFactory factory = new LineageRegistrationDraftFactory(repository);

    LineageAssetDraft draft = factory.asset(assetCommand(7L), true);

    assertEquals(7L, draft.projectId());
  }

  @Test
  void relationRejectsAssetsFromDifferentProjects() {
    LineageRepository repository = mock(LineageRepository.class);
    when(repository.findAsset(1L)).thenReturn(Optional.of(asset(1L, 7L)));
    when(repository.findAsset(2L)).thenReturn(Optional.of(asset(2L, 8L)));
    LineageRegistrationDraftFactory factory = new LineageRegistrationDraftFactory(repository);

    RegisterRelationCommand command = new RegisterRelationCommand(
        1L,
        2L,
        LineageRelationType.DERIVES_FROM,
        "TEST",
        "relation-1",
        null,
        null,
        "v1",
        Instant.parse("2026-08-29T00:00:00Z"),
        null,
        null);

    assertThrows(ProjectContextException.class, () -> factory.relation(command, true));
  }

  private RegisterAssetCommand assetCommand(Long sourceProjectId) {
    return new RegisterAssetCommand(
        "table:orders",
        LineageAssetType.TABLE,
        "orders",
        "TEST",
        "orders",
        null,
        "9",
        "demo",
        null,
        "orders",
        null,
        null,
        sourceProjectId);
  }

  private LineageAsset asset(long id, Long projectId) {
    Instant now = Instant.parse("2026-08-29T00:00:00Z");
    return new LineageAsset(
        id,
        projectId,
        "asset:" + id,
        LineageAssetType.TABLE,
        "asset-" + id,
        "TEST",
        String.valueOf(id),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        now,
        now);
  }
}

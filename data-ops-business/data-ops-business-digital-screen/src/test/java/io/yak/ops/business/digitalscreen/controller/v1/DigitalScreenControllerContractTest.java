package io.yak.ops.business.digitalscreen.controller.v1;

import static io.yak.ops.core.project.ProjectMigrationMode.PROJECT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DigitalScreenControllerContractTest {

  @Test
  void baseRestPathRemainsStableInsideRequiredProjectScope() {
    RequestMapping mapping = DigitalScreenController.class.getAnnotation(RequestMapping.class);
    ProjectScope projectScope = DigitalScreenController.class.getAnnotation(ProjectScope.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/digital-screens");
    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(PROJECT_REQUIRED);
  }

  @Test
  void viewerAndVersionHistoryUseExplicitPublishedSnapshotRoutes() throws Exception {
    Method published = DigitalScreenController.class.getMethod("published", long.class);
    Method versions = DigitalScreenController.class.getMethod("versions", long.class);
    Method version = DigitalScreenController.class.getMethod("version", long.class, int.class);
    assertThat(published.getAnnotation(GetMapping.class).value())
        .containsExactly("/{screenId}/published");
    assertThat(versions.getAnnotation(GetMapping.class).value())
        .containsExactly("/{screenId}/versions");
    assertThat(version.getAnnotation(GetMapping.class).value())
        .containsExactly("/{screenId}/versions/{versionNo}");
  }

  @Test
  void publishOfflineAndRollbackRemainExplicitLifecycleActions() throws Exception {
    Method publish = DigitalScreenController.class.getMethod("publish", long.class);
    Method offline = DigitalScreenController.class.getMethod("offline", long.class);
    Method rollback = DigitalScreenController.class.getMethod("rollback", long.class, int.class);
    assertThat(publish.getAnnotation(PostMapping.class).value())
        .containsExactly("/{screenId}/publish");
    assertThat(offline.getAnnotation(PostMapping.class).value())
        .containsExactly("/{screenId}/offline");
    assertThat(rollback.getAnnotation(PostMapping.class).value())
        .containsExactly("/{screenId}/versions/{versionNo}/rollback");
  }
}

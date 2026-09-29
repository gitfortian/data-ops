package io.yak.ops.business.analysis.controller.v1;

import static io.yak.ops.core.project.ProjectMigrationMode.PROJECT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.analysis.controller.v1.dto.AnalysisRequests.SaveAnalysisRequest;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AnalysisControllerContractTest {

  @Test
  void keepsExistingVersionedCrudContractInsideRequiredProjectScope() throws Exception {
    RequestMapping root = AnalysisController.class.getAnnotation(RequestMapping.class);
    ProjectScope projectScope = AnalysisController.class.getAnnotation(ProjectScope.class);
    Method list = AnalysisController.class.getMethod("list");
    Method get = AnalysisController.class.getMethod("get", long.class);
    Method create = AnalysisController.class.getMethod("create", SaveAnalysisRequest.class);
    Method update = AnalysisController.class.getMethod(
        "update", long.class, SaveAnalysisRequest.class);
    Method delete = AnalysisController.class.getMethod("delete", long.class);

    assertThat(root.value()).containsExactly("/api/v1/analyses");
    assertThat(projectScope).isNotNull();
    assertThat(projectScope.value()).isEqualTo(PROJECT_REQUIRED);
    assertThat(list.getAnnotation(GetMapping.class)).isNotNull();
    assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{analysisId}");
    assertThat(create.getAnnotation(PostMapping.class)).isNotNull();
    assertThat(update.getAnnotation(PutMapping.class).value()).containsExactly("/{analysisId}");
    assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{analysisId}");
  }
}

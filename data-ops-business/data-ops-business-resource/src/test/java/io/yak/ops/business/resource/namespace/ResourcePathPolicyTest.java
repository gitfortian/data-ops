package io.yak.ops.business.resource.namespace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.resource.domain.ResourcePath;
import io.yak.ops.business.resource.exception.ResourceException;
import org.junit.jupiter.api.Test;

class ResourcePathPolicyTest {

  private final ResourceNamePolicy names = new ResourceNamePolicy();

  @Test
  void buildsSafeLogicalAndStoragePaths() {
    assertThat(ResourcePath.root().child(names.normalize("demo.sql")).value())
        .isEqualTo("/demo.sql");
    assertThat(new ResourcePath("/jobs").child(names.normalize("demo.sql")).value())
        .isEqualTo("/jobs/demo.sql");
    assertThat(new ResourcePath("/jobs/demo.sql").storagePath())
        .isEqualTo("jobs/demo.sql");
    assertThat(ResourcePath.suffix("demo.SQL")).isEqualTo("sql");
  }

  @Test
  void rejectsTraversalAndPathSeparatorsInNames() {
    assertThatThrownBy(() -> names.normalize(".."))
        .isInstanceOf(ResourceException.class);
    assertThatThrownBy(() -> names.normalize("a/b"))
        .isInstanceOf(ResourceException.class);
    assertThatThrownBy(() -> names.normalize("a\\b"))
        .isInstanceOf(ResourceException.class);
  }
}

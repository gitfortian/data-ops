package io.yak.ops.boot.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

class YakConsumptionPublicBoundaryTest {
  @Test
  void externalRuntimePathDoesNotRequireConsoleLogin() throws IOException {
    assertThat(isPublic("/api/v1/data-service/runtime/orders")).isTrue();
    assertThat(isPublic("/api/v1/data-service/runtime/nested/orders")).isTrue();
  }

  @Test
  void sourceManagementAndConsumptionKeepConsoleAuthentication() throws IOException {
    for (String path : List.of("/api/v1/data-service/1", "/api/v1/data-service/consumers",
        "/api/v1/data-service/consumers/1/keys", "/api/v1/data-service/1/logs",
        "/api/v1/data-service/1/runtime", "/api/v1/datasets/1/query",
        "/api/v1/consumption/subscriptions", "/api/v1/consumption/products/DATASET:1")) {
      assertThat(isPublic(path)).as(path).isFalse();
    }
  }

  private boolean isPublic(String path) throws IOException {
    var source = new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml")).getFirst();
    List<String> patterns = new Binder(ConfigurationPropertySources.from(source))
        .bind("yak.security.public-paths", Bindable.listOf(String.class)).get();
    AntPathMatcher matcher = new AntPathMatcher();
    return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
  }
}

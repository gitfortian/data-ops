package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.yak.ops.business.quality.asset.QualityAssetSectionProvider;
import io.yak.ops.business.quality.asset.QualityTableAssetReader;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.spi.section.SectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class QualitySectionProviderUniquenessTest {

  @Test
  void qualityModuleAssemblesExactlyOneQualitySectionProvider() {
    new ApplicationContextRunner()
        .withUserConfiguration(QualityProviderConfiguration.class)
        .withPropertyValues("yak.quality.enabled=true")
        .withBean(QualityTableAssetReader.class, () -> mock(QualityTableAssetReader.class))
        .withBean(QualityMonitorReader.class, () -> mock(QualityMonitorReader.class))
        .withBean(QualityExecutionReader.class, () -> mock(QualityExecutionReader.class))
        .run(context -> {
          assertThat(context.getBeansOfType(SectionProvider.class)).hasSize(1);
          assertThat(context.getBean(SectionProvider.class))
              .isInstanceOf(QualityAssetSectionProvider.class);
        });
  }

  @Test
  void onlyQualityDomainDeclaresAQualitySectionProvider() throws IOException {
    Path repository = repositoryRoot();
    Path businessModules = repository.resolve("yak-ops-business");
    List<String> qualityProviders;
    try (var paths = Files.walk(businessModules)) {
      qualityProviders = paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> path.toString().contains("src" + java.io.File.separator + "main"))
          .filter(this::isQualitySectionProvider)
          .map(repository::relativize)
          .map(Path::toString)
          .toList();
    }

    assertThat(qualityProviders).containsExactly(
        Path.of("yak-ops-business", "yak-ops-business-quality", "src", "main", "java",
            "io", "yak", "ops", "business", "quality", "asset",
            "QualityAssetSectionProvider.java").toString());
    assertThat(repository.resolve(Path.of("yak-ops-business", "yak-ops-business-asset",
        "src", "main", "java", "io", "yak", "ops", "business", "asset", "quality",
        "QualitySectionProvider.java"))).doesNotExist();
  }

  private boolean isQualitySectionProvider(Path source) {
    try {
      String content = Files.readString(source, StandardCharsets.UTF_8);
      return content.contains("implements SectionProvider")
          && content.contains("return SectionType.QUALITY;");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to inspect " + source, e);
    }
  }

  private Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null && !Files.isDirectory(current.resolve("yak-ops-business"))) {
      current = current.getParent();
    }
    assertThat(current).as("Repository root should contain yak-ops-business").isNotNull();
    return current;
  }

  @Configuration(proxyBeanMethods = false)
  @Import(QualityAssetSectionProvider.class)
  static class QualityProviderConfiguration {}
}

package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Executable contract for Lineage direct dependencies and runtime-adapter ownership. */
class LineageMavenDependencyBoundaryTest {

  private static final String LINEAGE = "yak-ops-business-lineage";
  private static final String DATASOURCE = "yak-ops-business-datasource";

  @Test
  void lineageDirectDependencySurfaceIsExact() throws Exception {
    assertThat(dependencies(lineagePom()))
        .containsExactlyInAnyOrder(
            dependency("io.yak.ops", "yak-ops-common"),
            optional("io.yak.ops", DATASOURCE),
            dependency("org.springframework.boot", "spring-boot-starter-web"),
            dependency("org.springframework.boot", "spring-boot-starter-validation"),
            dependency("org.springframework", "spring-tx"),
            dependency("com.baomidou", "mybatis-plus-spring-boot3-starter"),
            dependency("io.swagger.core.v3", "swagger-annotations-jakarta"),
            dependency("org.flywaydb", "flyway-core"),
            optional("org.projectlombok", "lombok"),
            testDependency("org.springframework.boot", "spring-boot-starter-test"));
  }

  @Test
  void sharedDatabaseModuleOwnsMybatisAndFlywayRuntimeAdapters() throws Exception {
    Set<Dependency> dependencies = dependencies(datasourcePom());

    assertThat(dependencies)
        .contains(
            runtimeDependency("com.baomidou", "mybatis-plus-jsqlparser-4.9"),
            runtimeDependency("org.flywaydb", "flyway-mysql"));
  }

  @Test
  void everyDirectLineageConsumerOwnsDatasourceAssemblyExplicitly() throws Exception {
    Set<String> consumers = new LinkedHashSet<>();
    try (Stream<Path> paths = Files.walk(repositoryRoot())) {
      for (Path pom : paths.filter(this::isProjectPom).toList()) {
        Set<Dependency> dependencies = dependencies(pom);
        if (!contains(dependencies, "io.yak.ops", LINEAGE)) continue;

        String relative = normalize(repositoryRoot().relativize(pom));
        consumers.add(relative);
        assertThat(contains(dependencies, "io.yak.ops", DATASOURCE))
            .as("%s must assemble Datasource explicitly because Lineage keeps it optional", relative)
            .isTrue();
      }
    }

    assertThat(consumers)
        .as("The explicit assembly rule must exercise real Lineage consumers")
        .isNotEmpty();
  }

  private boolean contains(Set<Dependency> dependencies, String groupId, String artifactId) {
    return dependencies.stream()
        .anyMatch(
            dependency ->
                dependency.groupId().equals(groupId)
                    && dependency.artifactId().equals(artifactId));
  }

  private Set<Dependency> dependencies(Path pom) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Document document = factory.newDocumentBuilder().parse(pom.toFile());
    Element dependencies = directChild(document.getDocumentElement(), "dependencies");
    if (dependencies == null) return Set.of();

    Set<Dependency> result = new LinkedHashSet<>();
    NodeList children = dependencies.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (!(child instanceof Element element) || !"dependency".equals(localName(element))) {
        continue;
      }
      result.add(
          new Dependency(
              directChildText(element, "groupId"),
              directChildText(element, "artifactId"),
              defaultValue(directChildText(element, "scope"), "compile"),
              Boolean.parseBoolean(defaultValue(directChildText(element, "optional"), "false"))));
    }
    return result;
  }

  private Element directChild(Element parent, String name) {
    NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);
      if (child instanceof Element element && name.equals(localName(element))) return element;
    }
    return null;
  }

  private String directChildText(Element parent, String name) {
    Element child = directChild(parent, name);
    return child == null ? null : child.getTextContent().trim();
  }

  private String localName(Element element) {
    return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
  }

  private String defaultValue(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private Dependency dependency(String groupId, String artifactId) {
    return new Dependency(groupId, artifactId, "compile", false);
  }

  private Dependency optional(String groupId, String artifactId) {
    return new Dependency(groupId, artifactId, "compile", true);
  }

  private Dependency runtimeDependency(String groupId, String artifactId) {
    return new Dependency(groupId, artifactId, "runtime", false);
  }

  private Dependency testDependency(String groupId, String artifactId) {
    return new Dependency(groupId, artifactId, "test", false);
  }

  private boolean isProjectPom(Path path) {
    String normalized = normalize(path);
    return path.getFileName().toString().equals("pom.xml")
        && !normalized.contains("/target/")
        && !normalized.contains("/.deps/");
  }

  private Path lineagePom() {
    return repositoryRoot()
        .resolve("yak-ops-business/yak-ops-business-lineage/pom.xml");
  }

  private Path datasourcePom() {
    return repositoryRoot()
        .resolve("yak-ops-business/yak-ops-business-datasource/pom.xml");
  }

  private Path repositoryRoot() {
    Path current = Paths.get(".").toAbsolutePath().normalize();
    if (Files.isDirectory(current.resolve("yak-ops-business"))) return current;

    Path candidate = current.resolve("../..").normalize();
    assertThat(Files.isDirectory(candidate.resolve("yak-ops-business")))
        .as("Unable to locate repository root from %s", current)
        .isTrue();
    return candidate;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record Dependency(
      String groupId,
      String artifactId,
      String scope,
      boolean optional) {
  }
}

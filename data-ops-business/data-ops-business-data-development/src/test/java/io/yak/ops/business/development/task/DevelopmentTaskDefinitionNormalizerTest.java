package io.yak.ops.business.development.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.spi.task.model.TaskDefinition;
import org.junit.jupiter.api.Test;

class DevelopmentTaskDefinitionNormalizerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DevelopmentTaskDefinitionNormalizer normalizer =
      new DevelopmentTaskDefinitionNormalizer(objectMapper);

  @Test
  void legacySqlConfigDefaultsToGenericDialect() throws Exception {
    TaskDefinition definition = normalizer.normalize(
        sqlNode(),
        "SQL",
        1,
        "select 1",
        "{}");

    JsonNode config = objectMapper.readTree(definition.configJson());
    assertThat(definition.taskType()).isEqualTo("SQL");
    assertThat(config.path("dialect").asText()).isEqualTo("GENERIC");
  }

  @Test
  void canonicalizesDatabaseTypeAliasWithoutLosingOtherSqlConfig() throws Exception {
    TaskDefinition definition = normalizer.normalize(
        sqlNode(),
        "sql",
        1,
        "select * from t",
        "{\"databaseType\":\"PostgreSQL\",\"dataSourceId\":\"42\",\"maxRows\":100}");

    JsonNode config = objectMapper.readTree(definition.configJson());
    assertThat(config.path("dialect").asText()).isEqualTo("POSTGRE_SQL");
    assertThat(config.path("dataSourceId").asText()).isEqualTo("42");
    assertThat(config.path("maxRows").asInt()).isEqualTo(100);
    assertThat(config.has("databaseType")).isFalse();
  }

  @Test
  void doesNotInjectDialectIntoNonSqlTaskConfig() throws Exception {
    DevelopmentNode shell = new DevelopmentNode(2L, "shell", "SHELL", 1L, null, false, null, null);
    TaskDefinition definition = normalizer.normalize(shell, "SHELL", 1, "echo ok", "{}");

    assertThat(objectMapper.readTree(definition.configJson()).has("dialect")).isFalse();
  }

  @Test
  void rejectsUnknownExplicitSqlDialect() {
    assertThatThrownBy(() -> normalizer.normalize(
        sqlNode(),
        "SQL",
        1,
        "select 1",
        "{\"dialect\":\"NOT_A_DATABASE\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL dialect");
  }

  private DevelopmentNode sqlNode() {
    return new DevelopmentNode(1L, "sql", "SQL", 1L, null, false, null, null);
  }
}

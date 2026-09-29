package io.yak.ops.business.sync.offline.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class MongoOfflineDefinitionModelAdapterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void stripsDatasourceSecretsButKeepsMongoTaskOptions() throws Exception {
    ObjectNode definition =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "source": {
                    "connectorId":"mongodb",
                    "uri":"mongodb://user:secret@mongo/app",
                    "password":"secret",
                    "options": {
                      "uri":"mongodb://user:secret@mongo/app",
                      "password":"secret",
                      "filter":"{\"active\":true}"
                    },
                    "config": {
                      "connectorOptions": {
                        "uri":"mongodb://user:secret@mongo/app",
                        "password":"secret",
                        "filter":"{\"active\":true}"
                      }
                    }
                  },
                  "sink": {
                    "connectorId":"mongodb",
                    "options": {
                      "username":"yak",
                      "password":"secret",
                      "document_id_field":"user_id"
                    }
                  }
                }
                """);

    OfflineDefinitionModelAdapter.sanitizeForPersistence(definition);

    assertThat(definition.path("source").has("uri")).isFalse();
    assertThat(definition.path("source").has("password")).isFalse();
    assertThat(definition.path("source").path("options").has("uri")).isFalse();
    assertThat(definition.path("source").path("options").has("password")).isFalse();
    assertThat(definition.path("source").path("options").path("filter").asText())
        .contains("active");
    assertThat(
            definition
                .path("source")
                .path("config")
                .path("connectorOptions")
                .has("uri"))
        .isFalse();
    assertThat(definition.path("sink").path("options").has("username")).isFalse();
    assertThat(definition.path("sink").path("options").has("password")).isFalse();
    assertThat(definition.path("sink").path("options").path("document_id_field").asText())
        .isEqualTo("user_id");
  }

  @Test
  void copiesMongoFieldSelectionIntoJobSpecConfig() throws Exception {
    ObjectNode definition =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "basic":{"mode":"GUIDE_SINGLE"},
                  "source":{
                    "connectorId":"mongodb",
                    "table":"users",
                    "fields":["_id","address.city"]
                  },
                  "sink":{"connectorId":"jdbc"}
                }
                """);

    ObjectNode adapted =
        (ObjectNode) OfflineDefinitionModelAdapter.forJobSpec(definition, mapper);

    assertThat(adapted.path("source").path("config").path("fields")).hasSize(2);
    assertThat(adapted.path("source").path("config").path("fields").get(1).asText())
        .isEqualTo("address.city");
  }
}

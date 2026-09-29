package io.yak.ops.business.datasource.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.security.SensitiveTextMasker;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataSourceSecretCodecTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DataSourceSecretCodec codec =
      new DataSourceSecretCodec(objectMapper, new SensitiveTextMasker());

  @Test
  void shouldMaskPasswordInResponseJsonAndJdbcUrl() throws Exception {
    DataSourcePluginDescriptor descriptor = descriptorWithPasswordField();
    String masked =
        codec.maskConnectionJson(
            descriptor,
            "{\"host\":\"db\",\"username\":\"test_user\","
                + "\"password\":\"TEST_ONLY_VALUE\","
                + "\"properties\":{\"accessToken\":\"TEST_ONLY_TOKEN\"}}");
    JsonNode root = objectMapper.readTree(masked);

    assertThat(root.get("password").asText()).isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(root.path("properties").path("accessToken").asText())
        .isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(root.get("username").asText()).isEqualTo("test_user");
    assertThat(
            codec.maskSensitiveText(
                "jdbc:mysql://test_user:TEST_ONLY_VALUE@db/demo?password=TEST_ONLY_VALUE"))
        .isEqualTo("jdbc:mysql://test_user:******@db/demo?password=******");
  }

  @Test
  void shouldMaskNestedSshCredentials() throws Exception {
    String masked =
        codec.maskConnectionJson(
            descriptorWithPasswordField(),
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion\","
                + "\"password\":\"TEST_ONLY_VALUE\",\"privateKey\":\"TEST_ONLY_KEY\","
                + "\"passphrase\":\"TEST_ONLY_PHRASE\"}}");
    JsonNode ssh = objectMapper.readTree(masked).path("sshTunnel");

    assertThat(ssh.path("password").asText()).isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("privateKey").asText()).isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("passphrase").asText()).isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("host").asText()).isEqualTo("bastion");
  }

  @Test
  void shouldReuseStoredPasswordWhenEditSubmitsMask() throws Exception {
    String merged =
        codec.mergeStoredSecrets(
            descriptorWithPasswordField(),
            "{\"host\":\"new-db\",\"password\":\"******\","
                + "\"properties\":{\"accessToken\":\"******\"}}",
            "{\"host\":\"old-db\",\"password\":\"TEST_ONLY_VALUE\","
                + "\"properties\":{\"accessToken\":\"TEST_ONLY_TOKEN\"}}");
    JsonNode root = objectMapper.readTree(merged);

    assertThat(root.get("host").asText()).isEqualTo("new-db");
    assertThat(root.get("password").asText()).isEqualTo("TEST_ONLY_VALUE");
    assertThat(root.path("properties").path("accessToken").asText())
        .isEqualTo("TEST_ONLY_TOKEN");
  }

  @Test
  void shouldReuseStoredSshSecretsWhenEditSubmitsMask() throws Exception {
    String merged =
        codec.mergeStoredSecrets(
            descriptorWithPasswordField(),
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"new-bastion\","
                + "\"password\":\"******\",\"privateKey\":\"******\","
                + "\"passphrase\":\"******\"}}",
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"old-bastion\","
                + "\"password\":\"TEST_ONLY_VALUE\",\"privateKey\":\"TEST_ONLY_KEY\","
                + "\"passphrase\":\"TEST_ONLY_PHRASE\"}}");
    JsonNode ssh = objectMapper.readTree(merged).path("sshTunnel");

    assertThat(ssh.path("host").asText()).isEqualTo("new-bastion");
    assertThat(ssh.path("password").asText()).isEqualTo("TEST_ONLY_VALUE");
    assertThat(ssh.path("privateKey").asText()).isEqualTo("TEST_ONLY_KEY");
    assertThat(ssh.path("passphrase").asText()).isEqualTo("TEST_ONLY_PHRASE");
  }

  @Test
  void shouldUseNewPasswordWhenUserChangesIt() throws Exception {
    String merged =
        codec.mergeStoredSecrets(
            descriptorWithPasswordField(),
            "{\"password\":\"TEST_ONLY_NEW\"}",
            "{\"password\":\"TEST_ONLY_OLD\"}");

    assertThat(objectMapper.readTree(merged).get("password").asText())
        .isEqualTo("TEST_ONLY_NEW");
  }

  private DataSourcePluginDescriptor descriptorWithPasswordField() {
    FormField username =
        new FormField(
            "username",
            "用户名",
            FieldType.INPUT,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null);
    FormField password =
        new FormField(
            "password",
            "密码",
            FieldType.PASSWORD,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null);
    return new DataSourcePluginDescriptor(
        DataSourceDbType.MYSQL,
        "MySQL",
        DataSourcePluginDescriptor.CURRENT_API_VERSION,
        Set.of(),
        new ConnectionForm(List.of(), List.of(username, password)),
        false,
        null);
  }
}

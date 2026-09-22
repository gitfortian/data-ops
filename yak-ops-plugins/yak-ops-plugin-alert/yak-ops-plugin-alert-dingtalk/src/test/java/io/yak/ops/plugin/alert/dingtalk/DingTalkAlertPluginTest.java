package io.yak.ops.plugin.alert.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.plugin.alert.api.AlertLevel;
import io.yak.ops.plugin.alert.api.AlertMessage;
import io.yak.ops.plugin.alert.api.AlertResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DingTalkAlertPlugin} and {@link DingTalkAlertConfig}. */
class DingTalkAlertPluginTest {

  @Test
  void descriptorShouldExposeCorrectType() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    assertThat(plugin.type()).isEqualTo("DINGTALK");
    assertThat(plugin.descriptor().name()).isEqualTo("DingTalk");
    assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
  }

  @Test
  void sendShouldFailOnInvalidConfig() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    AlertMessage message = AlertMessage.of("Test", "Hello", "{}");
    AlertResult result = plugin.send(message);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("webhookUrl");
  }

  @Test
  void configShouldRejectBlankWebhookUrl() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"\",\"securityType\":\"SIGN\",\"secret\":\"SECtest\"}"));
  }

  @Test
  void configShouldRejectInvalidMsgType() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://example.com\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"test\"],"
                + "\"msgType\":\"actionCard\"}"));
  }

  @Test
  void configShouldParseValidJson() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"SIGN\","
                + "\"secret\":\"mysecret\","
                + "\"msgType\":\"text\"}");

    assertThat(config.webhookUrl()).startsWith("https://oapi.dingtalk.com");
    assertThat(config.secret()).isEqualTo("mysecret");
    assertThat(config.normalizedMsgType()).isEqualTo("text");
    assertThat(config.hasSecret()).isTrue();
  }

  @Test
  void configShouldDefaultToMarkdownAndSign() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"secret\":\"SECtest\"}");

    assertThat(config.normalizedMsgType()).isEqualTo("markdown");
    assertThat(config.hasSecret()).isTrue();
    assertThat(config.securityType()).isEqualTo("SIGN");
  }

  @Test
  void alertMessageShouldRejectBlankContent() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new AlertMessage("Title", "", AlertLevel.INFO, "{}"));
  }

  @Test
  void alertResultOkShouldBeSuccessful() {
    AlertResult ok = AlertResult.ok();
    assertThat(ok.success()).isTrue();
    assertThat(ok.errorMessage()).isNull();
  }

  @Test
  void alertResultFailShouldNotBeSuccessful() {
    AlertResult fail = AlertResult.fail("something went wrong");
    assertThat(fail.success()).isFalse();
    assertThat(fail.errorMessage()).isEqualTo("something went wrong");
  }

  // ---- security type support ----

  @Test
  void configShouldDefaultSecurityTypeToSign() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"secret\":\"SECtest\"}");

    assertThat(config.securityType()).isEqualTo("SIGN");
    assertThat(config.hasSecret()).isTrue();
  }

  @Test
  void configShouldSupportKeywordSecurityType() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\","
                + "\"keywords\":[\"Yak Ops\",\"告警\"]}");

    assertThat(config.securityType()).isEqualTo("KEYWORD");
    assertThat(config.hasKeywords()).isTrue();
    assertThat(config.keywords()).containsExactly("Yak Ops", "告警");
    assertThat(config.effectiveKeyword()).isEqualTo("Yak Ops");
    assertThat(config.hasSecret()).isFalse();
  }

  @Test
  void configShouldSupportIpSecurityType() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"IP\","
                + "\"ipAddresses\":[\"10.0.0.1\",\"192.168.1.0/24\"]}");

    assertThat(config.securityType()).isEqualTo("IP");
    assertThat(config.ipAddresses()).containsExactly("10.0.0.1", "192.168.1.0/24");
    assertThat(config.hasSecret()).isFalse();
    assertThat(config.hasKeywords()).isFalse();
  }

  @Test
  void configShouldRejectSignWithoutSecret() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"SIGN\"}"));
  }

  @Test
  void configShouldRejectKeywordWithoutKeywords() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\"}"));
  }

  // ---- at (mention) field support ----

  @Test
  void configShouldParseAtFields() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"Yak Ops\"],"
                + "\"atMobiles\":[\"13800138000\",\"13900139000\"],"
                + "\"atUserIds\":[\"user123\"],"
                + "\"isAtAll\":false}");

    assertThat(config.atMobiles()).containsExactly("13800138000", "13900139000");
    assertThat(config.atUserIds()).containsExactly("user123");
    assertThat(config.isAtAll()).isFalse();
    assertThat(config.hasAt()).isTrue();
  }

  @Test
  void configShouldSupportAtAll() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"Yak Ops\"],"
                + "\"isAtAll\":true}");

    assertThat(config.isAtAll()).isTrue();
    assertThat(config.hasAt()).isTrue();
  }

  @Test
  void configWithoutAtShouldNotHaveAt() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"Yak Ops\"]}");

    assertThat(config.hasAt()).isFalse();
  }

  @Test
  void textMessageShouldIncludeAtFieldWhenConfigured() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "SIGN", "SECtest", null, null, "text",
            List.of("13800138000"), null, true, null, null, null);

    DingTalkWebhookClient.TextMessage msg =
        new DingTalkWebhookClient.TextMessage("hello", config);

    assertThat(msg.at()).isNotNull();
    assertThat(msg.at().atMobiles()).containsExactly("13800138000");
    assertThat(msg.at().isAtAll()).isTrue();
  }

  @Test
  void textMessageShouldOmitAtFieldWhenNotConfigured() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "KEYWORD", null, List.of("Yak Ops"), null, "text",
            null, null, null, null, null, null);

    DingTalkWebhookClient.TextMessage msg =
        new DingTalkWebhookClient.TextMessage("hello", config);

    assertThat(msg.at()).isNull();
  }

  // ---- link message type support ----

  @Test
  void configShouldRejectLinkWithoutTitle() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://example.com\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"test\"],"
                + "\"msgType\":\"link\","
                + "\"linkMessageUrl\":\"https://example.com/detail\"}"));
  }

  @Test
  void configShouldRejectLinkWithoutMessageUrl() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://example.com\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"test\"],"
                + "\"msgType\":\"link\","
                + "\"linkTitle\":\"Title\"}"));
  }

  @Test
  void configShouldParseLinkType() {
    DingTalkAlertConfig config =
        DingTalkAlertConfig.parse(
            "{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\","
                + "\"securityType\":\"KEYWORD\",\"keywords\":[\"Yak Ops\"],"
                + "\"msgType\":\"link\","
                + "\"linkTitle\":\"Alert Detail\","
                + "\"linkMessageUrl\":\"https://yak-ops.example.com/alert/1\","
                + "\"linkPicUrl\":\"https://yak-ops.example.com/icon.png\"}");

    assertThat(config.normalizedMsgType()).isEqualTo("link");
    assertThat(config.linkTitle()).isEqualTo("Alert Detail");
    assertThat(config.linkMessageUrl()).isEqualTo("https://yak-ops.example.com/alert/1");
    assertThat(config.linkPicUrl()).isEqualTo("https://yak-ops.example.com/icon.png");
  }

  @Test
  void linkMessageShouldSerializeCorrectly() {
    DingTalkWebhookClient.LinkMessage msg =
        new DingTalkWebhookClient.LinkMessage(
            "Title", "Content", "https://example.com", "https://pic.example.com/img.png");

    String json = DingTalkConfigMapper.serialize(msg);
    assertThat(json).contains("\"msgtype\":\"link\"");
    assertThat(json).contains("\"title\":\"Title\"");
    assertThat(json).contains("\"messageUrl\":\"https://example.com\"");
  }

  // ---- test keyword ----

  @Test
  void testKeywordShouldBeYakOps() {
    assertThat(DingTalkAlertPlugin.TEST_KEYWORD).isEqualTo("Yak Ops");
  }

  @Test
  void effectiveKeywordShouldReturnConfiguredKeyword() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "KEYWORD", null, List.of("自定义关键词"), null, "text",
            null, null, null, null, null, null);

    assertThat(config.effectiveKeyword()).isEqualTo("自定义关键词");
  }

  @Test
  void effectiveKeywordShouldDefaultToYakOps() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "IP", null, null, List.of("10.0.0.1"), "text",
            null, null, null, null, null, null);

    assertThat(config.effectiveKeyword()).isEqualTo("Yak Ops");
  }

  // ---- signing ----

  @Test
  void webhookUrlShouldIncludeSignParamsWhenSecretIsConfigured() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "SIGN", "SECabcdef", null, null, "text",
            null, null, null, null, null, null);

    DingTalkWebhookClient client = new DingTalkWebhookClient();
    String url = client.buildWebhookUrl(config);

    assertThat(url).contains("timestamp=");
    assertThat(url).contains("sign=");
  }

  @Test
  void webhookUrlShouldNotIncludeSignParamsWithoutSecret() {
    DingTalkAlertConfig config =
        new DingTalkAlertConfig(
            "https://oapi.dingtalk.com/robot/send?access_token=test",
            "KEYWORD", null, List.of("Yak Ops"), null, "text",
            null, null, null, null, null, null);

    DingTalkWebhookClient client = new DingTalkWebhookClient();
    String url = client.buildWebhookUrl(config);

    assertThat(url).doesNotContain("timestamp=");
    assertThat(url).doesNotContain("sign=");
  }
}

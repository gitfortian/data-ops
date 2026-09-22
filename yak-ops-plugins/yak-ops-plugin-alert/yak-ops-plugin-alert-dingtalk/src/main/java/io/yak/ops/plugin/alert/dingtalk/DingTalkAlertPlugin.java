package io.yak.ops.plugin.alert.dingtalk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.plugin.alert.api.AlertLevel;
import io.yak.ops.plugin.alert.api.AlertMessage;
import io.yak.ops.plugin.alert.api.AlertPlugin;
import io.yak.ops.plugin.alert.api.AlertPluginDescriptor;
import io.yak.ops.plugin.alert.api.AlertResult;

/** DingTalk custom robot webhook alert plugin. */
public final class DingTalkAlertPlugin implements AlertPlugin {

  public static final String TYPE = "DINGTALK";

  /**
   * Keyword used in test messages so they can pass DingTalk's custom keyword security setting.
   * Users should add this keyword (or a superset like "Yak Ops") to their robot's keyword list.
   */
  public static final String TEST_KEYWORD = "Yak Ops";

  private static final AlertPluginDescriptor DESCRIPTOR =
      new AlertPluginDescriptor(
          TYPE,
          "DingTalk",
          "Send alerts via DingTalk custom robot webhook (supports text, markdown & link)",
          "1.0.0");

  private static final ObjectMapper RESPONSE_MAPPER =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final DingTalkWebhookClient client;

  public DingTalkAlertPlugin() {
    this.client = new DingTalkWebhookClient();
  }

  /** Package-private constructor for testing with a custom client. */
  DingTalkAlertPlugin(DingTalkWebhookClient client) {
    this.client = client;
  }

  @Override
  public AlertPluginDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public AlertResult send(AlertMessage message) {
    DingTalkAlertConfig config;
    try {
      config = DingTalkAlertConfig.parse(message.configJson());
    } catch (IllegalArgumentException e) {
      return AlertResult.fail("Invalid DingTalk config: " + e.getMessage());
    }

    String requestBody = buildRequestBody(message, config);

    try {
      String responseBody = client.send(config, requestBody);
      return parseResponse(responseBody);
    } catch (Exception e) {
      return AlertResult.fail("DingTalk webhook request failed: " + e.getMessage());
    }
  }

  @Override
  public boolean testConnection(String configJson) {
    DingTalkAlertConfig config;
    try {
      config = DingTalkAlertConfig.parse(configJson);
    } catch (IllegalArgumentException e) {
      return false;
    }

    // Use a text message containing the effective keyword so it can pass DingTalk's
    // custom keyword security setting if configured.
    String testBody =
        DingTalkConfigMapper.serialize(
            new DingTalkWebhookClient.TextMessage(
                config.effectiveKeyword() + " connectivity test", config));

    try {
      String responseBody = client.send(config, testBody);
      JsonNode node = RESPONSE_MAPPER.readTree(responseBody);
      return node.has("errcode") && node.get("errcode").asInt() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private String buildRequestBody(AlertMessage message, DingTalkAlertConfig config) {
    String levelPrefix = formatLevelPrefix(message.level());

    return switch (config.normalizedMsgType()) {
      case "text" -> {
        String content = levelPrefix + message.content();
        yield DingTalkConfigMapper.serialize(
            new DingTalkWebhookClient.TextMessage(content, config));
      }

      case "link" -> {
        String title = message.title() != null ? message.title() : config.linkTitle();
        String text = levelPrefix + message.content();
        yield DingTalkConfigMapper.serialize(
            new DingTalkWebhookClient.LinkMessage(
                title, text, config.linkMessageUrl(), config.linkPicUrl()));
      }

      default -> {
        // markdown
        String title = message.title() != null ? message.title() : "Yak Ops Alert";
        String text =
            "### "
                + levelPrefix
                + title
                + "\n\n"
                + message.content();
        yield DingTalkConfigMapper.serialize(
            new DingTalkWebhookClient.MarkdownMessage(title, text, config));
      }
    };
  }

  private String formatLevelPrefix(AlertLevel level) {
    if (level == null) {
      return "";
    }
    return switch (level) {
      case ERROR -> "[ERROR] ";
      case WARN -> "[WARN] ";
      case INFO -> "[INFO] ";
    };
  }

  private AlertResult parseResponse(String responseBody) {
    try {
      JsonNode node = RESPONSE_MAPPER.readTree(responseBody);
      if (node.has("errcode") && node.get("errcode").asInt() == 0) {
        return AlertResult.ok();
      }
      String errMsg =
          node.has("errmsg")
              ? node.get("errmsg").asText()
              : "Unknown DingTalk error";
      return AlertResult.fail("DingTalk API error: " + errMsg);
    } catch (Exception e) {
      return AlertResult.fail("Failed to parse DingTalk response: " + e.getMessage());
    }
  }
}

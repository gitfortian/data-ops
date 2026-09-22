package io.yak.ops.plugin.alert.dingtalk;

import io.yak.ops.plugin.alert.api.AlertLevel;
import io.yak.ops.plugin.alert.api.AlertMessage;
import io.yak.ops.plugin.alert.api.AlertResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration test against a real DingTalk webhook.
 *
 * <p>Run manually when needed; disabled by default to avoid sending messages on every build.
 * The webhook security setting uses custom keyword "测试", so message content must include it.
 */
@Disabled("Manual integration test")
class DingTalkRealWebhookTest {

  private static final String WEBHOOK_URL =
      System.getenv("DINGTALK_WEBHOOK_URL");

  @Test
  void sendTextMessage() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\",\"msgType\":\"text\"}";
    AlertMessage message =
        AlertMessage.of("测试通知", "【测试】这是来自 Yak Ops 告警插件的文本消息", AlertLevel.INFO, configJson);

    AlertResult result = plugin.send(message);
    System.out.println("Text result: " + result);
    assert result.success() : "Text message failed: " + result.errorMessage();
  }

  @Test
  void sendMarkdownMessage() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\",\"msgType\":\"markdown\"}";
    AlertMessage message =
        AlertMessage.of(
            "测试告警",
            "#### 测试告警通知\n\n"
                + "> 级别: **WARN**\n\n"
                + "> 来源: Yak Ops 告警插件\n\n"
                + "> 时间: " + java.time.LocalDateTime.now() + "\n\n"
                + "这是一条来自 Yak Ops 的 **Markdown** 告警测试消息",
            AlertLevel.WARN,
            configJson);

    AlertResult result = plugin.send(message);
    System.out.println("Markdown result: " + result);
    assert result.success() : "Markdown message failed: " + result.errorMessage();
  }

  @Test
  void sendLinkMessage() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\","
            + "\"msgType\":\"link\","
            + "\"linkTitle\":\"测试告警详情\","
            + "\"linkMessageUrl\":\"https://github.com/weifuwan/yak-ops\"}";
    AlertMessage message =
        AlertMessage.of(
            null,
            "【测试】Yak Ops 触发了一条 ERROR 级别告警，点击查看详情",
            AlertLevel.ERROR,
            configJson);

    AlertResult result = plugin.send(message);
    System.out.println("Link result: " + result);
    assert result.success() : "Link message failed: " + result.errorMessage();
  }

  @Test
  void testConnection() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\",\"msgType\":\"text\"}";

    AlertMessage testMsg = AlertMessage.of("连通测试", "测试连通性", configJson);
    AlertResult result = plugin.send(testMsg);
    System.out.println("Connection test result: " + result);
    assert result.success() : "Connection test failed: " + result.errorMessage();
  }

  // ---- @mention (at) feature tests ----

  @Test
  void sendTextMessageAtAll() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\","
            + "\"msgType\":\"text\","
            + "\"isAtAll\":true}";
    AlertMessage message =
        AlertMessage.of("@所有人测试", "【测试】@所有人 这是一条文本告警通知", AlertLevel.WARN, configJson);

    AlertResult result = plugin.send(message);
    System.out.println("Text @all result: " + result);
    assert result.success() : "Text @all failed: " + result.errorMessage();
  }

  @Test
  void sendMarkdownMessageAtAll() {
    DingTalkAlertPlugin plugin = new DingTalkAlertPlugin();

    String configJson =
        "{\"webhookUrl\":\"" + WEBHOOK_URL + "\","
            + "\"msgType\":\"markdown\","
            + "\"isAtAll\":true}";
    AlertMessage message =
        AlertMessage.of(
            "测试@所有人",
            "#### 测试告警 \u2014 @所有人\n\n"
                + "> 级别: **ERROR**\n\n"
                + "> 来源: Yak Ops 告警插件\n\n"
                + "> 时间: " + java.time.LocalDateTime.now() + "\n\n"
                + "这是一条 **@所有人** 的 Markdown 告警测试",
            AlertLevel.ERROR,
            configJson);

    AlertResult result = plugin.send(message);
    System.out.println("Markdown @all result: " + result);
    assert result.success() : "Markdown @all failed: " + result.errorMessage();
  }
}

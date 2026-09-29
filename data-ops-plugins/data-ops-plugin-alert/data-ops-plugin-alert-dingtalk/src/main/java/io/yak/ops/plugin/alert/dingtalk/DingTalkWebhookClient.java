package io.yak.ops.plugin.alert.dingtalk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HTTP client for DingTalk custom robot webhook.
 *
 * <p>Supports text, markdown and link message types, with optional HMAC-SHA256 signing for
 * security verification and @mention (at) support.
 */
final class DingTalkWebhookClient {

  private static final String SIGN_ALGORITHM = "HmacSHA256";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient httpClient;

  DingTalkWebhookClient() {
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /**
   * Send a message to the DingTalk webhook.
   *
   * @param config webhook configuration
   * @param requestBody JSON-encoded request body
   * @return DingTalk API response body
   * @throws Exception if the request fails or HTTP status is not 200
   */
  String send(DingTalkAlertConfig config, String requestBody) throws Exception {
    String url = buildWebhookUrl(config);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json; charset=utf-8")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "DingTalk webhook returned HTTP " + response.statusCode() + ": " + response.body());
    }

    return response.body();
  }

  /**
   * Build the final webhook URL with optional sign parameters.
   *
   * <p>When a secret is configured, the URL includes {@code timestamp} and {@code sign} query
   * parameters as required by DingTalk's security verification. The timestamp must be within
   * 1 hour of the DingTalk server time.
   */
  String buildWebhookUrl(DingTalkAlertConfig config) {
    String url = config.webhookUrl();
    if (!config.hasSecret()) {
      return url;
    }

    long timestamp = System.currentTimeMillis();
    String stringToSign = timestamp + "\n" + config.secret();
    String sign = hmacSha256(config.secret(), stringToSign);

    String separator = url.contains("?") ? "&" : "?";
    return url
        + separator
        + "timestamp="
        + timestamp
        + "&sign="
        + URLEncoder.encode(sign, StandardCharsets.UTF_8);
  }

  /** Compute HMAC-SHA256 and return Base64-encoded result. */
  private static String hmacSha256(String key, String message) {
    try {
      Mac mac = Mac.getInstance(SIGN_ALGORITHM);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), SIGN_ALGORITHM));
      byte[] signData = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(signData);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 signing failed: " + e.getMessage(), e);
    }
  }

  // ---- DingTalk request body models ----

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record AtField(
      @JsonProperty("atMobiles") List<String> atMobiles,
      @JsonProperty("atUserIds") List<String> atUserIds,
      @JsonProperty("isAtAll") Boolean isAtAll) {

    static AtField from(DingTalkAlertConfig config) {
      if (config == null || !config.hasAt()) {
        return null;
      }
      return new AtField(
          config.atMobiles(),
          config.atUserIds(),
          config.isAtAll());
    }
  }

  record TextContent(@JsonProperty("content") String content) {}

  record MarkdownContent(
      @JsonProperty("title") String title, @JsonProperty("text") String text) {}

  record LinkContent(
      @JsonProperty("title") String title,
      @JsonProperty("text") String text,
      @JsonProperty("messageUrl") String messageUrl,
      @JsonProperty("picUrl") String picUrl) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record TextMessage(
      @JsonProperty("msgtype") String msgtype,
      @JsonProperty("text") TextContent text,
      @JsonProperty("at") AtField at) {
    TextMessage(String content, DingTalkAlertConfig config) {
      this("text", new TextContent(content), AtField.from(config));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record MarkdownMessage(
      @JsonProperty("msgtype") String msgtype,
      @JsonProperty("markdown") MarkdownContent markdown,
      @JsonProperty("at") AtField at) {
    MarkdownMessage(String title, String text, DingTalkAlertConfig config) {
      this("markdown", new MarkdownContent(title, text), AtField.from(config));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record LinkMessage(
      @JsonProperty("msgtype") String msgtype,
      @JsonProperty("link") LinkContent link) {
    LinkMessage(String title, String text, String messageUrl, String picUrl) {
      this("link", new LinkContent(title, text, messageUrl, picUrl));
    }
  }
}

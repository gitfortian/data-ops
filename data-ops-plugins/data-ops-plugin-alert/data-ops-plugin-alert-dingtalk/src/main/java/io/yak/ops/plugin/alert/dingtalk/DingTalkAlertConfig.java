package io.yak.ops.plugin.alert.dingtalk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DingTalk webhook robot configuration.
 *
 * @param webhookUrl DingTalk custom robot webhook URL
 * @param securityType security verification type: {@code KEYWORD}, {@code SIGN} or {@code IP} (default: {@code SIGN})
 * @param secret signing secret for security verification (required when securityType is SIGN, SEC-prefixed string)
 * @param keywords custom keywords for security verification (required when securityType is KEYWORD)
 * @param ipAddresses IP addresses for security verification (informational, required when securityType is IP)
 * @param msgType message type: {@code text}, {@code markdown} or {@code link} (default: {@code markdown})
 * @param atMobiles phone numbers to @mention (optional)
 * @param atUserIds user IDs to @mention (optional)
 * @param isAtAll whether to @all members (default: false)
 * @param linkTitle title for link-type messages (required when msgType is link)
 * @param linkMessageUrl URL to open when clicking the link message (required when msgType is link)
 * @param linkPicUrl picture URL for link-type messages (optional)
 */
public record DingTalkAlertConfig(
    @JsonProperty("webhookUrl") String webhookUrl,
    @JsonProperty("securityType") String securityType,
    @JsonProperty("secret") String secret,
    @JsonProperty("keywords") List<String> keywords,
    @JsonProperty("ipAddresses") List<String> ipAddresses,
    @JsonProperty("msgType") String msgType,
    @JsonProperty("atMobiles") List<String> atMobiles,
    @JsonProperty("atUserIds") List<String> atUserIds,
    @JsonProperty("isAtAll") Boolean isAtAll,
    @JsonProperty("linkTitle") String linkTitle,
    @JsonProperty("linkMessageUrl") String linkMessageUrl,
    @JsonProperty("linkPicUrl") String linkPicUrl) {

  public DingTalkAlertConfig {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      throw new IllegalArgumentException("DingTalk webhookUrl must not be blank");
    }
    if (securityType == null || securityType.isBlank()) {
      securityType = "SIGN";
    }
    if (!"KEYWORD".equals(securityType) && !"SIGN".equals(securityType) && !"IP".equals(securityType)) {
      throw new IllegalArgumentException(
          "DingTalk securityType must be 'KEYWORD', 'SIGN' or 'IP', got: " + securityType);
    }
    if ("SIGN".equals(securityType) && (secret == null || secret.isBlank())) {
      throw new IllegalArgumentException(
          "DingTalk secret is required when securityType is 'SIGN'");
    }
    if ("KEYWORD".equals(securityType) && (keywords == null || keywords.isEmpty())) {
      throw new IllegalArgumentException(
          "DingTalk keywords is required when securityType is 'KEYWORD'");
    }
    if (msgType == null || msgType.isBlank()) {
      msgType = "markdown";
    }
    if (!"text".equals(msgType) && !"markdown".equals(msgType) && !"link".equals(msgType)) {
      throw new IllegalArgumentException(
          "DingTalk msgType must be 'text', 'markdown' or 'link', got: " + msgType);
    }
    if ("link".equals(msgType)) {
      if (linkTitle == null || linkTitle.isBlank()) {
        throw new IllegalArgumentException(
            "DingTalk linkTitle is required when msgType is 'link'");
      }
      if (linkMessageUrl == null || linkMessageUrl.isBlank()) {
        throw new IllegalArgumentException(
            "DingTalk linkMessageUrl is required when msgType is 'link'");
      }
    }
  }

  /** Parse configuration from JSON string. */
  public static DingTalkAlertConfig parse(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      throw new IllegalArgumentException("DingTalk config JSON must not be blank");
    }
    try {
      return DingTalkConfigMapper.parse(configJson);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse DingTalk config JSON: " + e.getMessage(), e);
    }
  }

  /** Whether signing secret is configured and security type is SIGN. */
  public boolean hasSecret() {
    return "SIGN".equals(securityType) && secret != null && !secret.isBlank();
  }

  /** Normalized message type. */
  public String normalizedMsgType() {
    return msgType != null ? msgType : "markdown";
  }

  /** Whether any @mention is configured. */
  public boolean hasAt() {
    return isAtAll != null && isAtAll
        || (atMobiles != null && !atMobiles.isEmpty())
        || (atUserIds != null && !atUserIds.isEmpty());
  }

  /** Whether keyword security is configured. */
  public boolean hasKeywords() {
    return "KEYWORD".equals(securityType) && keywords != null && !keywords.isEmpty();
  }

  /** Get the first keyword for embedding in messages, or default test keyword. */
  public String effectiveKeyword() {
    if (hasKeywords()) {
      return keywords.getFirst();
    }
    return "Yak Ops";
  }
}

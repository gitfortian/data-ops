package io.yak.ops.business.lifecycle.preview;

import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 预览确认令牌(design 3.3):HMAC 签名 + 5 分钟时间窗,防"预览 A 却下发 B"。
 * 密钥随实例生成:重启后旧令牌失效,用户重新预览即可,可接受。
 */
@Component
public class ConfirmTokenService {

  public static final long WINDOW_SECONDS = 300;

  private final byte[] secret;

  public ConfirmTokenService() {
    byte[] seed = new byte[32];
    new java.security.SecureRandom().nextBytes(seed);
    this.secret = seed;
  }

  public String issue(Long projectId, List<Long> modelIds) {
    long epochSecond = Instant.now().getEpochSecond();
    String payload = projectId + ":" + canonical(modelIds) + ":" + epochSecond;
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString((payload + ":" + sign(payload)).getBytes(StandardCharsets.UTF_8));
  }

  /** 校验失败抛 47009(过期/篡改/模型集不一致),调用方须重新预览。 */
  public void validate(Long projectId, List<Long> modelIds, String token) {
    if (token == null || token.isBlank()) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "缺少预览确认令牌");
    }
    String payload;
    String signature;
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      int split = decoded.lastIndexOf(':');
      payload = decoded.substring(0, split);
      signature = decoded.substring(split + 1);
    } catch (RuntimeException e) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "令牌格式无效");
    }
    if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8))) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "令牌校验失败");
    }
    String[] parts = payload.split(":");
    if (parts.length != 3
        || !parts[0].equals(String.valueOf(projectId))
        || !parts[1].equals(canonical(modelIds))) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "令牌与本次下发模型不一致");
    }
    long issuedAt;
    try {
      issuedAt = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "令牌时间戳无效");
    }
    if (Instant.now().getEpochSecond() - issuedAt > WINDOW_SECONDS) {
      throw new LifecycleException(LifecycleErrorCode.CONFIRM_EXPIRED, "超过 5 分钟有效期");
    }
  }

  private static String canonical(List<Long> modelIds) {
    return modelIds.stream().sorted().map(String::valueOf).reduce("",
        (a, b) -> a.isEmpty() ? b : a + "," + b);
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }
}

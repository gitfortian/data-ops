package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 上架预检令牌(对齐 lifecycle 预览确认 token 先例):HMAC 签名 + 5 分钟时间窗,
 * 绑定的资产集合与本次 publish 必须一致,防"预检 A 却上架 B"。
 * 密钥随实例生成:重启后旧令牌失效(48005),重新预检即可。
 */
@Component
public class PrecheckTokenService {

  public static final long WINDOW_SECONDS = 300;

  private final byte[] secret;

  public PrecheckTokenService() {
    byte[] seed = new byte[32];
    new SecureRandom().nextBytes(seed);
    this.secret = seed;
  }

  public String issue(Long projectId, List<Long> assetIds) {
    long epochSecond = Instant.now().getEpochSecond();
    String payload = projectId + ":" + canonical(assetIds) + ":" + epochSecond;
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString((payload + ":" + sign(payload)).getBytes(StandardCharsets.UTF_8));
  }

  /** 校验失败统一抛 48005(缺失/篡改/集合不一致/过期)。 */
  public void validate(Long projectId, List<Long> assetIds, String token) {
    if (token == null || token.isBlank()) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "缺少预检令牌");
    }
    String payload;
    String signature;
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      int split = decoded.lastIndexOf(':');
      payload = decoded.substring(0, split);
      signature = decoded.substring(split + 1);
    } catch (RuntimeException e) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "令牌格式无效");
    }
    if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8))) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "令牌校验失败");
    }
    String[] parts = payload.split(":");
    if (parts.length != 3
        || !parts[0].equals(String.valueOf(projectId))
        || !parts[1].equals(canonical(assetIds))) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "令牌与本次上架资产不一致");
    }
    long issuedAt;
    try {
      issuedAt = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "令牌时间戳无效");
    }
    if (Instant.now().getEpochSecond() - issuedAt > WINDOW_SECONDS) {
      throw new AssetException(AssetErrorCode.PRECHECK_TOKEN_INVALID, "超过 5 分钟有效期");
    }
  }

  private static String canonical(List<Long> assetIds) {
    return assetIds.stream().sorted().map(String::valueOf)
        .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
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

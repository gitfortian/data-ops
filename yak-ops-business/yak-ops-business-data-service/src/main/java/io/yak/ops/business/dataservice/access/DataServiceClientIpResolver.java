package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the external client IP without blindly trusting spoofable forwarding headers.
 *
 * <p>Forwarded headers are considered only when the direct peer belongs to the explicitly
 * configured trusted-proxy CIDRs. The X-Forwarded-For chain is walked from right to left until the
 * first untrusted hop, which prevents a caller from winning by prepending a forged address.</p>
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataServiceClientIpResolver {
  private static final int MAX_FORWARDED_HOPS = 32;
  private final List<String> trustedProxyNetworks;

  public DataServiceClientIpResolver(
      @Value("${yak.data-service.access.trusted-proxies:}") String trustedProxies) {
    this.trustedProxyNetworks = parseTrustedProxies(trustedProxies);
  }

  public String resolve(HttpServletRequest request) {
    if (request == null) return null;
    String remote = IpNetwork.tryNormalizeAddress(request.getRemoteAddr());
    if (remote == null || !isTrustedProxy(remote)) return remote;

    String forwardedHeader = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedHeader)) {
      List<String> forwarded = parseForwardedFor(forwardedHeader);
      if (forwarded.isEmpty()) return remote;
      String current = remote;
      for (int index = forwarded.size() - 1; index >= 0 && isTrustedProxy(current); index--) {
        current = forwarded.get(index);
      }
      return current;
    }

    String realIp = IpNetwork.tryNormalizeAddress(request.getHeader("X-Real-IP"));
    return realIp == null ? remote : realIp;
  }

  private boolean isTrustedProxy(String address) {
    if (address == null) return false;
    return trustedProxyNetworks.stream().anyMatch(network -> IpNetwork.contains(network, address));
  }

  private List<String> parseTrustedProxies(String raw) {
    if (!StringUtils.hasText(raw)) return List.of();
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .map(IpNetwork::normalizeNetwork)
        .toList();
  }

  private List<String> parseForwardedFor(String raw) {
    if (!StringUtils.hasText(raw)) return List.of();
    String[] values = raw.split(",", -1);
    if (values.length == 0 || values.length > MAX_FORWARDED_HOPS) return List.of();
    List<String> result = new ArrayList<>(values.length);
    for (String value : values) {
      String normalized = IpNetwork.tryNormalizeAddress(value.trim());
      if (normalized == null) return List.of();
      result.add(normalized);
    }
    return List.copyOf(result);
  }
}

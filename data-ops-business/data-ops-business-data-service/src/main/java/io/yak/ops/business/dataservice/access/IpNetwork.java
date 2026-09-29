package io.yak.ops.business.dataservice.access;

import java.net.InetAddress;
import java.util.Arrays;

/** Strict IP-literal/CIDR parsing used by Data Service access policy and trusted-proxy handling. */
final class IpNetwork {
  private IpNetwork() {}

  static String normalizeNetwork(String raw) {
    ParsedNetwork network = parseNetwork(raw);
    byte[] normalized = Arrays.copyOf(network.address(), network.address().length);
    zeroHostBits(normalized, network.prefixLength());
    String address = canonical(normalized);
    return network.explicitPrefix() ? address + "/" + network.prefixLength() : address;
  }

  static String normalizeAddress(String raw) {
    if (raw == null || raw.isBlank() || raw.contains("/")) {
      throw new IllegalArgumentException("IP 地址格式无效：" + raw);
    }
    return canonical(parseLiteral(stripBracketsAndZone(raw.trim())));
  }

  static String tryNormalizeAddress(String raw) {
    try {
      return normalizeAddress(raw);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  static boolean contains(String rawNetwork, String rawAddress) {
    ParsedNetwork network = parseNetwork(rawNetwork);
    byte[] address = parseLiteral(stripBracketsAndZone(rawAddress == null ? "" : rawAddress.trim()));
    if (network.address().length != address.length) return false;
    int wholeBytes = network.prefixLength() / 8;
    int remainder = network.prefixLength() % 8;
    for (int i = 0; i < wholeBytes; i++) {
      if (network.address()[i] != address[i]) return false;
    }
    if (remainder == 0) return true;
    int mask = (0xff << (8 - remainder)) & 0xff;
    return (network.address()[wholeBytes] & mask) == (address[wholeBytes] & mask);
  }

  private static ParsedNetwork parseNetwork(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("IP/CIDR 不能为空");
    String value = raw.trim();
    int slash = value.indexOf('/');
    if (slash >= 0 && slash != value.lastIndexOf('/')) {
      throw new IllegalArgumentException("IP/CIDR 格式无效：" + raw);
    }
    String addressPart = slash < 0 ? value : value.substring(0, slash).trim();
    byte[] address = parseLiteral(stripBracketsAndZone(addressPart));
    int maxPrefix = address.length * 8;
    int prefix = maxPrefix;
    if (slash >= 0) {
      String prefixPart = value.substring(slash + 1).trim();
      try {
        prefix = Integer.parseInt(prefixPart);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("CIDR 前缀无效：" + raw, exception);
      }
      if (prefix < 0 || prefix > maxPrefix) {
        throw new IllegalArgumentException("CIDR 前缀必须在 0~" + maxPrefix + " 之间：" + raw);
      }
    }
    byte[] network = Arrays.copyOf(address, address.length);
    zeroHostBits(network, prefix);
    return new ParsedNetwork(network, prefix, slash >= 0);
  }

  private static byte[] parseLiteral(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("IP 地址不能为空");
    if (!value.matches("[0-9A-Fa-f:.]+")) {
      throw new IllegalArgumentException("仅支持 IPv4/IPv6 字面量：" + value);
    }
    if (!value.contains(":")) return parseIpv4(value);
    try {
      byte[] bytes = InetAddress.getByName(value).getAddress();
      if (bytes.length != 16 && bytes.length != 4) {
        throw new IllegalArgumentException("IP 地址格式无效：" + value);
      }
      return bytes;
    } catch (Exception exception) {
      throw new IllegalArgumentException("IP 地址格式无效：" + value, exception);
    }
  }

  private static byte[] parseIpv4(String value) {
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4) throw new IllegalArgumentException("IPv4 地址格式无效：" + value);
    byte[] bytes = new byte[4];
    for (int i = 0; i < parts.length; i++) {
      if (!parts[i].matches("\\d{1,3}")) {
        throw new IllegalArgumentException("IPv4 地址格式无效：" + value);
      }
      int octet = Integer.parseInt(parts[i]);
      if (octet < 0 || octet > 255) throw new IllegalArgumentException("IPv4 地址格式无效：" + value);
      bytes[i] = (byte) octet;
    }
    return bytes;
  }

  private static String stripBracketsAndZone(String value) {
    String result = value == null ? "" : value.trim();
    if (result.startsWith("[") && result.endsWith("]")) {
      result = result.substring(1, result.length() - 1);
    }
    int zone = result.indexOf('%');
    return zone < 0 ? result : result.substring(0, zone);
  }

  private static void zeroHostBits(byte[] address, int prefix) {
    int wholeBytes = prefix / 8;
    int remainder = prefix % 8;
    if (wholeBytes < address.length && remainder > 0) {
      int mask = (0xff << (8 - remainder)) & 0xff;
      address[wholeBytes] = (byte) (address[wholeBytes] & mask);
      wholeBytes++;
    }
    for (int i = wholeBytes; i < address.length; i++) address[i] = 0;
  }

  private static String canonical(byte[] address) {
    try {
      return InetAddress.getByAddress(address).getHostAddress();
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法规范化 IP 地址", exception);
    }
  }

  private record ParsedNetwork(byte[] address, int prefixLength, boolean explicitPrefix) {}
}

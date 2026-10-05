package io.yak.ops.business.agent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Turn-local evidence handles. Business truth stays at the producing source. */
public final class GovernanceEvidenceLedger {
  private static final int MAX_EVIDENCE = 40;
  private static final Pattern CITATION = Pattern.compile("\\[(E[A-Z0-9_]{1,64})\\]");
  private final List<Evidence> entries = new ArrayList<>();
  private final java.util.Map<String, java.util.Map<String, String>> scalarFacts = new java.util.HashMap<>();

  public synchronized void recordFacts(String id, java.util.Map<String, String> facts) {
    if (entries.stream().noneMatch(e -> e.id().equals(id) && "OK".equals(e.status()))) return;
    scalarFacts.put(id, java.util.Map.copyOf(facts));
  }

  public synchronized GovernanceVerifiedFact verifyFact(String id, String field) {
    String value = scalarFacts.getOrDefault(id, java.util.Map.of()).get(field);
    if (value == null) throw new IllegalArgumentException("事实字段不在本轮可读证据中");
    return new GovernanceVerifiedFact(id, field, value);
  }

  public record Evidence(String id, String owner, String reference, String status,
      Instant observedAt, String sourceUpdatedAt, String path) {}

  public synchronized Evidence register(String owner, String reference, String status,
      String sourceUpdatedAt, String path) {
    if (entries.size() >= MAX_EVIDENCE) throw new IllegalStateException("本轮证据数量已达上限，请缩小问题范围");
    if (path == null || !path.startsWith("/") || path.startsWith("//")) {
      throw new IllegalArgumentException("证据回链必须是平台内部路径");
    }
    Evidence evidence = new Evidence("E" + UUID.randomUUID().toString().replace("-", "")
        .substring(0, 8).toUpperCase(java.util.Locale.ROOT), owner, reference, status,
        Instant.now(), sourceUpdatedAt == null ? "unknown" : sourceUpdatedAt, path);
    entries.add(evidence);
    return evidence;
  }

  public synchronized List<Evidence> entries() { return List.copyOf(entries); }

  public static boolean mentionsEvidence(String text) {
    return text != null && (CITATION.matcher(text).find() || text.contains("```yak-"));
  }

  public synchronized boolean hasValidCitations(String text) {
    var matcher = CITATION.matcher(text == null ? "" : text);
    boolean readable = false;
    while (matcher.find()) {
      String id = matcher.group(1);
      var evidence = entries.stream().filter(e -> e.id().equals(id)).findFirst();
      if (evidence.isEmpty()) return false;
      if ("OK".equals(evidence.get().status()) || "EMPTY".equals(evidence.get().status())) readable = true;
    }
    return readable;
  }

  public synchronized boolean containsGovernanceEvidence() {
    return entries.stream().anyMatch(e -> !"DATASET".equals(e.owner()));
  }

  /** Reject unknown citations; publish links exclusively from this server-owned registry. */
  public synchronized String validateAnswer(String answer) {
    var readable = entries.stream().filter(e -> "OK".equals(e.status()) || "EMPTY".equals(e.status())).toList();
    String body = answer == null ? "" : answer;
    var matcher = CITATION.matcher(body);
    boolean unknownCitation = false;
    while (matcher.find()) {
      String id = matcher.group(1);
      if (entries.stream().noneMatch(e -> e.id().equals(id))) {
        body = "回答包含本轮不存在的证据引用，未发布该解读。请缩小问题范围后重试。";
        unknownCitation = true;
        break;
      }
    }
    if (readable.isEmpty()) {
      body = "当前没有可读取的治理证据，无法判断对象是否健康或合规。请检查权限及源域可用性。";
    } else if (!unknownCitation && !hasValidCitations(body)) {
      body = "模型回答未引用本轮证据，未发布该解读。可从下方证据回链核验后重试。";
    }
    // Source descriptions and model text do not control navigation or HTML rendering.
    body = body.replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
        .replaceAll("(?s)<[^>]*>", "")
        .replaceAll("(?i)(?:https?://|javascript:|data:)[^\\s]+", "[未验证链接]");
    StringBuilder result = new StringBuilder(body).append("\n\n本轮证据（源更新时间 unknown 表示源域未提供）：\n");
    for (Evidence evidence : entries) {
      result.append("- [").append(evidence.id()).append("](").append(evidence.path()).append(")")
          .append(" · ").append(evidence.owner()).append(" · ").append(evidence.status())
          .append(" · ").append(evidence.reference().replaceAll("[\\[\\]()< >\\r\\n]", ""))
          .append(" · 读取 ").append(evidence.observedAt())
          .append(" · 源更新 ").append(evidence.sourceUpdatedAt()).append('\n');
    }
    return result.toString();
  }
}

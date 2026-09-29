package io.yak.ops.boot.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Web 审计兜底行为参数（票 02/03）：exclude-paths 追加排除；redact-fields 追加脱敏敏感词。 */
@ConfigurationProperties(prefix = "yak.audit.web")
public class AuditWebProperties {

  private java.util.List<String> excludePaths = java.util.List.of("/api/v1/audit/**");

  private java.util.List<String> redactFields = java.util.List.of();

  public java.util.List<String> getExcludePaths() {
    return excludePaths;
  }

  public void setExcludePaths(java.util.List<String> excludePaths) {
    this.excludePaths = excludePaths;
  }

  public java.util.List<String> getRedactFields() {
    return redactFields;
  }

  public void setRedactFields(java.util.List<String> redactFields) {
    this.redactFields = redactFields;
  }
}

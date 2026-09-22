package io.yak.ops.business.digitalscreen.controller.v1.vo;

import java.time.Instant;
import java.util.Map;

public final class DigitalScreenViews {

  private DigitalScreenViews() {
  }

  public record DigitalScreenVO(
      String id,
      String name,
      String description,
      String templateId,
      int templateVersion,
      String status,
      Map<String, Object> bindings,
      long revision,
      Long publishedRevision,
      Integer publishedVersionNo,
      boolean hasUnpublishedChanges,
      Instant publishedTime,
      Instant createTime,
      Instant updateTime) {
  }

  public record DigitalScreenVersionSummaryVO(
      String id,
      int versionNo,
      long sourceRevision,
      String name,
      Instant publishedTime,
      boolean current) {
  }

  public record DigitalScreenVersionVO(
      String id,
      String screenId,
      int versionNo,
      long sourceRevision,
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings,
      Instant publishedTime,
      Instant createTime) {
  }
}

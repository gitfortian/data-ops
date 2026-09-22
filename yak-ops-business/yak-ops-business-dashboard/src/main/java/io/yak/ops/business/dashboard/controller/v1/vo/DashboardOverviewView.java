package io.yak.ops.business.dashboard.controller.v1.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** HTTP view for the bounded Dashboard overview contract. */
public record DashboardOverviewView(
    long dashboardCount,
    long publishedDashboardCount,
    List<Item> recentDashboards) {

  public record Item(
      @JsonSerialize(using = ToStringSerializer.class) long id,
      String name,
      String description,
      @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
      int currentVersionNo,
      @JsonSerialize(using = ToStringSerializer.class) Long publishedVersionId,
      int publishedVersionNo,
      Instant publishedTime,
      Instant createTime,
      Instant updateTime) {}
}

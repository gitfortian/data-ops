package io.yak.ops.business.dashboard.controller.v1.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterOperator;
import io.yak.ops.business.dashboard.domain.DashboardInteractionEvent;
import java.time.Instant;
import java.util.List;

/** Dashboard HTTP 输出模型。 */
public final class DashboardViews {

    private DashboardViews() {
    }

    public record Dashboard(
            @JsonSerialize(using = ToStringSerializer.class) long id,
            String name,
            String description,
            @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
            int currentVersionNo,
            @JsonSerialize(using = ToStringSerializer.class) Long publishedVersionId,
            int publishedVersionNo,
            Instant publishedTime,
            Instant createTime,
            Instant updateTime) {
    }

    public record Version(
            @JsonSerialize(using = ToStringSerializer.class) long id,
            @JsonSerialize(using = ToStringSerializer.class) long dashboardId,
            int versionNo,
            String name,
            String description,
            @JsonSerialize(using = ToStringSerializer.class) Long activeDatasetId,
            Instant createTime) {
    }

    public record Widget(
            @JsonSerialize(using = ToStringSerializer.class) long id,
            @JsonSerialize(using = ToStringSerializer.class) long dashboardVersionId,
            String widgetKey,
            @JsonSerialize(using = ToStringSerializer.class) Long analysisId,
            String title,
            Object inlineAnalysis,
            int x,
            int y,
            int w,
            int h,
            Integer minW,
            Integer minH,
            int sortOrder) {
    }

    public record GlobalFilterBinding(
            String widgetKey,
            String fieldId,
            int sortOrder) {
    }

    public record GlobalFilter(
            String filterKey,
            String name,
            DashboardGlobalFilterOperator operator,
            Object defaultValue,
            List<GlobalFilterBinding> bindings,
            int sortOrder) {
    }

    public record Interaction(
            String interactionKey,
            DashboardInteractionEvent event,
            String sourceWidgetKey,
            String sourceFieldId,
            String targetFilterKey,
            int sortOrder) {
    }

    public record Detail(
            Dashboard dashboard,
            Version currentVersion,
            Object theme,
            List<Version> versions,
            List<Widget> widgets,
            List<GlobalFilter> globalFilters,
            List<Interaction> interactions) {
    }

    public record VersionDetail(
            Dashboard dashboard,
            Version version,
            Object theme,
            List<Widget> widgets,
            List<GlobalFilter> globalFilters,
            List<Interaction> interactions) {
    }
}

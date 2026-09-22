package io.yak.ops.business.dashboard.controller.v1.dto;

import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterOperator;
import io.yak.ops.business.dashboard.domain.DashboardInteractionEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Dashboard 保存接口输入契约。 */
public record SaveDashboardRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Min(1) Long activeDatasetId,
        Object theme,
        @Size(max = 200) List<@Valid WidgetRequest> widgets,
        @Size(max = 20) List<@Valid GlobalFilterRequest> globalFilters,
        @Size(max = 100) List<@Valid InteractionRequest> interactions) {

    public record WidgetRequest(
            @NotBlank @Size(max = 64) String widgetKey,
            @Min(1) Long analysisId,
            @Size(max = 200) String title,
            Object inlineAnalysis,
            @Min(0) @Max(23) int x,
            @Min(0) int y,
            @Min(1) @Max(24) int w,
            @Min(1) @Max(60) int h,
            @Min(1) @Max(24) Integer minW,
            @Min(1) @Max(60) Integer minH) {
    }

    public record GlobalFilterRequest(
            @NotBlank @Size(max = 64) String filterKey,
            @NotBlank @Size(max = 200) String name,
            @NotNull DashboardGlobalFilterOperator operator,
            Object defaultValue,
            @Size(max = 200) List<@Valid FilterBindingRequest> bindings) {
    }

    public record FilterBindingRequest(
            @NotBlank @Size(max = 64) String widgetKey,
            @NotBlank @Size(max = 64) String fieldId) {
    }

    public record InteractionRequest(
            @NotBlank @Size(max = 64) String interactionKey,
            @NotNull DashboardInteractionEvent event,
            @NotBlank @Size(max = 64) String sourceWidgetKey,
            @NotBlank @Size(max = 64) String sourceFieldId,
            @NotBlank @Size(max = 64) String targetFilterKey) {
    }
}

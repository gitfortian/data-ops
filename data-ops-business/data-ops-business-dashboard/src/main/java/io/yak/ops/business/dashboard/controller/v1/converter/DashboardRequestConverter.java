package io.yak.ops.business.dashboard.controller.v1.converter;

import io.yak.ops.business.dashboard.controller.v1.dto.SaveDashboardRequest;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import io.yak.ops.business.dashboard.domain.InteractionSpec;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/** HTTP DTO -> Dashboard 应用命令转换。 */
@Component
public class DashboardRequestConverter {

    public DashboardDraft toDraft(SaveDashboardRequest request) {
        List<WidgetSpec> widgets = request.widgets() == null
                ? List.of()
                : request.widgets().stream()
                        .map(widget -> new WidgetSpec(
                                widget.widgetKey(),
                                widget.analysisId(),
                                widget.title(),
                                widget.inlineAnalysis(),
                                widget.x(),
                                widget.y(),
                                widget.w(),
                                widget.h(),
                                widget.minW(),
                                widget.minH()))
                        .toList();

        List<GlobalFilterSpec> filters = request.globalFilters() == null
                ? List.of()
                : request.globalFilters().stream()
                        .map(filter -> new GlobalFilterSpec(
                                filter.filterKey(),
                                filter.name(),
                                filter.operator(),
                                filter.defaultValue(),
                                filter.bindings() == null
                                        ? List.of()
                                        : filter.bindings().stream()
                                                .map(binding -> new FilterBindingSpec(
                                                        binding.widgetKey(),
                                                        binding.fieldId()))
                                                .toList()))
                        .toList();

        List<InteractionSpec> interactions = request.interactions() == null
                ? List.of()
                : request.interactions().stream()
                        .map(interaction -> new InteractionSpec(
                                interaction.interactionKey(),
                                interaction.event(),
                                interaction.sourceWidgetKey(),
                                interaction.sourceFieldId(),
                                interaction.targetFilterKey()))
                        .toList();

        return new DashboardDraft(
                request.name(),
                request.description(),
                request.activeDatasetId(),
                request.theme(),
                widgets,
                filters,
                interactions);
    }
}

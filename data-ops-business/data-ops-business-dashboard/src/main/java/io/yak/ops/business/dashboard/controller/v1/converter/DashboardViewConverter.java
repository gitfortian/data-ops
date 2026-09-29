package io.yak.ops.business.dashboard.controller.v1.converter;

import io.yak.ops.business.dashboard.controller.v1.vo.DashboardViews;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterBindingSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardInteractionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

/** Dashboard Domain -> HTTP VO 纯转换。 */
@Component
public class DashboardViewConverter {

    public DashboardViews.Dashboard dashboard(DashboardAsset source) {
        return new DashboardViews.Dashboard(
                source.id(), source.name(), source.description(), source.currentVersionId(),
                source.currentVersionNo(), source.publishedVersionId(), source.publishedVersionNo(),
                source.publishedTime(), source.createTime(), source.updateTime());
    }

    public DashboardViews.Version version(DashboardVersion source) {
        if (source == null) return null;
        return new DashboardViews.Version(
                source.id(), source.dashboardId(), source.versionNo(), source.name(),
                source.description(), source.activeDatasetId(), source.createTime());
    }

    public DashboardViews.Widget widget(DashboardWidgetSnapshot source) {
        return new DashboardViews.Widget(
                source.id(), source.dashboardVersionId(), source.widgetKey(), source.analysisId(),
                source.title(), source.inlineAnalysis(), source.x(), source.y(), source.w(), source.h(),
                source.minW(), source.minH(), source.sortOrder());
    }

    public DashboardViews.GlobalFilter globalFilter(DashboardGlobalFilterSnapshot source) {
        return new DashboardViews.GlobalFilter(
                source.filterKey(), source.name(), source.operator(), source.defaultValue(),
                source.bindings().stream().map(this::binding).toList(), source.sortOrder());
    }

    public DashboardViews.Interaction interaction(DashboardInteractionSnapshot source) {
        return new DashboardViews.Interaction(
                source.interactionKey(), source.event(), source.sourceWidgetKey(),
                source.sourceFieldId(), source.targetFilterKey(), source.sortOrder());
    }

    public DashboardViews.Detail detail(DashboardDetail source) {
        return new DashboardViews.Detail(
                dashboard(source.dashboard()), version(source.currentVersion()), source.theme(),
                source.versions().stream().map(this::version).toList(),
                source.widgets().stream().map(this::widget).toList(),
                source.globalFilters().stream().map(this::globalFilter).toList(),
                source.interactions().stream().map(this::interaction).toList());
    }

    public DashboardViews.VersionDetail versionDetail(DashboardVersionDetail source) {
        return new DashboardViews.VersionDetail(
                dashboard(source.dashboard()), version(source.version()), source.theme(),
                source.widgets().stream().map(this::widget).toList(),
                source.globalFilters().stream().map(this::globalFilter).toList(),
                source.interactions().stream().map(this::interaction).toList());
    }

    public List<DashboardViews.Dashboard> dashboards(List<DashboardAsset> source) {
        return source.stream().map(this::dashboard).toList();
    }

    public List<DashboardViews.Version> versions(List<DashboardVersion> source) {
        return source.stream().map(this::version).toList();
    }

    private DashboardViews.GlobalFilterBinding binding(DashboardGlobalFilterBindingSnapshot source) {
        return new DashboardViews.GlobalFilterBinding(
                source.widgetKey(), source.fieldId(), source.sortOrder());
    }
}

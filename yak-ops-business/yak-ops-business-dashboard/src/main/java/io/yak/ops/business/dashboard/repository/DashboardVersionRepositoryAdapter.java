package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterBindingPO;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterPO;
import io.yak.ops.business.dashboard.dao.model.DashboardInteractionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardVersionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardWidgetPO;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterBindingSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterOperator;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardInteractionEvent;
import io.yak.ops.business.dashboard.domain.DashboardInteractionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.repository.codec.DashboardJsonCodec;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for immutable DashboardVersion aggregate snapshots. */
@Repository
@ConditionalOnDataSourceEnabled
public class DashboardVersionRepositoryAdapter implements DashboardVersionRepository {

  private final DashboardDao dao;
  private final DashboardJsonCodec json;

  public DashboardVersionRepositoryAdapter(DashboardDao dao, DashboardJsonCodec json) {
    this.dao = dao;
    this.json = json;
  }

  @Override
  public long appendVersion(long dashboardId, int versionNo, DashboardDraft draft) {
    DashboardVersionPO version = new DashboardVersionPO();
    version.setDashboardId(dashboardId);
    version.setVersionNo(versionNo);
    version.setNameSnapshot(draft.name());
    version.setDescriptionSnapshot(draft.description());
    version.setActiveDatasetId(draft.activeDatasetId());
    version.setThemeJson(json.write(draft.theme()));
    requireInserted(dao.insertVersion(version), "创建 DashboardVersion 失败");
    if (version.getId() == null) {
      throw new IllegalStateException("创建 DashboardVersion 后未返回主键");
    }

    long versionId = version.getId();
    for (int index = 0; index < draft.widgets().size(); index++) {
      var widget = draft.widgets().get(index);
      DashboardWidgetPO row = new DashboardWidgetPO();
      row.setDashboardVersionId(versionId);
      row.setWidgetKey(widget.widgetKey());
      row.setAnalysisId(widget.analysisId());
      row.setTitle(widget.title());
      row.setInlineAnalysisJson(json.write(widget.inlineAnalysis()));
      row.setGridX(widget.x());
      row.setGridY(widget.y());
      row.setGridW(widget.w());
      row.setGridH(widget.h());
      row.setMinW(widget.minW());
      row.setMinH(widget.minH());
      row.setSortOrder(index + 1);
      requireInserted(dao.insertWidget(row), "创建 DashboardWidget 失败");
    }

    for (int index = 0; index < draft.globalFilters().size(); index++) {
      var filter = draft.globalFilters().get(index);
      DashboardFilterPO row = new DashboardFilterPO();
      row.setDashboardVersionId(versionId);
      row.setFilterKey(filter.filterKey());
      row.setName(filter.name());
      row.setOperator(filter.operator().name());
      row.setDefaultValueJson(json.write(filter.defaultValue()));
      row.setSortOrder(index + 1);
      requireInserted(dao.insertFilter(row), "创建 DashboardFilter 失败");

      for (int bindingIndex = 0; bindingIndex < filter.bindings().size(); bindingIndex++) {
        var binding = filter.bindings().get(bindingIndex);
        DashboardFilterBindingPO bindingRow = new DashboardFilterBindingPO();
        bindingRow.setDashboardVersionId(versionId);
        bindingRow.setFilterKey(filter.filterKey());
        bindingRow.setWidgetKey(binding.widgetKey());
        bindingRow.setFieldId(binding.fieldId());
        bindingRow.setSortOrder(bindingIndex + 1);
        requireInserted(dao.insertFilterBinding(bindingRow), "创建 DashboardFilterBinding 失败");
      }
    }

    for (int index = 0; index < draft.interactions().size(); index++) {
      var interaction = draft.interactions().get(index);
      DashboardInteractionPO row = new DashboardInteractionPO();
      row.setDashboardVersionId(versionId);
      row.setInteractionKey(interaction.interactionKey());
      row.setEventType(interaction.event().name());
      row.setSourceWidgetKey(interaction.sourceWidgetKey());
      row.setSourceFieldId(interaction.sourceFieldId());
      row.setTargetFilterKey(interaction.targetFilterKey());
      row.setSortOrder(index + 1);
      requireInserted(dao.insertInteraction(row), "创建 DashboardInteraction 失败");
    }
    return versionId;
  }

  @Override
  public Optional<DashboardVersionSnapshot> findVersionSnapshot(long versionId) {
    return Optional.ofNullable(dao.selectVersion(versionId)).map(this::snapshot);
  }

  @Override
  public Optional<DashboardVersionSnapshot> findVersionSnapshotByNo(long dashboardId, int versionNo) {
    return Optional.ofNullable(dao.selectVersionByNo(dashboardId, versionNo)).map(this::snapshot);
  }

  @Override
  public List<DashboardVersion> listVersions(long dashboardId) {
    return dao.selectVersions(dashboardId).stream().map(this::toDomain).toList();
  }

  @Override
  public int nextVersionNo(long dashboardId) {
    return dao.selectNextVersionNo(dashboardId);
  }

  private DashboardVersionSnapshot snapshot(DashboardVersionPO version) {
    long versionId = version.getId();
    List<DashboardWidgetSnapshot> widgets = dao.selectWidgets(versionId).stream()
        .map(this::toDomain)
        .toList();
    Map<String, List<DashboardFilterBindingPO>> bindingsByFilter =
        dao.selectFilterBindings(versionId).stream()
            .collect(Collectors.groupingBy(
                DashboardFilterBindingPO::getFilterKey,
                java.util.LinkedHashMap::new,
                Collectors.toList()));
    List<DashboardGlobalFilterSnapshot> filters = dao.selectFilters(versionId).stream()
        .map(filter -> toDomain(
            filter,
            bindingsByFilter.getOrDefault(filter.getFilterKey(), List.of())))
        .toList();
    List<DashboardInteractionSnapshot> interactions = dao.selectInteractions(versionId).stream()
        .map(this::toDomain)
        .toList();
    return new DashboardVersionSnapshot(
        toDomain(version), json.read(version.getThemeJson()), widgets, filters, interactions);
  }

  private DashboardVersion toDomain(DashboardVersionPO row) {
    return new DashboardVersion(
        row.getId(),
        row.getDashboardId(),
        value(row.getVersionNo()),
        row.getNameSnapshot(),
        row.getDescriptionSnapshot(),
        row.getActiveDatasetId(),
        instant(row.getCreateTime()));
  }

  private DashboardWidgetSnapshot toDomain(DashboardWidgetPO row) {
    return new DashboardWidgetSnapshot(
        row.getId(),
        row.getDashboardVersionId(),
        row.getWidgetKey(),
        row.getAnalysisId(),
        row.getTitle(),
        json.read(row.getInlineAnalysisJson()),
        value(row.getGridX()),
        value(row.getGridY()),
        value(row.getGridW()),
        value(row.getGridH()),
        row.getMinW(),
        row.getMinH(),
        value(row.getSortOrder()));
  }

  private DashboardGlobalFilterSnapshot toDomain(
      DashboardFilterPO row,
      List<DashboardFilterBindingPO> bindings) {
    List<DashboardGlobalFilterBindingSnapshot> domainBindings = new ArrayList<>(bindings.size());
    for (DashboardFilterBindingPO binding : bindings) {
      domainBindings.add(new DashboardGlobalFilterBindingSnapshot(
          binding.getWidgetKey(), binding.getFieldId(), value(binding.getSortOrder())));
    }
    return new DashboardGlobalFilterSnapshot(
        row.getFilterKey(),
        row.getName(),
        DashboardGlobalFilterOperator.valueOf(row.getOperator()),
        json.read(row.getDefaultValueJson()),
        List.copyOf(domainBindings),
        value(row.getSortOrder()));
  }

  private DashboardInteractionSnapshot toDomain(DashboardInteractionPO row) {
    return new DashboardInteractionSnapshot(
        row.getInteractionKey(),
        DashboardInteractionEvent.valueOf(row.getEventType()),
        row.getSourceWidgetKey(),
        row.getSourceFieldId(),
        row.getTargetFilterKey(),
        value(row.getSortOrder()));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private void requireInserted(int count, String message) {
    if (count != 1) throw new IllegalStateException(message);
  }
}

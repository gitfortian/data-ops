package io.yak.ops.business.dashboard.dao;

import io.yak.ops.business.dashboard.dao.model.DashboardFilterBindingPO;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterPO;
import io.yak.ops.business.dashboard.dao.model.DashboardInteractionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.dao.model.DashboardVersionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardWidgetPO;
import java.util.List;

/** Dashboard 数据库访问边界。 */
public interface DashboardDao {

    int insertDashboard(DashboardPO dashboard);

    int insertVersion(DashboardVersionPO version);

    int insertWidget(DashboardWidgetPO widget);

    int insertFilter(DashboardFilterPO filter);

    int insertFilterBinding(DashboardFilterBindingPO binding);

    int insertInteraction(DashboardInteractionPO interaction);

    int updateCurrentVersion(
            long dashboardId,
            long versionId,
            int versionNo,
            String name,
            String description);

    int updatePublishedVersion(long dashboardId, long versionId, int versionNo);

    DashboardPO selectDashboard(long dashboardId);

    List<DashboardPO> selectDashboards();

    DashboardVersionPO selectVersion(long versionId);

    DashboardVersionPO selectVersionByNo(long dashboardId, int versionNo);

    List<DashboardVersionPO> selectVersions(long dashboardId);

    List<DashboardWidgetPO> selectWidgets(long versionId);

    List<DashboardFilterPO> selectFilters(long versionId);

    List<DashboardFilterBindingPO> selectFilterBindings(long versionId);

    List<DashboardInteractionPO> selectInteractions(long versionId);

    boolean existsWidgetByAnalysisId(long analysisId);

    int selectNextVersionNo(long dashboardId);

    int deleteDashboard(long dashboardId);
}

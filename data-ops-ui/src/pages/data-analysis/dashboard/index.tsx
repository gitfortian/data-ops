import DashboardListContent from './components/DashboardListContent';
import DashboardPageHeader from './components/DashboardPageHeader';
import RenameDashboardModal from './components/RenameDashboardModal';
import { useDashboardListPage } from './hooks/useDashboardListPage';

const DashboardListPage = () => {
  const page = useDashboardListPage();
  const hasActiveFilters = Boolean(
    page.keyword.trim() ||
      page.status !== 'all' ||
      page.timeRange !== 'all',
  );

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#f6f7f8]">
      <main className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <DashboardPageHeader
          total={page.dashboards.length}
          lifecycleCounts={page.lifecycleCounts}
          status={page.status}
          timeRange={page.timeRange}
          keyword={page.keyword}
          loading={page.loading}
          onStatusChange={page.setStatus}
          onTimeRangeChange={page.setTimeRange}
          onKeywordChange={page.setKeyword}
          onRefresh={() => void page.refresh()}
          onCreate={page.createDashboard}
        />

        <div className="my-3 h-px bg-[#f0f1f2]" />

        <DashboardListContent
          loading={page.loading}
          totalDashboards={page.dashboards.length}
          filteredTotal={page.filteredDashboards.length}
          pageItems={page.pageItems}
          currentPage={page.page}
          pageSize={page.pageSize}
          filtered={hasActiveFilters}
          deletingId={page.deletingId}
          onReset={page.resetFilters}
          onCreate={page.createDashboard}
          onOpen={page.openDashboard}
          onEdit={page.editDashboard}
          onRename={page.openRename}
          onDelete={page.removeDashboard}
          onPageChange={page.changePage}
        />
      </main>

      <RenameDashboardModal
        dashboard={page.renameTarget}
        value={page.renameValue}
        loading={page.renaming}
        onChange={page.setRenameValue}
        onCancel={page.closeRename}
        onSubmit={() => void page.renameDashboard()}
      />
    </div>
  );
};

export default DashboardListPage;

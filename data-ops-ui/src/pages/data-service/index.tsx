import { Alert, Button } from 'antd';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import DataServiceDetailNavigator from './components/DataServiceDetailNavigator';
import DataServiceMarketplaceHome from './components/DataServiceMarketplaceHome';
import DataServiceSearchResults from './components/DataServiceSearchResults';
import { useDataServiceMarketplace } from './hooks/useDataServiceMarketplace';

const DataServicePage = () => {
  const {
    services,
    loading,
    keyword,
    submittedKeyword,
    detailTarget,
    catalogIssue,
    sourceUnavailable,
    callStatsUnavailable,
    loadMarketplace,
    callsByApiId,
    runningServices,
    recommendedServices,
    hotServices,
    searchResults,
    searching,
    totalCalls,
    canObserve,
    canManage,
    canDelete,
    dataSourceName,
    changeKeyword,
    search,
    resetSearch,
    openDetail,
    closeDetail,
    deleteService,
    toggleService,
    copyEndpoint,
  } = useDataServiceMarketplace();

  return (
    <div className="min-h-[calc(100vh-64px)] bg-white">
      {catalogIssue && (
        <Alert type="error" showIcon className="mx-5 mt-4"
          message={catalogIssue === 'FORBIDDEN' ? '无权读取 API 集市' : 'API 集市读取失败'}
          description="当前 Project 的 API 列表不可确认，不能将读取失败解释为没有发布服务。"
          action={<Button onClick={() => void loadMarketplace()}>重试</Button>}
        />
      )}
      {!catalogIssue && sourceUnavailable && (
        <Alert type="warning" showIcon className="mx-5 mt-4"
          message="数据源名称暂不可用"
          description="API 列表仍可查看；来源名称使用 ID 回退，不代表数据源不存在。"
        />
      )}
      {!catalogIssue && callStatsUnavailable && (
        <Alert type="warning" showIcon className="mx-5 mt-4"
          message="最近调用统计暂不可用"
          description="不能据此判断近期没有调用；API 列表不受影响。"
        />
      )}
      {!catalogIssue && (loading ? (
        <div role="status" className="px-5 py-8 text-sm text-[#667085]">
          正在读取当前项目的 API 列表…
        </div>
      ) : searching ? (
        <DataServiceSearchResults
          keyword={keyword}
          submittedKeyword={submittedKeyword}
          loading={loading}
          records={searchResults}
          callsByApiId={callsByApiId}
          canManage={canManage}
          canDelete={canDelete}
          dataSourceName={dataSourceName}
          onKeywordChange={changeKeyword}
          onSearch={search}
          onReset={resetSearch}
          onOpen={(service) => openDetail(service)}
          onCopyEndpoint={(endpoint) => void copyEndpoint(endpoint)}
          onToggle={(service, enabled) =>
            void toggleService(service, enabled)
          }
          onDelete={deleteService}
        />
      ) : (
        <DataServiceMarketplaceHome
          keyword={keyword}
          loading={loading}
          recommendedServices={recommendedServices}
          hotServices={hotServices}
          callsByApiId={callsByApiId}
          totalServices={services.length}
          runningServices={runningServices.length}
          totalCalls={totalCalls}
          callStatsUnavailable={callStatsUnavailable}
          canObserve={canObserve}
          dataSourceName={dataSourceName}
          onKeywordChange={changeKeyword}
          onSearch={search}
          onOpen={(service) => openDetail(service)}
        />
      ))}

      <DataServiceDetailNavigator
        open={Boolean(detailTarget)}
        service={detailTarget}
        onClose={closeDetail}
      />
    </div>
  );
};


/** Destroy list, search, selected actions and pending reads on scope change. */
export default function ScopedDataServicePage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <DataServicePage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}

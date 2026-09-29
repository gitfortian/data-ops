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
      {searching ? (
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
          canObserve={canObserve}
          dataSourceName={dataSourceName}
          onKeywordChange={changeKeyword}
          onSearch={search}
          onOpen={(service) => openDetail(service)}
        />
      )}

      <DataServiceDetailNavigator
        open={Boolean(detailTarget)}
        service={detailTarget}
        onClose={closeDetail}
      />
    </div>
  );
};

export default DataServicePage;

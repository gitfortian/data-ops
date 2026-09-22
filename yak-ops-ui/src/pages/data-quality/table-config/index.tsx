import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider } from 'antd';

import DataSourceTreePane from './components/DataSourceTreePane';
import QualityTableRegistryHeader from './components/QualityTableRegistryHeader';
import RegisteredTablePanel from './components/RegisteredTablePanel';
import RegisterTableDrawer from './components/RegisterTableDrawer';
import { useQualityTableRegistryPage } from './hooks/useQualityTableRegistryPage';

const QualityTableRegistryPage = () => {
  const {
    source,
    table,
    selectDataSource,
    refresh,
    changeAssetKeyword,
    changeCandidateKeyword,
    refreshing,
  } = useQualityTableRegistryPage();

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[620px] flex-col overflow-hidden bg-white">
        <QualityTableRegistryHeader
          refreshing={refreshing}
          onRefresh={() => void refresh()}
        />

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <DataSourceTreePane
            sourceNodes={source.sourceNodes}
            treeLoading={source.treeLoading}
            selectedNodeKey={source.selectedNodeKey}
            leftWidth={source.leftWidth}
            collapsed={source.collapsed}
            onSelect={selectDataSource}
            onResizeStart={source.startResize}
            onCollapsedChange={source.setCollapsed}
          />

          <RegisteredTablePanel
            dataSourceId={source.dataSourceId}
            selectedSourceNode={source.selectedSourceNode}
            assets={table.assets}
            assetTotal={table.assetTotal}
            assetCurrent={table.assetCurrent}
            keyword={table.keyword}
            assetLoading={table.assetLoading}
            onAssetCurrentChange={table.setAssetCurrent}
            onKeywordChange={changeAssetKeyword}
            onOpenRegister={table.openRegisterDrawer}
            onOpenRuleManagement={table.openRuleManagement}
            onCreateMonitor={table.createMonitor}
          />
        </div>
      </div>

      <RegisterTableDrawer
        open={table.registerOpen}
        registering={table.registering}
        candidates={table.candidates}
        candidateTotal={table.candidateTotal}
        candidateCurrent={table.candidateCurrent}
        candidateKeyword={table.candidateKeyword}
        candidateLoading={table.candidateLoading}
        selectedCandidates={table.selectedCandidates}
        selectedCandidateKeys={table.selectedCandidateKeys}
        selectedCandidateRecords={table.selectedCandidateRecords}
        onClose={table.closeRegisterDrawer}
        onRegister={() => void table.handleRegister()}
        onCandidateCurrentChange={table.setCandidateCurrent}
        onCandidateKeywordChange={changeCandidateKeyword}
        onSelect={table.updateCandidateSelection}
        onSelectAll={table.updateAllCandidateSelection}
        onClear={table.clearCandidateSelection}
      />
    </ConfigProvider>
  );
};

export default QualityTableRegistryPage;

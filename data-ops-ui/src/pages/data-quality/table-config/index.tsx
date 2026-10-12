import { BRAND_THEME } from '@/styles/brand';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { Alert, Button, ConfigProvider } from 'antd';
import { history, useLocation } from '@umijs/max';

import DataSourceTreePane from './components/DataSourceTreePane';
import QualityTableRegistryHeader from './components/QualityTableRegistryHeader';
import RegisteredTablePanel from './components/RegisteredTablePanel';
import RegisterTableDrawer from './components/RegisterTableDrawer';
import { useQualityTableRegistryPage } from './hooks/useQualityTableRegistryPage';

const QualityTableRegistryPage = () => {
  const location = useLocation();
  const returnAssetId = new URLSearchParams(location.search).get('returnAssetId');
  const returnTarget = returnAssetId && /^\d+$/.test(returnAssetId)
    ? `/data-asset/detail/${returnAssetId}`
    : undefined;
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
          onBackToAsset={returnTarget ? () => history.push(returnTarget) : undefined}
        />

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <DataSourceTreePane
            sourceNodes={source.sourceNodes}
            treeLoading={source.treeLoading}
            sourceError={source.sourceError}
            onRetry={() => void refresh()}
            selectedNodeKey={source.selectedNodeKey}
            leftWidth={source.leftWidth}
            collapsed={source.collapsed}
            onSelect={selectDataSource}
            onResizeStart={source.startResize}
            onCollapsedChange={source.setCollapsed}
          />

          {table.assetError ? (
            <div className="min-w-0 flex-1 p-4">
              <Alert type="error" showIcon message="已注册数据表读取失败"
                description={table.assetError}
                action={<Button onClick={() => void refresh()}>按当前筛选重试</Button>} />
            </div>
          ) : <RegisteredTablePanel
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
            onUnregister={(record) => void table.unregisterTableAsset(record)}
          />}
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
        candidateError={table.candidateError}
        onRetry={() => void table.retryCandidates()}
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

export default function QualityTableRegistryScope() {
  const { currentProject } = useSecurityProject();
  const { can, permissionCodes } = usePermissionAccess();
  if (!currentProject) return <Alert type="info" showIcon message="请选择项目空间" />;
  if (!can('quality:monitor:read')) return <Alert type="info" showIcon message="暂无质量监控读取权限" />;
  return <QualityTableRegistryPage key={JSON.stringify([currentProject.id, permissionCodes])} />;
}

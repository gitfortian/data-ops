import { BRAND_THEME } from '@/styles/brand';
import { history, useIntl } from '@umijs/max';
import { ConfigProvider } from 'antd';
import { useEffect, useRef } from 'react';

import {
  datasetIdFromSearch,
  resolveDatasetDevelopmentSource,
} from './assetGovernance';
import CreateDevelopmentNodeModal from './components/CreateDevelopmentNodeModal';
import CreateDirectoryModal from './components/CreateDirectoryModal';
import DeleteDevelopmentResourceModal from './components/DeleteDevelopmentResourceModal';
import DevelopmentEditorWorkspace from './components/DevelopmentEditorWorkspace';
import DevelopmentTreePane from './components/DevelopmentTreePane';
import MoveResourceModal from './components/MoveResourceModal';
import RenameResourceModal from './components/RenameResourceModal';
import {
  ResourceInvalidatedNotice,
  WorkspaceLoadFailureState,
} from './components/WorkspaceStateFeedback';
import { developmentNodeIdFromSearch } from './executions/executionExperience';
import { useDataDevelopmentPage } from './hooks/useDataDevelopmentPage';

export default function DataDevelopmentPage() {
  const intl = useIntl();
  const page = useDataDevelopmentPage();
  const deepLinkAppliedRef = useRef(false);
  const datasetResolveInFlightRef = useRef(false);
  const directoryLabel = intl.formatMessage({ id: 'pages.dataDevelopment.common.directory' });
  const nodeLabel = intl.formatMessage({ id: 'pages.dataDevelopment.common.node' });

  useEffect(() => {
    if (deepLinkAppliedRef.current || page.treeLoading || page.treeFailure) return;

    const nodeId = developmentNodeIdFromSearch(window.location.search);
    if (nodeId) {
      if (!page.nodes.some((node) => String(node.id) === nodeId)) return;
      deepLinkAppliedRef.current = true;
      page.focusNode(nodeId);
      return;
    }

    const datasetId = datasetIdFromSearch(window.location.search);
    if (!datasetId) {
      deepLinkAppliedRef.current = true;
      return;
    }
    if (datasetResolveInFlightRef.current) return;

    datasetResolveInFlightRef.current = true;
    void resolveDatasetDevelopmentSource(datasetId)
      .then((source) => {
        const resolvedNodeId = source.state === 'FOUND'
          ? String(source.developmentNodeId || '')
          : '';
        if (
          resolvedNodeId
          && page.nodes.some((node) => String(node.id) === resolvedNodeId)
        ) {
          const params = new URLSearchParams(window.location.search);
          params.set('nodeId', resolvedNodeId);
          if (source.currentDatasetVersionNo && source.currentDatasetVersionNo > 0) {
            params.set('datasetVersionNo', String(source.currentDatasetVersionNo));
          } else {
            params.delete('datasetVersionNo');
          }
          history.replace(`${window.location.pathname}?${params.toString()}`);
          page.focusNode(resolvedNodeId);
        }
        deepLinkAppliedRef.current = true;
      })
      .catch(() => {
        // Cross-domain provenance failure must not break the Development workspace itself.
        deepLinkAppliedRef.current = true;
      })
      .finally(() => {
        datasetResolveInFlightRef.current = false;
      });
  }, [page.focusNode, page.nodes, page.treeFailure, page.treeLoading]);

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex min-h-[640px] flex-col bg-[#f5f5f6] md:h-[calc(100vh-64px)] md:overflow-hidden">
        {page.invalidatedResource && !page.treeFailure ? (
          <ResourceInvalidatedNotice
            resourceType={page.invalidatedResource.resourceType}
            onRefresh={() => void page.loadTree()}
            onDismiss={page.dismissInvalidatedResource}
          />
        ) : null}

        <div className="flex min-h-0 flex-1 flex-col overflow-hidden border border-[#e4e7ec] bg-white md:flex-row">
          {page.treeFailure ? (
            <WorkspaceLoadFailureState
              failure={page.treeFailure}
              loading={page.treeLoading}
              onRetry={() => void page.loadTree()}
            />
          ) : (
            <>
              <div className="flex h-[240px] shrink-0 overflow-hidden md:h-full max-md:[&>aside]:!w-full max-md:[&>aside>div]:!w-full max-md:[&>[role=separator]]:hidden">
              <DevelopmentTreePane
                treeData={page.treeData}
                treeLoading={page.treeLoading}
                selectedNodeKey={page.selectedNodeKey}
                searchValue={page.treeKeyword}
                leftWidth={page.treeWidth}
                collapsed={page.treeCollapsed}
                onCreateDirectory={page.openCreateDirectory}
                onCreateNode={page.openCreateNode}
                onResourceAction={page.handleResourceAction}
                onSearchChange={page.setTreeKeyword}
                onResizeStart={page.handleResizeStart}
                onCollapsedChange={page.setTreeCollapsed}
                onSelect={page.selectTreeNodes}
              />
              </div>

              <div className="flex min-h-[600px] min-w-0 flex-1 md:min-h-0">
              <DevelopmentEditorWorkspace
                nodes={page.nodes}
                directories={page.directories}
                selectedNodeId={page.selectedResourceNodeId}
                onNodeFocus={page.focusNode}
                onCreateNode={page.openCreateNode}
                onNodesChanged={page.loadTree}
              />
              </div>
            </>
          )}
        </div>

        <CreateDevelopmentNodeModal
          open={page.createNodeOpen}
          type={page.createNodeType}
          sqlDialect={page.createSqlDialect}
          directories={page.directories}
          loading={page.nodeSaving}
          defaultDirectoryId={page.directoryIdForSelection}
          onCancel={page.closeCreateNode}
          onNext={(type, directoryId, name) =>
            void page.submitNode(type, directoryId, name, page.createSqlDialect)
          }
        />

        <CreateDirectoryModal
          open={page.createDirectoryOpen}
          directories={page.directories}
          defaultParentId={page.directoryIdForSelection}
          loading={page.directorySaving}
          onCancel={page.closeCreateDirectory}
          onSubmit={(parentId, name) =>
            void page.submitDirectory(parentId, name)
          }
        />

        <RenameResourceModal
          open={Boolean(page.renameTarget)}
          resourceLabel={
            page.renameTarget?.nodeType === 'directory' ? directoryLabel : nodeLabel
          }
          initialName={page.renameTarget?.title || ''}
          loading={page.renameSaving}
          onCancel={page.closeRename}
          onSubmit={(name) => void page.submitRename(name)}
        />

        <DeleteDevelopmentResourceModal
          target={page.deleteTarget}
          loading={page.deleteSaving}
          onCancel={page.closeDelete}
          onConfirm={() => void page.submitDelete()}
        />

        <MoveResourceModal
          open={Boolean(page.moveTarget)}
          resourceLabel={
            page.moveTarget?.nodeType === 'directory' ? directoryLabel : nodeLabel
          }
          resourceName={page.moveTarget?.title || ''}
          directories={page.directories}
          resourceId={page.moveTarget?.resourceId || ''}
          resourceType={
            page.moveTarget?.nodeType === 'directory' ? 'directory' : 'node'
          }
          loading={page.moveSaving}
          onCancel={page.closeMove}
          onConfirm={(targetDirectoryId) =>
            void page.submitMove(targetDirectoryId)
          }
        />
      </div>
    </ConfigProvider>
  );
}

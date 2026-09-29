import { YakButton } from '@/components/ui';
import { history, useParams } from '@umijs/max';
import { DigitalScreenEditorView } from './components/DigitalScreenEditorView';
import { useDigitalScreenEditor } from './hooks/useDigitalScreenEditor';

export default function DigitalScreenEditorPage() {
  const { id } = useParams<{ id: string }>();
  const editor = useDigitalScreenEditor(id);

  if (editor.isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#f4f5f6] text-[13px] text-[#98a2b3]">
        正在加载数字化大屏...
      </div>
    );
  }

  if (!editor.screen) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-[#f4f5f6] text-[#667085]">
        <div className="text-[14px]">数字化大屏不存在</div>
        <YakButton type="link" onClick={() => history.push('/digital-screen')}>返回大屏列表</YakButton>
      </div>
    );
  }

  return (
    <DigitalScreenEditorView
      screen={editor.screen}
      name={editor.name}
      description={editor.description}
      bindings={editor.bindings}
      selectedComponentId={editor.selectedComponentId}
      datasets={editor.datasets}
      datasetsError={editor.datasetsError}
      versions={editor.versions}
      isDatasetsLoading={editor.isDatasetsLoading}
      isVersionsLoading={editor.isVersionsLoading}
      isVersionsOpen={editor.isVersionsOpen}
      isSaving={editor.isSaving}
      isPublishing={editor.isPublishing}
      isOfflining={editor.isOfflining}
      rollingBackVersionNo={editor.rollingBackVersionNo}
      isDirty={editor.isDirty}
      template={editor.template}
      selectedComponent={editor.selectedComponent}
      runtime={editor.runtime}
      bindableCount={editor.bindableCount}
      isSelectedQuerying={editor.isSelectedQuerying}
      selectedQueryError={editor.selectedQueryError}
      onBack={() => history.push('/digital-screen')}
      onPreview={() => history.push(`/digital-screen/${editor.screen?.id}`)}
      onNameChange={editor.setName}
      onDescriptionChange={editor.setDescription}
      onComponentSelect={editor.setSelectedComponentId}
      onSave={() => void editor.saveScreen()}
      onPublish={() => void editor.publishScreen()}
      onOffline={() => void editor.offlineScreen()}
      onOpenVersions={editor.openVersions}
      onCloseVersions={() => editor.setIsVersionsOpen(false)}
      onRollbackVersion={(versionNo) => void editor.rollbackVersion(versionNo)}
      onBindingChange={editor.updateSelectedBinding}
    />
  );
}

import type { PublishedDataset } from '@/components/analysis/model';
import {
  ScreenRenderer,
  type ScreenComponent,
  type ScreenDataOverrides,
  type ScreenTemplate,
} from '@/components/screen-engine';
import { YakButton } from '@/components/ui';
import type {
  DigitalScreenBindings,
  DigitalScreenComponentBinding,
  DigitalScreenInstance,
  DigitalScreenVersionSummary,
} from '@/services/digital-screen';
import { Input, Popconfirm } from 'antd';
import { ArrowLeft, CloudOff, Database, Eye, History, Save, Send } from 'lucide-react';
import { DataBindingPanel } from './DataBindingPanel';
import { VersionHistoryDrawer } from './VersionHistoryDrawer';

interface ScreenRuntimeViewModel {
  data: ScreenDataOverrides;
  loadingIds: string[];
  errors: Record<string, string>;
  loadingCount: number;
  boundCount: number;
}

interface DigitalScreenEditorViewProps {
  screen: DigitalScreenInstance;
  name: string;
  description: string;
  bindings: DigitalScreenBindings;
  selectedComponentId?: string;
  datasets: PublishedDataset[];
  datasetsError: string;
  versions: DigitalScreenVersionSummary[];
  isDatasetsLoading: boolean;
  isVersionsLoading: boolean;
  isVersionsOpen: boolean;
  isSaving: boolean;
  isPublishing: boolean;
  isOfflining: boolean;
  rollingBackVersionNo?: number;
  isDirty: boolean;
  template?: ScreenTemplate;
  selectedComponent?: ScreenComponent;
  runtime: ScreenRuntimeViewModel;
  bindableCount: number;
  isSelectedQuerying: boolean;
  selectedQueryError?: string;
  onBack: () => void;
  onPreview: () => void;
  onNameChange: (name: string) => void;
  onDescriptionChange: (description: string) => void;
  onComponentSelect: (componentId: string) => void;
  onSave: () => void;
  onPublish: () => void;
  onOffline: () => void;
  onOpenVersions: () => void;
  onCloseVersions: () => void;
  onRollbackVersion: (versionNo: number) => void;
  onBindingChange: (binding?: DigitalScreenComponentBinding) => void;
}

export function DigitalScreenEditorView({
  screen,
  name,
  description,
  bindings,
  selectedComponentId,
  datasets,
  datasetsError,
  versions,
  isDatasetsLoading,
  isVersionsLoading,
  isVersionsOpen,
  isSaving,
  isPublishing,
  isOfflining,
  rollingBackVersionNo,
  isDirty,
  template,
  selectedComponent,
  runtime,
  bindableCount,
  isSelectedQuerying,
  selectedQueryError,
  onBack,
  onPreview,
  onNameChange,
  onDescriptionChange,
  onComponentSelect,
  onSave,
  onPublish,
  onOffline,
  onOpenVersions,
  onCloseVersions,
  onRollbackVersion,
  onBindingChange,
}: DigitalScreenEditorViewProps) {
  const hasSavedUnpublishedChanges = Boolean(screen.hasUnpublishedChanges);
  const hasPendingPublish = isDirty || hasSavedUnpublishedChanges;
  const isPublished = screen.status === 'published';
  const statusLabel = isPublished
    ? `${screen.publishedVersionNo ? `已发布 V${screen.publishedVersionNo}` : '已发布'}${hasPendingPublish ? ' · 有未发布更改' : ''}`
    : '草稿';

  return (
    <div className="flex h-screen min-w-[1180px] flex-col overflow-hidden bg-[#f4f5f6] text-[#161823]">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#e5e7ea] bg-white px-4">
        <div className="flex min-w-0 items-center gap-2">
          <YakButton type="text" icon={<ArrowLeft size={16} />} onClick={onBack} />
          <div className="h-5 w-px bg-[#e7e9ec]" />
          <Input
            variant="borderless"
            value={name}
            onChange={(event) => onNameChange(event.target.value)}
            className="w-[320px] px-2 text-[14px] font-semibold"
            maxLength={80}
          />
          <span className={[
            'ml-1 rounded-[4px] px-2 py-1 text-[11px] font-medium',
            isPublished
              ? hasPendingPublish
                ? 'bg-[#fff7e8] text-[#ad6800]'
                : 'bg-[#edf8f2] text-[#27845a]'
              : 'bg-[#f2f3f4] text-[#7b818a]',
          ].join(' ')}>
            {statusLabel}
          </span>
          <span className="ml-1 text-[11px] text-[#98a2b3]">
            已绑定 {runtime.boundCount}/{bindableCount}
          </span>
          {runtime.loadingCount ? (
            <span className="text-[11px] text-[#8a9099]">正在刷新数据...</span>
          ) : null}
        </div>

        <div className="flex items-center gap-2">
          <YakButton icon={<History size={14} />} onClick={onOpenVersions}>版本历史</YakButton>
          <YakButton
            icon={<Eye size={14} />}
            disabled={!isPublished}
            onClick={onPreview}
          >
            线上预览
          </YakButton>
          <YakButton icon={<Save size={14} />} loading={isSaving} onClick={onSave}>保存</YakButton>
          {isPublished ? (
            <Popconfirm
              title="确认取消发布？"
              description="线上大屏会停止访问，但发布历史会继续保留。"
              okText="取消发布"
              cancelText="返回"
              onConfirm={onOffline}
            >
              <YakButton icon={<CloudOff size={14} />} loading={isOfflining}>取消发布</YakButton>
            </Popconfirm>
          ) : null}
          <YakButton
            type="primary"
            icon={<Send size={14} />}
            loading={isPublishing}
            disabled={isPublished && !hasPendingPublish}
            onClick={onPublish}
          >
            {isPublished ? '更新发布' : '发布'}
          </YakButton>
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <main className="min-w-0 flex-1 overflow-auto p-6">
          <div className="mx-auto flex min-h-full max-w-[1400px] items-center justify-center">
            {template ? (
              <div className="w-full overflow-hidden border border-[#dfe2e6] bg-[#111827]">
                <ScreenRenderer
                  template={template}
                  data={runtime.data}
                  selectedComponentId={selectedComponentId}
                  onComponentClick={(component) => onComponentSelect(component.id)}
                />
              </div>
            ) : (
              <div className="flex h-[420px] w-full items-center justify-center border border-[#e0e3e7] bg-white text-[13px] text-[#98a2b3]">
                当前模板不存在
              </div>
            )}
          </div>
        </main>

        <aside className="w-[360px] shrink-0 overflow-y-auto border-l border-[#e5e7ea] bg-white">
          <section className="border-b border-[#eceef1] px-5 py-5">
            <div className="text-[13px] font-semibold text-[#161823]">大屏设置</div>
            <label className="mt-4 block text-[12px] text-[#667085]">
              名称
              <Input
                value={name}
                onChange={(event) => onNameChange(event.target.value)}
                className="mt-2"
                variant="filled"
                maxLength={80}
              />
            </label>
            <label className="mt-4 block text-[12px] text-[#667085]">
              描述
              <Input.TextArea
                value={description}
                onChange={(event) => onDescriptionChange(event.target.value)}
                className="mt-2"
                variant="filled"
                rows={3}
                maxLength={200}
                placeholder="可选"
              />
            </label>
          </section>

          <section className="border-b border-[#eceef1] px-5 py-5">
            <div className="text-[13px] font-semibold text-[#161823]">模板</div>
            <div className="mt-4 rounded-[7px] bg-[#f6f7f8] p-3">
              <div className="text-[13px] font-medium text-[#444950]">{template?.name || '未知模板'}</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">
                {template ? `${template.category} · ${template.width} × ${template.height}` : screen.templateId}
              </div>
            </div>
            <div className="mt-2 text-[11px] leading-[18px] text-[#a3a8b0]">
              布局由模板固定。编辑内容只写入 Draft，只有点击发布后才会生成新的线上快照。
            </div>
          </section>

          <section className="px-5 py-5">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-[13px] font-semibold text-[#161823]">
                <Database size={14} /> 数据绑定
              </div>
              <span className="text-[10px] text-[#a3a8b0]">{datasets.length} 个可用 Dataset</span>
            </div>
            <div className="mt-4">
              <DataBindingPanel
                component={selectedComponent}
                binding={selectedComponent ? bindings[selectedComponent.id] : undefined}
                datasets={datasets}
                datasetsLoading={isDatasetsLoading}
                datasetsError={datasetsError}
                querying={isSelectedQuerying}
                queryError={selectedQueryError}
                onChange={onBindingChange}
              />
            </div>
          </section>
        </aside>
      </div>

      <VersionHistoryDrawer
        open={isVersionsOpen}
        versions={versions}
        loading={isVersionsLoading}
        rollingBackVersionNo={rollingBackVersionNo}
        onClose={onCloseVersions}
        onRollback={onRollbackVersion}
      />
    </div>
  );
}

import { Alert, Button, Empty, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { getLogicalDraft, listLogicalDrafts, listLogicalVersions } from '@/services/modeling/logical';
import type { LogicalDraftVersion, LogicalModelDraft } from '@/services/modeling/logical';
import { latestJourneyFrozenVersion } from '@/utils/journeyNavigation';
import { requiresLogicalBasis } from './modelCreationScope';

/**
 * A read-only design assistant. The Modeling Create API has no authorized
 * LogicalVersion binding field; never pass this selection to a create request.
 */
export default function ModelCreationLogicalBasis({
  layer, processId, onOpenLogical,
}: {
  layer?: string;
  processId?: number;
  onOpenLogical: (processId: number) => void;
}) {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const canRead = can('modeling:read');
  const active = requiresLogicalBasis(layer);
  const scope = JSON.stringify([currentProject?.id, processId, active, canRead]);
  const [catalog, setCatalog] = useState<{ scope: string; drafts: LogicalModelDraft[]; error?: boolean }>();
  const [chosen, setChosen] = useState<{ scope: string; id: number }>();
  const [versions, setVersions] = useState<{ scope: string; id: number; list: LogicalDraftVersion[]; error?: boolean }>();
  const [reload, setReload] = useState(0);
  const [versionReload, setVersionReload] = useState(0);
  const [fetching, setFetching] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const selected = chosen?.scope === scope ? chosen.id : undefined;
  const drafts = catalog?.scope === scope ? catalog.drafts : [];
  const selectedDraft = drafts.find(x => x.id === selected);
  const verifiedVersions = versions?.scope === scope && versions.id === selected ? versions.list : [];
  const latestVersion = useMemo(() => latestJourneyFrozenVersion(verifiedVersions), [verifiedVersions]);

  useEffect(() => {
    setCatalog(undefined);
    setChosen(undefined);
    setVersions(undefined);
    setFetching(false);
    if (!active || !canRead || !currentProject?.id || !processId) return;
    let live = true;
    setFetching(true);
    void listLogicalDrafts().then((items) => {
      if (!live) return;
      setCatalog({ scope, drafts: (items ?? []).filter(x =>
        x.processId === processId && String(x.projectId) === String(currentProject.id)) });
    }).catch(() => {
      if (live) setCatalog({ scope, drafts: [], error: true });
    }).finally(() => { if (live) setFetching(false); });
    return () => { live = false; };
  }, [scope, active, canRead, processId, currentProject?.id, reload]);

  useEffect(() => {
    setVersions(undefined);
    setVersionsLoading(false);
    if (selected == null || !selectedDraft) return;
    let live = true;
    setVersionsLoading(true);
    void Promise.all([getLogicalDraft(selected), listLogicalVersions(selected)]).then(([detail, list]) => {
      if (!live) return;
      if (detail.model.id !== selected || detail.model.processId !== processId
          || String(detail.model.projectId) !== String(currentProject?.id)) {
        throw new Error('Logical draft Owner identity mismatch');
      }
      setVersions({ scope, id: selected, list: (list ?? []).filter(x =>
        Number.isSafeInteger(x.versionNo) && x.versionNo > 0) });
    }).catch(() => {
      if (live) setVersions({ scope, id: selected, list: [], error: true });
    }).finally(() => { if (live) setVersionsLoading(false); });
    return () => { live = false; };
  }, [scope, selected, selectedDraft?.id, versionReload]);

  if (!layer) return null;

  if (!active) return null;

  if (!processId) return <Typography.Text type="secondary">
    关联业务过程后，可查看对应的逻辑设计。
  </Typography.Text>;
  if (!canRead || !currentProject?.id) return <Alert showIcon type="error"
    message="无法读取当前项目逻辑设计依据"
    description="请确认已选择项目并具有建模读取权限。"
  />;

  return (
    <section aria-label="逻辑设计依据核对" className="space-y-3 rounded-xl border border-[#dfe5ed] bg-[#f8fafc] p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Typography.Text strong>逻辑设计参考</Typography.Text>
          <Typography.Paragraph className="!mb-0 !text-[12px]" type="secondary">
            参考业务实体、属性和粒度；创建后可继续核对字段映射。
          </Typography.Paragraph>
        </div>
        <Space>
          <Button size="small" loading={fetching} onClick={() => setReload(v => v + 1)}>刷新</Button>
          <Button size="small" type="link" onClick={() => onOpenLogical(processId)}>查看逻辑模型</Button>
        </Space>
      </div>
      {catalog?.scope === scope && catalog.error ? (
        <Alert type="error" showIcon message="逻辑草稿目录暂时无法读取"
          description="请刷新重试，或前往逻辑模型查看。" />
      ) : catalog?.scope === scope && drafts.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} imageStyle={{ height: 36 }}
          description="该业务过程暂无逻辑模型" style={{ marginBlock: 12 }}>
          <Button size="small" onClick={() => onOpenLogical(processId)}>创建逻辑模型</Button>
        </Empty>
      ) : (
        <div>
          <Select
            aria-label="逻辑模型参考"
            className="w-full"
            showSearch optionFilterProp="label"
            allowClear
            loading={fetching}
            disabled={fetching || !drafts.length}
            value={selected}
            placeholder={fetching ? '正在加载逻辑模型…' : '选择逻辑模型（可选）'}
            options={drafts.map(x => ({ value: x.id, label: `${x.name} · ${x.code}` }))}
            onChange={(v) => { setChosen(v === undefined ? undefined : { scope, id: v }); setVersions(undefined); }}
          />
          {selectedDraft && (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px]">
              <Tag color={latestVersion ? 'blue' : 'default'}>
                {versionsLoading ? '加载版本中' : versions?.error ? '版本读取失败' : latestVersion
                  ? `设计版本 V${latestVersion}` : '尚无设计版本'}
              </Tag>
              <span className="text-[#667085]">
                {versionsLoading ? '正在核对版本。' : versions?.error ? '请重新读取后确认。' : latestVersion
                  ? '仅供参考；创建后可在“业务逻辑依据”中核对映射。'
                  : '可先保存物理草稿，或前往逻辑模型保存设计版本。'}
              </span>
              {versions?.error && <Button size="small" type="link"
                onClick={() => setVersionReload(v => v + 1)}>重新读取版本</Button>}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

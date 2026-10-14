import { Alert, Button, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { listLogicalDrafts, listLogicalVersions } from '@/services/modeling/logical';
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
    if (!active || !canRead || !currentProject?.id || !processId) return;
    let live = true;
    setFetching(true);
    void listLogicalDrafts().then((items) => {
      if (!live) return;
      setCatalog({ scope, drafts: (items ?? []).filter(x => x.processId === processId) });
    }).catch(() => {
      if (live) setCatalog({ scope, drafts: [], error: true });
    }).finally(() => { if (live) setFetching(false); });
    return () => { live = false; };
  }, [scope, active, canRead, processId, currentProject?.id, reload]);

  useEffect(() => {
    setVersions(undefined);
    if (selected == null || !selectedDraft) return;
    let live = true;
    setVersionsLoading(true);
    void listLogicalVersions(selected).then((list) => {
      if (!live) return;
      setVersions({ scope, id: selected, list: (list ?? []).filter(x =>
        Number.isSafeInteger(x.versionNo) && x.versionNo > 0) });
    }).catch(() => {
      if (live) setVersions({ scope, id: selected, list: [], error: true });
    }).finally(() => { if (live) setVersionsLoading(false); });
    return () => { live = false; };
  }, [scope, selected, selectedDraft?.id]);

  if (!active) return (
    <Alert type="info" showIcon
      message="ODS 贴源建模是独立路径"
      description="此处是手动新建 ODS 草稿；要从已核验来源表自动带入准确表结构，请在数据源工作台选择原始表进入 ODS。"
    />
  );

  if (!processId) return <Alert type="warning" showIcon
    message="建议先明确业务过程及数据粒度"
    description="非 ODS 模型通常需要业务逻辑模型解释字段和粒度。这里允许保存物理草稿，但不会虚构逻辑版本、正式映射或批准状态。"
  />;
  if (!canRead || !currentProject?.id) return <Alert showIcon type="warning"
    message="无法读取当前项目逻辑设计依据"
    description="尚未获得建模读取权限或当前项目无效，不能将逻辑设计状态标成“没有模型”。"
  />;

  return (
    <section aria-label="逻辑设计依据核对" className="space-y-3 rounded-xl border border-[#dfe5ed] bg-[#f8fafc] p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Typography.Text strong>业务逻辑依据（只读核对）</Typography.Text>
          <Typography.Paragraph className="!mb-0 !text-[12px]" type="secondary">
            先明确逻辑草稿和真实冻结版本，再创建物理结构。此选择不会写入物理模型，也不是正式的逻辑↔物理绑定。
          </Typography.Paragraph>
        </div>
        <Space>
          <Button size="small" loading={fetching} onClick={() => setReload(v => v + 1)}>重新核验</Button>
          <Button size="small" type="link" onClick={() => onOpenLogical(processId)}>打开逻辑工作台</Button>
        </Space>
      </div>
      {catalog?.scope === scope && catalog.error ? (
        <Alert type="error" showIcon message="逻辑草稿目录暂时无法读取"
          description="读取失败不代表没有业务设计；可重新核验或返回逻辑模型页。" />
      ) : catalog?.scope === scope && drafts.length === 0 ? (
        <Alert type="warning" showIcon message="该业务过程暂无可读取的逻辑草稿"
          description="推荐先在逻辑工作台创建实体、业务主键和关系，再冻结草稿版本。当前创建操作仅形成物理设计草稿。" />
      ) : (
        <div>
          <Select
            className="w-full"
            showSearch optionFilterProp="label"
            allowClear
            loading={fetching}
            disabled={fetching || !drafts.length}
            value={selected}
            placeholder={fetching ? '正在核对当前项目逻辑模型…' : '选择要核对的逻辑草稿（不自动绑定）'}
            options={drafts.map(x => ({ value: x.id, label: `${x.name} · ${x.code} (#${x.id})` }))}
            onChange={(v) => { setChosen(v === undefined ? undefined : { scope, id: v }); setVersions(undefined); }}
          />
          {selectedDraft && (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px]">
              <Tag color={latestVersion ? 'blue' : 'default'}>
                {versionsLoading ? '核验冻结版本中' : versions?.error ? '冻结版本读取失败' : latestVersion
                  ? `已存在冻结快照 V${latestVersion}` : '尚无已核验冻结版本'}
              </Tag>
              <span className="text-[#667085]">
                {latestVersion ? '可以创建物理草稿后，进入模型详情“业务逻辑依据”重新选择并预检此版本。'
                  : '建议完成业务主键、粒度与标准字段核对后冻结；此处不会伪造逻辑版本。'}
              </span>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

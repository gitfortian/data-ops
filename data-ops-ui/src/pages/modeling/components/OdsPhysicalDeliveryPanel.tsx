import { history } from '@umijs/max';
import { Alert, Button, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { listAllDataSources } from '@/services/data-source/api';
import { inspectOdsPhysicalTarget, listOdsTargetObservations, recordOdsTargetObservation, type OdsPhysicalTargetInspection, type OdsTargetObservation } from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { buildIntegrationCreatePath } from '@/services/integration/sourceHandoff';

interface Props {
  open: boolean;
  model: ModelingModelRecord;
  onClose: () => void;
}

export default function OdsPhysicalDeliveryPanel({ open, model, onClose }: Props) {
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  const [sources, setSources] = useState<{ id: number; name: string; dbType?: string }[]>([]);
  const [targetId, setTargetId] = useState<number>();
  const [database, setDatabase] = useState('');
  const [schema, setSchema] = useState('');
  const [report, setReport] = useState<OdsPhysicalTargetInspection>();
  const [busy, setBusy] = useState(false);
  const [loadingSources, setLoadingSources] = useState(false);
  const [recording, setRecording] = useState(false);
  const [observations, setObservations] = useState<OdsTargetObservation[]>([]);
  const readEpoch = useRef(0);
  const invalidate = () => { readEpoch.current += 1; setReport(undefined); };
  const modelId = Number(model.id);
  const versionNo = Number(model.latestVersionNo);
  const hasVersion = Number.isSafeInteger(versionNo) && versionNo > 0;
  const allowed = canAll(['modeling:read', 'resource:data-source:read']);

  useEffect(() => {
    let current = true;
    readEpoch.current += 1;
    setSources([]);
    setObservations([]);
    setTargetId(undefined);
    setDatabase('');
    setSchema('');
    setReport(undefined);
    if (!open || !allowed) return () => { current = false; };
    setLoadingSources(true);
    void listOdsTargetObservations(modelId).then(rows => {
      if (current) setObservations(rows ?? []);
    }).catch(error => {
      if (current) message.error(error instanceof Error ? error.message : '核验历史不可读取');
    });
    void listAllDataSources().then(page => {
      if (!current) return;
      setSources((page.bizData ?? []).filter(item => Number.isSafeInteger(Number(item.id)))
        .map(item => ({
          id: Number(item.id),
          name: String(item.name || '未命名数据源'),
          dbType: String(item.dbType || ''),
        })));
    }).catch(error => {
      if (current) message.error(error instanceof Error ? error.message : '目标数据源不可读取');
    }).finally(() => {
      if (current) setLoadingSources(false);
    });
    return () => { current = false; };
  }, [open, allowed, currentProject?.id, modelId, versionNo]);

  const inspect = async () => {
    if (!allowed || !hasVersion || !targetId || !database.trim()) return;
    const ticket = ++readEpoch.current;
    setBusy(true);
    setReport(undefined);
    try {
      const result = await inspectOdsPhysicalTarget(
        modelId, versionNo, targetId, database.trim(), schema.trim() || undefined,
      );
      if (result.modelId !== modelId || result.versionNo !== versionNo
          || result.targetDatasourceId !== targetId
          || result.targetDatabase !== database.trim()
          || (result.targetSchema || '') !== schema.trim()) {
        throw new Error('目标或模型版本在核验过程中已变化，请重新选择');
      }
      if (ticket === readEpoch.current) setReport(result);
    } catch (error) {
      if (ticket === readEpoch.current)
        message.error(error instanceof Error ? error.message : '目标库核验不可用');
    } finally { if (ticket === readEpoch.current) setBusy(false); }
  };

  const recordObservation = async () => {
    if (!allowed || !canAll(['modeling:update']) || !targetId || !database.trim()
        || report?.state !== 'COLUMN_MATCH_NEEDS_DDL_REVIEW') return;
    const ticket = ++readEpoch.current;
    const previous = report;
    setRecording(true);
    try {
      // Never submit a cached UI fingerprint; the server re-inspects the live target.
      const saved = await recordOdsTargetObservation(
        modelId, versionNo, targetId, database.trim(), schema.trim() || undefined,
      );
      const historyRows = await listOdsTargetObservations(modelId);
      if (ticket !== readEpoch.current) return;
      const confirmed = historyRows.find(row => row.id === saved.id);
      if (!confirmed || confirmed.modelId !== modelId || confirmed.versionNo !== versionNo
          || confirmed.modelVersionId !== previous.versionId
          || confirmed.targetDatasourceId !== targetId
          || confirmed.targetDatabase !== database.trim()
          || (confirmed.targetSchema || '') !== schema.trim()
          || confirmed.observedCatalogSha256 !== saved.observedCatalogSha256) {
        throw new Error('服务端保存回执与核验记录重新读取不一致，不能确认记录成功');
      }
      setObservations(historyRows);
      message.success('目标 Catalog 观察证据已保存并重新读取。不是建表、部署或同步成功回执。');
    } catch (error) {
      if (ticket === readEpoch.current) message.error(
        error instanceof Error ? error.message : '记录保存结果未知，请重新查看核验历史，勿盲目重试');
    } finally {
      if (ticket === readEpoch.current) setRecording(false);
    }
  };

  const sourceId = Number(model.sourceDatasourceId);
  const matchingObservation = report?.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW'
    ? observations.find(row => row.status === 'RECENT_CATALOG_OBSERVATION'
      && row.modelId === modelId && row.versionNo === versionNo
      && row.modelVersionId === report.versionId
      && row.targetDatasourceId === targetId
      && row.targetDatabase === database.trim()
      && (row.targetSchema || '') === schema.trim()
      && row.targetTable === report.targetTable
      && row.observedCatalogSha256 === report.observedCatalogSha256) : undefined;
  // Navigation only: the Offline Sync owner MUST revalidate before accepting
  // any binding or permitting a runnable revision.
  const mayCreateSync = allowed && Boolean(matchingObservation)
    && Number.isSafeInteger(sourceId) && sourceId > 0;

  return <Modal
    title="ODS 目标库核验与离线同步接续"
    open={open} onCancel={onClose} width={830} destroyOnHidden
    footer={<Button onClick={onClose}>关闭</Button>}>
    <Alert type="info" showIcon className="mb-3"
      message="建模不执行 DDL，也不自动创建或运行同步任务"
      description="先从已发布物理版本生成并由授权人员在目标库执行建表脚本，再按目标 Catalog 核验。核验不是部署回执；同步任务和运行结果由离线同步模块独立保存。" />
    <Space wrap className="mb-3">
      <Tag>模型 #{modelId}</Tag>
      <Tag>{hasVersion ? `当前发布 V${versionNo}` : '尚未发布'}</Tag>
      <Tag>来源：{model.sourceSchema ? `${model.sourceSchema}.` : ''}{model.sourceTable || '未绑定'}</Tag>
    </Space>
    {!hasVersion && <Alert type="warning" showIcon className="mb-3"
      message="请先保存并发布 ODS 版本；草稿结构不生成部署完成证据。" />}
    <div className="mb-3 flex flex-wrap items-end gap-2">
      <div className="min-w-[185px] flex-1">
        <Typography.Text type="secondary">目标数据源</Typography.Text>
        <Select className="w-full" placeholder="选择目标库连接" loading={loadingSources}
          disabled={!allowed || !hasVersion}
          value={targetId} onChange={value => { setTargetId(value); invalidate(); }}
          options={sources.map(item => ({
            value: item.id, label: `${item.name} (${item.dbType || '未知类型'})`,
          }))} />
      </div>
      <div className="w-[180px]">
        <Typography.Text type="secondary">目标 Database</Typography.Text>
        <Input value={database} placeholder="精确数据库名"
          disabled={!hasVersion || !allowed} onChange={event => {
            setDatabase(event.target.value); invalidate();
          }} />
      </div>
      <div className="w-[155px]">
        <Typography.Text type="secondary">目标 Schema（可空）</Typography.Text>
        <Input value={schema} placeholder="如 public" disabled={!hasVersion || !allowed}
          onChange={event => { setSchema(event.target.value); invalidate(); }} />
      </div>
      <Button loading={busy} type="primary" onClick={() => void inspect()}
        disabled={!hasVersion || !allowed || !targetId || !database.trim()}>
        读取目标库并核验
      </Button>
    </div>
    {report && <>
      <Alert type={report.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW' ? 'success' : 'warning'}
        showIcon className="mb-3"
        message={report.state === 'COLUMN_MATCH_NEEDS_DDL_REVIEW'
          ? '目标 Catalog 物理列匹配，仍需确认约束与部署记录'
          : report.state === 'STRUCTURE_DIFFERS'
            ? '目标字段与冻结版本存在差异，禁止认为部署完成'
            : '目标 Catalog 不可核验，不能推断已部署或未部署'}
        description={`V${report.versionNo} · ${report.targetDatabase}.${report.targetSchema
          ? report.targetSchema + '.' : ''}${report.targetTable} · 冻结列 ${report.frozenColumns} / 观察列 ${report.observedColumns}`} />
      {report.differences.length > 0 && <Table size="small" pagination={false}
        rowKey={(r) => r.column + ':' + r.issue} dataSource={report.differences}
        columns={[
          { title: '字段', dataIndex: 'column' }, { title: '差异', dataIndex: 'issue' },
          { title: '冻结预期', dataIndex: 'expectedType' },
          { title: '目标实际', dataIndex: 'observedType' },
        ]} />}
      {report.limitations.map((line, idx) => <Typography.Paragraph key={idx}
        type="secondary" className="!mb-1">· {line}</Typography.Paragraph>)}
      <Space wrap className="mt-3">
        <Button disabled={!allowed || !canAll(['modeling:update'])
            || report.state !== 'COLUMN_MATCH_NEEDS_DDL_REVIEW'}
          loading={recording} onClick={() => void recordObservation()}>
          保存此次目标列观察记录
        </Button>
        <Button disabled={!mayCreateSync} type="primary" onClick={() =>
          history.push(buildIntegrationCreatePath('batch', {
            dataSourceId: String(sourceId), odsModelId: modelId,
            odsVersionNo: versionNo, odsTargetObservationId: matchingObservation!.id,
          }))}>
          前往离线同步配置（需人工建任务）
        </Button>
        <Typography.Text type="secondary">
          需先保存并回读本次 Catalog 观察，再进入同步配置；同步 Owner 仍须独立核验精确版本、目标和执行权限。
        </Typography.Text>
      </Space>
    </>}
    {observations.length > 0 && <>
      <Typography.Title level={5} className="!mt-4">最近目标列核验记录（最多 20 条）</Typography.Title>
      <Table size="small" rowKey="id" pagination={false} dataSource={observations.slice(0, 5)}
        columns={[
          { title: '记录', dataIndex: 'id', width: 75 },
          { title: '版本', dataIndex: 'versionNo', width: 75, render: (no: number) => `V${no}` },
          { title: '目标', key: 'target', render: (_, row: OdsTargetObservation) =>
            `${row.targetDatabase}.${row.targetSchema ? row.targetSchema + '.' : ''}${row.targetTable}` },
          { title: '观察时间', dataIndex: 'observedAt', width: 168 },
          { title: '证据状态', dataIndex: 'status', width: 210,
            render: (status: string) => <Tag color={status === 'RECENT_CATALOG_OBSERVATION' ? 'blue' : 'orange'}>
              {status === 'RECENT_CATALOG_OBSERVATION' ? '近期列观察（非部署）'
                : status === 'VERSION_CHANGED' ? '发布版本已变化' : '观察已过期'}
            </Tag> },
        ]} />
      <Typography.Text type="secondary">
        历史观察不可替代实时目标核验、DDL 部署审计、同步任务发布版本和运行证据。
      </Typography.Text>
    </>}
  </Modal>;
}

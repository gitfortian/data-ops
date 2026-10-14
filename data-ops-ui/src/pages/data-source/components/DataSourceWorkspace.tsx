import DatabaseIcons from '@/components/data-source/icons/DatabaseIcons';
import { history, useAccess } from '@umijs/max';
import { Button, Drawer, Modal, Space, Tabs, message } from 'antd';
import { ArrowUpRight, Database, Search, Unplug } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { collectDefinitionPath, findCoveringCollectionJobs } from '@/services/metadata/collectionHandoff';
import type { CollectionSourceScope } from '@/services/metadata/collectionHandoff';
import type { CollectJobRecord } from '@/services/metadata/types';
import type { DataSourcePermissions, DataSourceRecord } from '../types';
import { buildLiveSourceOdsPath } from '@/utils/liveSourceOdsJourney';
import { dataSourceRecordKey } from '../types';
import CollectionInfo from './CollectionInfo';
import DataSourceStatus from './DataSourceStatus';
import LiveSchemaExplorer from './LiveSchemaExplorer';

interface DataSourceWorkspaceProps {
  record: DataSourceRecord;
  permissions: DataSourcePermissions;
  testingId: string;
  onClose: () => void;
  onTestConnection: (record: DataSourceRecord) => void;
  onEdit: (record: DataSourceRecord) => void;
  initialTable?: { database: string; schema?: string; table: string };
}

export default function DataSourceWorkspace({
  record,
  permissions,
  testingId,
  onClose,
  onTestConnection,
  onEdit,
  initialTable,
}: DataSourceWorkspaceProps) {
  const access = useAccess();
  const sourceKey = dataSourceRecordKey(record.id);
  const validSource = Boolean(sourceKey) && Number.isSafeInteger(Number(sourceKey)) && Number(sourceKey) > 0;
  const canReadMetadata = validSource && access.hasPermission('data-metadata:read');
  const canCreate = canReadMetadata && access.hasPermission('data-metadata:create');
  const canUpdate = canReadMetadata && access.hasPermission('data-metadata:update');
  const [tab, setTab] = useState('live');
  const [defining, setDefining] = useState(false);
  const [candidates, setCandidates] = useState<CollectJobRecord[] | null>(null);
  const [candidateScope, setCandidateScope] = useState<CollectionSourceScope>({ dataSourceId: sourceKey });
  const definitionSequence = useRef(0);
  useEffect(
    () => () => {
      definitionSequence.current += 1;
    },
    [],
  );
  const defineCollection = async (target: CollectionSourceScope) => {
    if (!canReadMetadata || defining) return;
    const request = ++definitionSequence.current;
    setDefining(true);
    try {
      const matches = await findCoveringCollectionJobs(target);
      if (request !== definitionSequence.current) return;
      if (matches.length === 1) {
        history.push(
          canUpdate
            ? collectDefinitionPath(target, matches[0].id)
            : `/data-metadata/collect?dataSourceId=${encodeURIComponent(sourceKey)}`,
        );
      } else if (matches.length > 1) {
        setCandidateScope(target);
        setCandidates(matches);
      } else if (canCreate) {
        history.push(collectDefinitionPath(target));
      } else {
        message.warning('当前范围没有适用任务，且当前账号没有创建采集任务的权限');
      }
    } catch {
      if (request === definitionSequence.current)
        message.warning('未能完整核对已有任务，请到采集任务页面核验后再定义，避免重复创建');
    } finally {
      if (request === definitionSequence.current) setDefining(false);
    }
  };
  return (
    <>
      <Drawer
        open
        onClose={onClose}
        width="min(1360px, 100vw)"
        placement="right"
        rootClassName="source-workspace source-workbench-v082"
        title={
          <div className="source-workspace-title">
            <div className="source-card-icon">
              <DatabaseIcons dbType={record.dbType} width="28" height="28" />
            </div>
            <div>
              <h2>{record.name || '数据源工作区'}</h2>
              <p>{record.dbType} · {record.environmentName || record.environment || '未分类环境'}</p>
            </div>
          </div>
        }
      >
        <main className="source-workbench-content" data-testid="source-ods-workbench">
          <div className="source-workbench-intro">
            <div>
              <h1>源表结构</h1>
              <p>查看当前数据源的库、Schema、表及字段，核对后可直接进入 ODS 建模。</p>
            </div>
            <Button onClick={onClose}>返回数据源列表</Button>
          </div>
          <section className="source-workspace-connection" aria-label="当前数据源连接">
            <div>
              <div className="source-workspace-connection-label">
                <strong>连接状态</strong>
                <DataSourceStatus status={record.connStatus} />
              </div>
              <div className="source-workspace-connection-note">最近一次连接检测结果；实际表结构以实时探查为准。</div>
            </div>
            <Space wrap>
              {permissions.canUpdate ? <Button onClick={() => onEdit(record)}>编辑连接</Button> : null}
              {permissions.canTest ? (
                <Button icon={<Unplug size={15} />}
                  loading={testingId === sourceKey}
                  disabled={Boolean(testingId) && testingId !== sourceKey}
                  onClick={() => onTestConnection(record)}
                >检测连接</Button>
              ) : null}
            </Space>
          </section>
          <section className="source-workspace-panel" aria-label="源表发现与采集信息">
            <div className="source-workbench-panel-head">
              <div>
                <h2>数据表与字段</h2>
                <p>选择源表、核对字段；采集是独立的可选操作。</p>
              </div>
              <span>选择源表 → 核对结构 → 打开或创建 ODS</span>
            </div>
            <Tabs
              activeKey={tab}
              onChange={setTab}
              items={[
                { key: 'live', label: <span className="source-workspace-tab-label"><Search size={15} aria-hidden="true" />即时探查</span> },
                ...(canReadMetadata ? [
                  { key: 'collection', label: <span className="source-workspace-tab-label"><Database size={15} aria-hidden="true" />采集信息</span> },
                ] : []),
              ]}
            />
            {tab === 'live' ? (
              <LiveSchemaExplorer
                sourceId={sourceKey}
                initialTable={initialTable}
                onOpenOds={validSource && access.hasPermission('modeling:read')
                  && access.hasPermission('resource:data-source:read')
                  ? (source) => {
                      const path = buildLiveSourceOdsPath(source);
                      if (path) history.push(path);
                      else message.warning('来源坐标不完整，无法安全进入 ODS，请重新核对物理表');
                    }
                  : undefined}
                canRead={validSource && access.hasPermission('resource:data-source:read')}
                onDefineTask={canCreate || canUpdate ? (target) => void defineCollection(target) : undefined}
                definitionLoading={defining}
              />
            ) : (
              <CollectionInfo sourceId={sourceKey} canCreate={canCreate} />
            )}
          </section>
          <p className="source-workbench-footnote">
            资产纳管不作为 ODS 建模前置条件。实时探查不创建采集任务，也不代表元数据已正式采集。
          </p>
        </main>
      </Drawer>
      <Modal
        open={Boolean(candidates)}
        title="选择已有采集任务"
        onCancel={() => setCandidates(null)}
        footer={<Button onClick={() => setCandidates(null)}>取消</Button>}
      >
        <p className="source-workspace-caption">
          以下任务均覆盖当前探查范围。选择任务后核对原有采集范围和调度，不重复创建。
        </p>
        {candidates?.map((job) => (
          <Button
            key={job.id}
            block
            className="source-collection-candidate"
            onClick={() => {
              setCandidates(null);
              history.push(
                canUpdate
                  ? collectDefinitionPath(candidateScope, job.id)
                  : `/data-metadata/collect?dataSourceId=${encodeURIComponent(sourceKey)}`,
              );
            }}
          >
            <span>
              <strong>{job.jobName}</strong>
              <span className="source-workspace-scope">
                {job.databaseName || '全部库'} / {job.schemaName || '全部 Schema'} / {job.tablePattern || '全部表'}
              </span>
            </span>
            <ArrowUpRight size={15} />
          </Button>
        ))}
      </Modal>
    </>
  );
}

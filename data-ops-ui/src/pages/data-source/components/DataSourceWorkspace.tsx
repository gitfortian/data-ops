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
        width="100vw"
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
            <span className="source-workbench-head-caption">J1 / 来源发现与贴源建模</span>
          </div>
        }
      >
        <div className="source-workbench-layout" data-testid="source-ods-workbench">
          <aside className="source-workbench-rail" aria-label="来源到 ODS 工作步骤">
            <div className="source-workbench-eyebrow">SOURCE / WORKBENCH</div>
            <h2>从源表开始建设</h2>
            <p className="source-workbench-rail-desc">
              不需要先创建资产。按当前项目的真实来源逐步核对，再选择打开或新建 ODS。
            </p>
            <ol className="source-workbench-steps">
              <li className="source-workbench-step">
                <span className="source-workbench-step-number">01</span>
                <div><strong>连接与授权</strong><small>已有数据源 · 按当前权限检查</small></div>
              </li>
              <li className="source-workbench-step active">
                <span className="source-workbench-step-number">02</span>
                <div><strong>发现并核对源表</strong><small>库 / Schema / 表 / 实时列结构</small></div>
              </li>
              <li className="source-workbench-step">
                <span className="source-workbench-step-number">03</span>
                <div><strong>打开或创建 ODS</strong><small>查找精确同源目标 · 核对字段后保存</small></div>
              </li>
              <li className="source-workbench-step">
                <span className="source-workbench-step-number">04</span>
                <div><strong>返回来源继续</strong><small>保存后回到同一张原始表</small></div>
              </li>
            </ol>
            <div className="source-workbench-rail-footer">
              <strong>资产治理是并行任务</strong>
              <p>没有 Asset 写权限，也可以完成来源发现及有权的 ODS 建模。</p>
            </div>
          </aside>
          <main className="source-workbench-main">
            <header className="source-workbench-page-head">
              <div>
                <p className="source-workbench-eyebrow">SOURCE DISCOVERY · 当前项目</p>
                <h1>发现源表，继续 ODS 建模</h1>
                <p>选择准确的数据库、Schema 和原始表；核对实际列结构后，再进入 ODS 目标查找和字段保存。</p>
              </div>
              <Button onClick={onClose}>返回数据源列表</Button>
            </header>
            <section className="source-workspace-connection" aria-label="当前数据源连接">
              <div>
                <div className="source-workspace-connection-label">
                  <strong>连接状态</strong>
                  <DataSourceStatus status={record.connStatus} />
                </div>
                <div className="source-workspace-connection-note">来自最近一次连接检测；不是本次源表、采集或 ODS 保存成功的凭据。</div>
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
              <div className="source-workbench-section-head">
                <div><h2>来源结构与采集</h2><p>直接探查与目录采集是不同操作，切换不会写入元数据。</p></div>
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
          </main>
          <aside className="source-workbench-aside" aria-label="来源工作台下一步与说明">
            <section className="source-workbench-aside-card">
              <p className="source-workbench-eyebrow">NEXT ACTION</p>
              <h2>先核对来源，再做 ODS</h2>
              <p>在中央选择一张表，确认字段和主键之后，使用表结构区域的“打开或创建 ODS”。</p>
              <div className="source-workbench-proof">
                <strong>为何不直接建模？</strong>
                <span>同名表可能位于不同数据库或 Schema。目标页会再次核验源身份并精确查找已有 ODS，避免重复创建。</span>
              </div>
            </section>
            <section className="source-workbench-aside-card">
              <p className="source-workbench-eyebrow">可选治理步骤</p>
              <h3>需要正式采集元数据？</h3>
              <p>在“即时探查”选择库或表后点击“配置采集”，保存任务、预演，再正式运行。即时探查本身不代表采集完成。</p>
              {canReadMetadata ? (
                <Button block onClick={() => setTab('collection')}>查看采集信息</Button>
              ) : <p>当前角色无元数据读取权限，仍可使用已授权的实时源表能力。</p>}
            </section>
          </aside>
        </div>
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

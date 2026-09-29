import { Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  LandingStatusCell,
  LandingTaskModal,
  RunLandingLink,
  useMdmCollectStatus,
} from '@/pages/mdm/components/CollectLanding';
import FieldMappingEditor from './FieldMappingEditor';
import { listAllDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import {
  confirmMdmSource,
  listMdmSources,
  scanMdmTables,
  unbindMdmSource,
} from '@/services/mdm/api';
import { pageMdmEntities } from '@/services/mdm/api';
import type {
  MdmEntityRecord,
  MdmSourceRecord,
  MdmSourceRole,
  MdmTableCandidate,
} from '@/services/mdm/types';

const ROLE_LABELS: Record<MdmSourceRole, string> = {
  MAIN: '主来源',
  AUXILIARY: '辅助来源',
};

interface ConfirmFormValues {
  entityId: number;
  role: MdmSourceRole;
}

/** 主数据识别(ticket 53):数据源扫描 → 候选标注 → 确认为实体来源。 */
const MdmIdentificationPage = () => {
  const [form] = Form.useForm<ConfirmFormValues>();
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [datasourceId, setDatasourceId] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [scanning, setScanning] = useState(false);
  const [tables, setTables] = useState<MdmTableCandidate[]>([]);
  // 排除 = 当前扫描会话内不再提示(不落库,ticket 53 口径)
  const [excludedKeys, setExcludedKeys] = useState<Set<string>>(new Set());
  const [sources, setSources] = useState<MdmSourceRecord[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmTarget, setConfirmTarget] = useState<MdmTableCandidate | null>(null);
  const [confirmEntityId, setConfirmEntityId] = useState<number | undefined>(undefined);
  const [fieldMapping, setFieldMapping] = useState<Record<string, string>>({});
  const [mappingIssue, setMappingIssue] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [entities, setEntities] = useState<MdmEntityRecord[]>([]);
  const { bySource: collectBySource, reload: reloadCollect } = useMdmCollectStatus();
  const [landingTarget, setLandingTarget] = useState<MdmSourceRecord | null>(null);

  const loadDataSources = useCallback(async () => {
    try {
      const result = await listAllDataSources();
      setDataSources(result?.bizData ?? result ?? []);
    } catch {
      setDataSources([]);
    }
  }, []);

  const loadSources = useCallback(async () => {
    try {
      setSources(await listMdmSources());
    } catch {
      setSources([]);
    }
  }, []);

  const loadEntities = useCallback(async () => {
    try {
      const result = await pageMdmEntities({ pageNo: 1, pageSize: 200 });
      setEntities(result.bizData ?? []);
    } catch {
      setEntities([]);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
    void loadSources();
  }, [loadDataSources, loadSources]);

  const runScan = useCallback(async () => {
    if (!datasourceId) {
      message.warning('请先选择数据源');
      return;
    }
    setScanning(true);
    try {
      setTables(await scanMdmTables(datasourceId, keyword.trim() || undefined));
      setExcludedKeys(new Set());
    } catch {
      setTables([]);
      message.error('数据源扫描失败或不可用，请检查数据源连接');
    } finally {
      setScanning(false);
    }
  }, [datasourceId, keyword]);

  const tableKey = (table: MdmTableCandidate) =>
    `${table.database ?? ''}.${table.schema ?? ''}.${table.name}`;

  const visibleTables = useMemo(
    () => tables.filter((table) => !excludedKeys.has(tableKey(table))),
    [tables, excludedKeys],
  );

  const openConfirm = (table: MdmTableCandidate) => {
    setConfirmTarget(table);
    setConfirmOpen(true);
    form.resetFields();
    setFieldMapping({});
    setMappingIssue(null);
    // 候选命中时默认选中第一个匹配实体
    const first = table.candidates?.[0];
    setConfirmEntityId(first?.entityId);
    form.setFieldsValue({ entityId: first?.entityId, role: 'MAIN' });
    void loadEntities();
  };

  const submitConfirm = async () => {
    const values = await form.validateFields();
    if (!confirmTarget || !datasourceId) return;
    if (mappingIssue) {
      message.error(mappingIssue);
      return;
    }
    // 仅提交与属性编码不同名的映射;全同名时省略(后端同名回退)
    const custom = Object.fromEntries(
      Object.entries(fieldMapping).filter(
        ([code, column]) => column && column !== code,
      ),
    );
    setSaving(true);
    try {
      await confirmMdmSource({
        entityId: values.entityId,
        datasourceId,
        database: confirmTarget.database,
        schema: confirmTarget.schema,
        table: confirmTarget.name,
        role: values.role,
        fieldMapping: Object.keys(custom).length > 0 ? custom : undefined,
      });
      message.success(`已将 ${confirmTarget.name} 绑定为来源`);
      setConfirmOpen(false);
      await loadSources();
      await runScan();
    } catch {
      message.error('确认失败（可能已绑定或数据源不可用）');
    } finally {
      setSaving(false);
    }
  };

  const unbind = (record: MdmSourceRecord) => {
    Modal.confirm({
      title: '解绑来源',
      content: `确定解绑「${record.table}」与实体「${record.entityName}」的绑定？已被采集配置引用时将被阻断。`,
      okText: '解绑',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await unbindMdmSource(record.id);
          message.success('已解绑');
          await loadSources();
          await runScan();
        } catch {
          message.error('解绑失败（可能存在采集配置引用）');
        }
      },
    });
  };

  const columns: ColumnsType<MdmTableCandidate> = [
    { title: '库', dataIndex: 'database', width: 140, render: (value?: string) => value || '-' },
    { title: '模式', dataIndex: 'schema', width: 100, render: (value?: string) => value || '-' },
    { title: '表名', dataIndex: 'name', width: 200 },
    { title: '备注', dataIndex: 'remarks', ellipsis: true, render: (value?: string) => value || '-' },
    {
      title: '候选实体',
      dataIndex: 'candidates',
      width: 240,
      render: (candidates: MdmTableCandidate['candidates']) =>
        candidates.length > 0 ? (
          <Space size={4} wrap>
            {candidates.map((candidate) => (
              <Tag key={candidate.entityId} color="blue">
                {candidate.entityName}（{candidate.entityCode}）
              </Tag>
            ))}
          </Space>
        ) : (
          <Typography.Text type="secondary">未匹配</Typography.Text>
        ),
    },
    {
      title: '状态',
      dataIndex: 'confirmed',
      width: 90,
      render: (confirmed: boolean) =>
        confirmed ? <Tag color="green">已确认</Tag> : <Tag>待确认</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      render: (_, record) =>
        record.confirmed ? (
          <Typography.Text type="secondary">已绑定</Typography.Text>
        ) : (
          <Space size={4}>
            <Typography.Link onClick={() => openConfirm(record)}>确认</Typography.Link>
            <Typography.Link
              onClick={() =>
                setExcludedKeys((prev) => new Set(prev).add(tableKey(record)))
              }
            >
              排除
            </Typography.Link>
          </Space>
        ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">主数据识别</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            扫描数据源，按实体编码/名称匹配候选主数据，确认为来源绑定
          </div>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select
          className="!w-56"
          placeholder="选择数据源"
          value={datasourceId}
          onChange={setDatasourceId}
          showSearch
          optionFilterProp="label"
          options={dataSources.map((item) => ({
            label: item.name || String(item.id),
            value: item.id,
          }))}
        />
        <Input
          allowClear
          className="!w-56"
          placeholder="按表名搜索"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={runScan}
        />
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" loading={scanning} onClick={runScan}>
          扫描
        </YakButton>
      </div>

      <div className="mt-5">
        <div className="mb-2 text-[15px] font-semibold">数据源表清单（候选标注）</div>
        <Table<MdmTableCandidate>
          rowKey={tableKey}
          columns={columns}
          dataSource={visibleTables}
          loading={scanning}
          size="middle"
          locale={{
            emptyText: (
              <YakEmpty
                compact
                title="暂无表数据"
                description="选择数据源后点击「扫描」，候选表按实体编码/名称自动标注"
              />
            ),
          }}
          pagination={{ pageSize: 20, showTotal: (count) => `共 ${count} 张表` }}
        />
      </div>

      <div className="mt-6">
        <div className="mb-2 text-[15px] font-semibold">已确认来源</div>
        <Table<MdmSourceRecord>
          rowKey="id"
          size="middle"
          pagination={false}
          dataSource={sources}
          locale={{ emptyText: <YakEmpty compact title="暂无已确认来源" description="在上方扫描结果中确认候选表为主数据来源" /> }}
          columns={[
            { title: '实体', width: 160, render: (_, record) => `${record.entityName}（${record.entityCode}）` },
            { title: '数据源', dataIndex: 'datasourceName', width: 140 },
            { title: '库', dataIndex: 'database', width: 130, render: (value?: string) => value || '-' },
            { title: '表', dataIndex: 'table' },
            {
              title: '映射',
              key: 'mapping',
              width: 110,
              render: (_, record) => {
                const count = Object.keys(record.fieldMapping ?? {}).length;
                return count > 0 ? (
                  <Tag color="blue">{count} 项自定义</Tag>
                ) : (
                  <Typography.Text type="secondary">同名回退</Typography.Text>
                );
              },
            },
            {
              title: '角色',
              dataIndex: 'role',
              width: 100,
              render: (value: MdmSourceRole) => <Tag color={value === 'MAIN' ? 'green' : 'default'}>{ROLE_LABELS[value]}</Tag>,
            },
            {
              title: '落地采集',
              key: 'landing',
              width: 260,
              render: (_, record) => <LandingStatusCell status={collectBySource.get(record.id)} />,
            },
            {
              title: '操作',
              key: 'action',
              width: 170,
              render: (_, record) => (
                <Space size={8}>
                  {collectBySource.get(record.id) ? (
                    <RunLandingLink sourceId={record.id} onDone={reloadCollect} />
                  ) : (
                    <Typography.Link onClick={() => setLandingTarget(record)}>生成落地任务</Typography.Link>
                  )}
                  <Typography.Link type="danger" onClick={() => unbind(record)}>
                    解绑
                  </Typography.Link>
                </Space>
              ),
            },
          ]}
        />
      </div>

      <Modal
        title="确认为主数据来源"
        open={confirmOpen}
        onOk={submitConfirm}
        confirmLoading={saving}
        onCancel={() => setConfirmOpen(false)}
        okText="确认"
        cancelText="取消"
        destroyOnClose
      >
        <div className="mb-4 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
          表：{confirmTarget?.database ? `${confirmTarget.database}.` : ''}
          {confirmTarget?.schema ? `${confirmTarget.schema}.` : ''}
          {confirmTarget?.name}
        </div>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="entityId"
            label="归属实体"
            rules={[{ required: true, message: '请选择主数据实体' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择实体（如 客户）"
              onChange={(value?: number) => setConfirmEntityId(value)}
              options={entities.map((entity) => ({
                label: `${entity.name}（${entity.code}）`,
                value: entity.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="role" label="来源角色" rules={[{ required: true, message: '请选择来源角色' }]}>
            <Select
              options={[
                { label: '主来源（MAIN）', value: 'MAIN' },
                { label: '辅助来源（AUXILIARY）', value: 'AUXILIARY' },
              ]}
            />
          </Form.Item>
          <Form.Item label="字段映射（属性 ← 源列，同名自动匹配）">
            <FieldMappingEditor
              key={`${confirmTarget ? tableKey(confirmTarget) : ''}-${confirmEntityId ?? ''}-${datasourceId ?? ''}`}
              datasourceId={datasourceId}
              table={
                confirmTarget
                  ? {
                      database: confirmTarget.database,
                      schema: confirmTarget.schema,
                      name: confirmTarget.name,
                    }
                  : null
              }
              entityId={confirmEntityId}
              value={fieldMapping}
              onChange={setFieldMapping}
              onIssueChange={setMappingIssue}
            />
          </Form.Item>
        </Form>
      </Modal>

      <LandingTaskModal
        open={!!landingTarget}
        sourceId={landingTarget?.id}
        sourceTable={landingTarget ? `${landingTarget.database ? `${landingTarget.database}.` : ''}${landingTarget.table}` : undefined}
        onClose={() => setLandingTarget(null)}
        onSuccess={reloadCollect}
      />
    </div>
  );
};

export default MdmIdentificationPage;

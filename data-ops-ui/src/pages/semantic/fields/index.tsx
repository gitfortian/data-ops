import usePermissionAccess from '@/hooks/usePermissionAccess';
import { Alert, Button, Form, Input, Modal, message, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  bindSemanticProcessField,
  createSemanticField,
  changeSemanticFieldStatus,
  deleteSemanticField,
  getCodeSetOptions,
  getStandardOptions,
  listSemanticProcessFields,
  pageSemanticFields,
  pageSemanticProcesses,
  unbindSemanticProcessField,
  updateSemanticField,
} from '@/services/semantic/api';
import type {
  SemanticCodeSetOption,
  SemanticFieldRecord,
  SemanticFieldRole,
  SemanticProcessRecord,
  SemanticStandardKind,
  SemanticStandardOption,
} from '@/services/semantic/types';

const ROLE_LABELS: Record<SemanticFieldRole, string> = {
  PROCESS: '业务过程',
  DIMENSION: '维度',
  METRIC: '度量',
};

const ROLE_COLORS: Record<SemanticFieldRole, string> = {
  PROCESS: 'blue',
  DIMENSION: 'gold',
  METRIC: 'green',
};

const FIELD_ROLE_OPTIONS = [
  { label: '业务过程', value: 'PROCESS' },
  { label: '维度', value: 'DIMENSION' },
  { label: '度量', value: 'METRIC' },
];

/** 角色适配的引用项(2026-09-16):类型引用三类必填单独渲染;roles 决定显隐,required 为该类角色必填。 */
const REF_SELECTS: readonly {
  key: 'stdUnitId' | 'stdCaliberId' | 'stdCodeSetCode' | 'stdSecurityId';
  label: string;
  kind: SemanticStandardKind;
  roles: readonly SemanticFieldRole[];
  required: boolean;
}[] = [
  { key: 'stdUnitId', label: '单位标准', kind: 'UNIT', roles: ['METRIC'], required: true },
  // 口径选填(2026-09-17):作为该字段的默认推荐口径,指标可覆盖
  { key: 'stdCaliberId', label: '口径标准', kind: 'CALIBER', roles: ['METRIC'], required: false },
  { key: 'stdCodeSetCode', label: '码值标准', kind: 'CODE', roles: ['DIMENSION'], required: false },
  { key: 'stdSecurityId', label: '安全标准', kind: 'SECURITY', roles: ['DIMENSION'], required: false },
];

/** 单位推荐(非强制):type_code→unit_type 匹配;时间类不预填,时长类度量仍需手选时间单位。 */
const TYPE_CODE_TO_UNIT_TYPE: Record<string, string> = {
  amount: '金额',
  amount_tax: '金额',
  balance: '金额',
  cost: '金额',
  discount: '金额',
  freight: '金额',
  count: '数量',
  quantity: '数量',
  date: '时间',
  datetime: '时间',
  duration: '时间',
  interval: '时间',
  percent: '比率',
  ratio: '比率',
  length: '长度',
  weight: '重量',
  volume: '体积',
  energy: '能量',
};

const FieldsPage = () => {
  const { can } = usePermissionAccess();
  const [activeTab, setActiveTab] = useState('library');
  // 字段库
  const [records, setRecords] = useState<SemanticFieldRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [role, setRole] = useState<SemanticFieldRole | ''>('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticFieldRecord | null>(null);
  const [standardOptions, setStandardOptions] = useState<
    Partial<Record<SemanticStandardKind, SemanticStandardOption[]>>
  >({});
  // 码值标准引用按码集聚合(32.1):value = code_set_code,与字段表存储语义对齐
  const [codeSetOptions, setCodeSetOptions] = useState<SemanticCodeSetOption[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState(false);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  // 收紧 1:引用类型标准时生效类型只读,以标准定义为准
  const typeRefId = Form.useWatch('stdTypeId', form);
  // 按角色适配(2026-09-16):角色决定引用项显隐与必填(区别于列表过滤的 role state)
  const editorRole = Form.useWatch('role', form) as SemanticFieldRole | undefined;
  // 过程引用
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [processesLoading, setProcessesLoading] = useState(false);
  const [processId, setProcessId] = useState<number | undefined>(undefined);
  const [boundFields, setBoundFields] = useState<SemanticFieldRecord[]>([]);
  const [boundFieldsError, setBoundFieldsError] = useState(false);
  const [candidateFields, setCandidateFields] = useState<SemanticFieldRecord[]>([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [candidatePageNo, setCandidatePageNo] = useState(1);
  const [candidateKeyword, setCandidateKeyword] = useState('');
  const [candidateLoading, setCandidateLoading] = useState(false);
  const candidateSearchRevision = useRef(0);
  const processSearchRevision = useRef(0);

  /** 编辑弹窗打开时按需加载引用选项(35):列表页不预载全量标准。 */
  const loadEditorOptions = useCallback(async () => {
    setOptionsLoading(true);
    setOptionsError(false);
    try {
      const [standards, codeSets] = await Promise.all([
        getStandardOptions(['TYPE', 'UNIT', 'CALIBER', 'SECURITY']),
        getCodeSetOptions(),
      ]);
      setStandardOptions(standards ?? {});
      setCodeSetOptions(codeSets ?? []);
    } catch {
      setOptionsError(true);
      message.error('标准引用候选暂不可读，请重试');
    } finally {
      setOptionsLoading(false);
    }
  }, []);

  const loadFields = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      try {
        const result = await pageSemanticFields({
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          role: role || undefined,
          keyword: keyword || undefined,
        });
        setRecords(result.bizData ?? []);
        setTotal(result.pagination?.total ?? 0);
      } catch {
        message.error('加载标准字段失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    },
    [role, keyword],
  );

  const loadProcesses = useCallback(async (searchText: string) => {
    const revision = ++processSearchRevision.current;
    setProcessesLoading(true);
    try {
      const result = await pageSemanticProcesses({ pageNo: 1, pageSize: 20, keyword: searchText.trim() || undefined });
      if (revision === processSearchRevision.current) setProcesses(result.bizData ?? []);
    } catch {
      if (revision === processSearchRevision.current) message.error('业务过程候选加载失败，请重试');
    } finally {
      if (revision === processSearchRevision.current) setProcessesLoading(false);
    }
  }, []);

  const loadCandidateFields = useCallback(async (targetPageNo: number, searchText: string) => {
    const revision = ++candidateSearchRevision.current;
    setCandidateLoading(true);
    try {
      const result = await pageSemanticFields({
        pageNo: targetPageNo,
        pageSize: 10,
        keyword: searchText.trim() || undefined,
      });
      if (revision === candidateSearchRevision.current) {
        setCandidateFields(result.bizData ?? []);
        setCandidateTotal(result.pagination?.total ?? 0);
      }
    } catch {
      if (revision === candidateSearchRevision.current) {
        setCandidateFields([]);
        setCandidateTotal(0);
        message.error('字段候选加载失败，请重试');
      }
    } finally {
      if (revision === candidateSearchRevision.current) setCandidateLoading(false);
    }
  }, []);

  const loadBoundFields = useCallback(async (targetProcessId: number) => {
    setBoundFields([]);
    setBoundFieldsError(false);
    try {
      const list = await listSemanticProcessFields(targetProcessId);
      setBoundFields(list ?? []);
    } catch {
      setBoundFields([]);
      setBoundFieldsError(true);
      message.error('过程字段引用加载失败，可重试');
    }
  }, []);

  useEffect(() => {
    void loadFields(pageNo, pageSize);
  }, [pageNo, pageSize, role, loadFields]);

  useEffect(() => {
    void loadProcesses('');
  }, [loadProcesses]);

  useEffect(() => {
    void loadCandidateFields(candidatePageNo, candidateKeyword);
  }, [candidatePageNo, candidateKeyword, loadCandidateFields]);

  useEffect(() => {
    if (processId) {
      void loadBoundFields(processId);
    } else {
      setBoundFields([]);
    }
  }, [processId, loadBoundFields]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ role: 'DIMENSION' });
    setEditorOpen(true);
    void loadEditorOptions();
  };

  const openEdit = (record: SemanticFieldRecord) => {
    setEditing(record);
    form.setFieldsValue({ ...record });
    setEditorOpen(true);
    void loadEditorOptions();
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        if (editing.version === undefined) {
          message.error('数据版本缺失，请刷新列表后重试');
          return;
        }
        const { code: _ignored, ...rest } = values;
        await updateSemanticField(editing.id, { ...rest, id: editing.id, version: editing.version });
        message.success('标准字段已更新');
      } else {
        await createSemanticField(values);
        message.success('标准字段已创建');
      }
      setEditorOpen(false);
      await loadFields(pageNo, pageSize);
    } catch {
      message.error('保存失败（数据可能已被他人修改，或标准引用不合法），请刷新后重试');
    } finally {
      setSaving(false);
    }
  };

  const removeField = (record: SemanticFieldRecord) => {
    Modal.confirm({
      title: '删除标准字段',
      content: `确定删除「${record.name}（${record.code}）」？被业务过程引用时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSemanticField(record.id);
          message.success('已删除');
          await loadFields(pageNo, pageSize);
        } catch {
          message.error('删除失败，请稍后重试');
        }
      },
    });
  };

  const bindField = async (fieldId: number) => {
    if (!processId) {
      return;
    }
    try {
      await bindSemanticProcessField(processId, fieldId, false);
      await loadBoundFields(processId);
    } catch {
      message.error('绑定失败（可能已绑定），请检查后重试');
    }
  };

  const unbindField = async (fieldId: number) => {
    if (!processId) {
      return;
    }
    try {
      await unbindSemanticProcessField(processId, fieldId);
      await loadBoundFields(processId);
    } catch {
      message.error('解绑失败，请稍后重试');
    }
  };

  const fieldColumns = [
    {
      title: '编码',
      dataIndex: 'code',
      width: 160,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (value: SemanticFieldRole) => <Tag color={ROLE_COLORS[value]}>{ROLE_LABELS[value] ?? value}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value?: string) =>
        value === 'DISABLED' ? <Tag color="default">停用</Tag> : <Tag color="green">启用</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 90,
      render: (value?: string) =>
        value === 'PRESET' ? (
          <Tag color="processing">预置</Tag>
        ) : value === 'CAPTURE' ? (
          <Tag color="cyan">沉淀</Tag>
        ) : (
          <Tag>手工</Tag>
        ),
    },
    {
      title: '类型标准',
      dataIndex: 'stdTypeName',
      width: 180,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '单位标准',
      dataIndex: 'stdUnitName',
      width: 150,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '默认口径',
      dataIndex: 'stdCaliberName',
      width: 150,
      ellipsis: true,
      render: (value?: string) => value || '未设置',
    },
    { title: '描述', dataIndex: 'businessDesc', ellipsis: true },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      render: (_: unknown, record: SemanticFieldRecord) => (
        <Space size={0}>
          {can('semantic:update') && <>
            <Button type="link" size="small" onClick={async () => {
              try {
                await changeSemanticFieldStatus(record.id, record.status === 'DISABLED' ? 'ENABLED' : 'DISABLED');
                message.success(record.status === 'DISABLED' ? '字段已恢复' : '字段已停用');
                await loadFields(pageNo, pageSize);
                if (processId) await loadBoundFields(processId);
              } catch {
                message.error('状态更新失败，请刷新后检查字段引用和权限');
              }
            }}>{record.status === 'DISABLED' ? '恢复' : '停用'}</Button>
            <Button type="link" size="small" onClick={() => openEdit(record)}>编辑</Button>
          </>}
          {can('semantic:delete') && <Button type="link" size="small" danger onClick={() => removeField(record)}>删除</Button>}
        </Space>
      ),
    },
  ];

  const libraryTab = (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Space wrap>
          <Select
            allowClear
            placeholder="全部角色"
            style={{ width: 140 }}
            value={role || undefined}
            onChange={(value) => {
              setRole((value ?? '') as SemanticFieldRole | '');
              setPageNo(1);
            }}
            options={FIELD_ROLE_OPTIONS}
          />
          <Input.Search
            allowClear
            placeholder="按编码或名称搜索"
            className="!w-[220px]"
            onSearch={(value) => {
              setKeyword(value.trim());
              setPageNo(1);
            }}
          />
        </Space>
        {can('semantic:create') && <YakButton
          type="primary"
          className="!h-9 !rounded-lg !px-4 !text-white"
          onClick={() => {
          openCreate();
          }}
        >
          新建标准字段
        </YakButton>}
      </div>
      <Table<SemanticFieldRecord>
        className="mt-4"
        rowKey="id"
        loading={loading}
        columns={fieldColumns}
        dataSource={records}
        locale={{
          emptyText: (
            <YakEmpty compact title="还没有标准字段" description="点击右上角「新建标准字段」开始沉淀字段定义" />
          ),
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, size) => {
            setPageNo(page);
            setPageSize(size);
          },
        }}
      />
    </>
  );

  const processTab = (
    <div>
      <Space wrap className="mb-4">
        <Typography.Text>选择业务过程：</Typography.Text>
        <Select
          showSearch
          filterOption={false}
          placeholder="选择业务过程"
          style={{ width: 320 }}
          loading={processesLoading}
          value={processId}
          onSearch={(value) => void loadProcesses(value)}
          onFocus={() => {
            if (processes.length === 0) void loadProcesses('');
          }}
          onChange={(value) => setProcessId(value)}
          options={processes.map((item) => ({
            label: `${item.name}（${item.code}）`,
            value: item.id,
          }))}
        />
      </Space>
      {processId ? (
        <div className="grid grid-cols-2 gap-6 max-md:grid-cols-1">
          <div>
            <Typography.Text strong>已引用字段（按顺序）</Typography.Text>
            {boundFieldsError && (
              <div className="mt-2">
                <Button size="small" onClick={() => void loadBoundFields(processId)}>重试加载引用</Button>
              </div>
            )}
            <div className="mt-2 flex flex-col gap-2">
              {boundFieldsError ? (
                <Typography.Text type="secondary">引用暂不可用，重试前不会按空列表处理</Typography.Text>
              ) : boundFields.length === 0 ? (
                <Typography.Text type="secondary">尚未引用字段，从右侧字段库绑定</Typography.Text>
              ) : (
                boundFields.map((field) => (
                  <div
                    key={field.id}
                    className="flex items-center justify-between rounded border border-[#f0f0f0] px-3 py-2"
                  >
                    <Space size={8}>
                      <Tag color={ROLE_COLORS[field.role]}>{ROLE_LABELS[field.role]}</Tag>
                      <Typography.Text className="!text-[13px]">{field.name}</Typography.Text>
                      <Typography.Text type="secondary" className="!text-[12px]">
                        {field.code}
                      </Typography.Text>
                    </Space>
                    {can('semantic:update') && <Button
                      type="link" size="small" danger
                      onClick={() => {
                        void unbindField(field.id);
                      }}
                    >解绑</Button>}
                  </div>
                ))
              )}
            </div>
          </div>
          <div>
            <Typography.Text strong>从字段库绑定</Typography.Text>
            <div className="mt-2 flex max-h-[420px] flex-col gap-2 overflow-auto">
            <Input.Search
              allowClear
              placeholder="按字段名称或编码搜索"
              enterButton="搜索"
              onSearch={(value) => {
                setCandidatePageNo(1);
                setCandidateKeyword(value);
              }}
            />
            <Table<SemanticFieldRecord>
              className="mt-2"
              size="small"
              rowKey="id"
              loading={candidateLoading}
              dataSource={candidateFields}
              pagination={{
                current: candidatePageNo,
                pageSize: 10,
                total: candidateTotal,
                showSizeChanger: false,
                showTotal: (count) => `共 ${count} 个字段`,
                onChange: (page) => setCandidatePageNo(page),
              }}
              columns={[
                {
                  title: '标准字段',
                  render: (_: unknown, field: SemanticFieldRecord) => (
                    <Space size={6}>
                      <Tag color={ROLE_COLORS[field.role]}>{ROLE_LABELS[field.role]}</Tag>
                      <Typography.Text>{field.name}</Typography.Text>
                      <Typography.Text type="secondary" code>{field.code}</Typography.Text>
                      {field.status === 'DISABLED' && <Tag>停用</Tag>}
                    </Space>
                  ),
                },
                {
                  title: '操作',
                  width: 90,
                  render: (_: unknown, field: SemanticFieldRecord) => {
                    const alreadyBound = boundFields.some((bound) => bound.id === field.id);
                    return (
                      <Button
                        type="link"
                        size="small"
                        disabled={!can('semantic:update') || alreadyBound || field.status === 'DISABLED'}
                        onClick={() => void bindField(field.id)}
                      >
                        {alreadyBound ? '已引用' : '引用'}
                      </Button>
                    );
                  },
                },
              ]}
            />
            </div>
          </div>
        </div>
      ) : (
        <YakEmpty compact title="请选择业务过程" description="选择后可查看与编辑其标准字段引用" />
      )}
    </div>
  );

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="text-[20px] font-semibold leading-7">标准字段</div>
      <div className="mt-1 text-[13px] text-[#667085]">
        全局字段库：字段引用数据标准（不自由填写），业务过程按引用组装；派生建模由此带出字段
      </div>
      <Tabs
        className="mt-3"
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'library', label: '字段库', children: libraryTab },
          { key: 'process', label: '过程引用', children: processTab },
        ]}
      />

      <Modal
        open={editorOpen}
        title={editing ? '编辑标准字段' : '新建标准字段'}
        width={640}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          void submitEditor();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          {optionsError && <Alert className="mb-3" type="warning" showIcon message="标准引用选项未能加载" action={<Button size="small" onClick={() => void loadEditorOptions()}>重试</Button>} />}
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="code"
              label="编码"
              rules={[
                { required: true, message: '请输入编码' },
                {
                  pattern: /^[A-Za-z0-9_]{1,64}$/,
                  message: '仅允许字母、数字和下划线，1~64 位',
                },
              ]}
            >
              <Input disabled={Boolean(editing)} maxLength={64} placeholder="如 order_no" />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input maxLength={128} placeholder="字段名称" />
            </Form.Item>
          </div>
          <Form.Item name="role" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select options={FIELD_ROLE_OPTIONS} placeholder="选择字段角色（决定引用项）" />
          </Form.Item>
          {/* 按角色适配(2026-09-16):角色决定引用项显隐;preserve=false 切换即清空隐藏项 */}
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="stdTypeId"
              label="类型标准引用"
              preserve={false}
              rules={[{ required: true, message: '请选择类型标准' }]}
            >
              <Select
                allowClear
                showSearch
                loading={optionsLoading}
                optionFilterProp="label"
                placeholder="从类型标准中选择"
                options={(standardOptions.TYPE ?? []).map((standard) => ({
                  label: `${standard.name}（${standard.code}）`,
                  value: standard.id,
                }))}
                onChange={(value) => {
                  const hit = (standardOptions.TYPE ?? []).find((standard) => standard.id === value);
                  // data_type 提示刷新:选中类型标准后回填其标准类型
                  form.setFieldValue('dataType', hit?.stdType);
                  // 单位推荐(非强制):type_code→unit_type 匹配首个启用单位,仅度量展示单位
                  const unitType = hit?.typeCode ? TYPE_CODE_TO_UNIT_TYPE[hit.typeCode] : undefined;
                  const suggested = unitType
                    ? (standardOptions.UNIT ?? []).find((unit) => unit.unitType === unitType)
                    : undefined;
                  if (editorRole === 'METRIC') {
                    form.setFieldValue('stdUnitId', suggested?.id);
                  }
                }}
              />
            </Form.Item>
            <Form.Item
              name="dataType"
              label="生效类型"
              extra="必需的类型标准决定字段生效类型"
            >
              <Input
                disabled={!typeRefId}
                maxLength={64}
                placeholder={typeRefId ? '跟随类型标准' : '请先选择类型标准'}
              />
            </Form.Item>
            {REF_SELECTS.filter((item) => editorRole !== undefined && item.roles.includes(editorRole)).map((item) => {
              const refOptions: { label: string; value: string | number }[] =
                item.key === 'stdCodeSetCode'
                  ? codeSetOptions.map((option) => ({
                      label: `${option.name} (${option.codeSetCode})`,
                      value: option.codeSetCode,
                    }))
                  : (standardOptions[item.kind] ?? []).map((standard) => ({
                      label: `${standard.name}（${standard.code}）`,
                      value: standard.id,
                    }));
              return (
                <Form.Item
                  key={item.key}
                  name={item.key}
                  label={`${item.label}引用`}
                  preserve={false}
                  rules={item.required ? [{ required: true, message: `请选择${item.label}标准` }] : undefined}
                  extra={item.key === 'stdCaliberId' ? '选填，作为该字段的默认口径；指标可覆盖' : undefined}
                >
                  <Select
                    allowClear
                    showSearch
                    loading={optionsLoading}
                    optionFilterProp="label"
                    placeholder={
                      item.required
                        ? `请选择${item.label}标准`
                        : item.key === 'stdCodeSetCode'
                          ? '选择码集（字段值域）'
                          : `从${item.label}中选择`
                    }
                    options={refOptions}
                  />
                </Form.Item>
              );
            })}
          </div>
          <Form.Item name="businessDesc" label="业务描述">
            <Input.TextArea rows={2} maxLength={512} placeholder="字段的业务含义（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default FieldsPage;

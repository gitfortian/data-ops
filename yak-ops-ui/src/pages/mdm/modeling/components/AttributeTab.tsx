import { Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  createMdmAttribute,
  deleteMdmAttribute,
  listMdmAttributes,
  updateMdmAttribute,
} from '@/services/mdm/api';
import { getCodeSetOptions, getStandardOptions } from '@/services/semantic/api';
import type { SemanticCodeSetOption, SemanticStandardKind, SemanticStandardOption } from '@/services/semantic/types';
import type { MdmAttributeRecord, MdmAttributeType } from '@/services/mdm/types';

const ATTR_TYPE_LABELS: Record<MdmAttributeType, string> = {
  PK: 'PK（唯一标识）',
  ATTR: '属性',
  RELATION: '关系',
};

const ATTR_TYPE_OPTIONS = [
  { label: 'PK（唯一标识）', value: 'PK' },
  { label: '属性', value: 'ATTR' },
  { label: '关系', value: 'RELATION' },
];

interface AttributeFormValues {
  code?: string;
  name: string;
  attrType: MdmAttributeType;
  dataType?: string;
  stdTypeId?: number;
  stdUnitId?: number;
  stdCodeSetCode?: string;
  stdSecurityId?: number;
  required?: boolean;
  businessDesc?: string;
  sortOrder?: number;
}

/** 属性引用下拉定义:类型/单位/安全走标准 options,码值走码集 options。 */
const REF_SELECTS: {
  key: 'stdTypeId' | 'stdUnitId' | 'stdSecurityId' | 'stdCodeSetCode';
  label: string;
  kind?: SemanticStandardKind;
}[] = [
  { key: 'stdTypeId', label: '类型', kind: 'TYPE' },
  { key: 'stdUnitId', label: '单位', kind: 'UNIT' },
  { key: 'stdSecurityId', label: '安全', kind: 'SECURITY' },
  { key: 'stdCodeSetCode', label: '码值' },
];

/** 实体详情"属性"Tab(ticket 52):标准引用必须来自数据标准,弹窗打开时按需加载选项。 */
const AttributeTab = ({ entityId }: { entityId: number }) => {
  const [form] = Form.useForm<AttributeFormValues>();
  const [attributes, setAttributes] = useState<MdmAttributeRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<MdmAttributeRecord | null>(null);
  const [saving, setSaving] = useState(false);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [standardOptions, setStandardOptions] = useState<
    Partial<Record<SemanticStandardKind, SemanticStandardOption[]>>
  >({});
  const [codeSetOptions, setCodeSetOptions] = useState<SemanticCodeSetOption[]>([]);

  const loadAttributes = useCallback(async () => {
    setLoading(true);
    try {
      setAttributes(await listMdmAttributes(entityId));
    } catch {
      setAttributes([]);
      message.error('加载主数据属性失败');
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    void loadAttributes();
  }, [loadAttributes]);

  /** 编辑弹窗打开时按需加载引用选项(52,同 semantic 32.1):列表页不预载全量标准。 */
  const loadEditorOptions = useCallback(async () => {
    setOptionsLoading(true);
    try {
      const [standards, codeSets] = await Promise.all([
        getStandardOptions(['TYPE', 'UNIT', 'SECURITY']),
        getCodeSetOptions(),
      ]);
      setStandardOptions(standards ?? {});
      setCodeSetOptions(codeSets ?? []);
    } catch {
      setStandardOptions({});
      setCodeSetOptions([]);
    } finally {
      setOptionsLoading(false);
    }
  }, []);

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
    form.resetFields();
    void loadEditorOptions();
  };

  const openEdit = (record: MdmAttributeRecord) => {
    setEditing(record);
    setEditorOpen(true);
    form.setFieldsValue({
      name: record.name,
      attrType: record.type,
      dataType: record.dataType,
      stdTypeId: record.stdTypeId,
      stdUnitId: record.stdUnitId,
      stdCodeSetCode: record.stdCodeSetCode,
      stdSecurityId: record.stdSecurityId,
      required: record.required,
      businessDesc: record.businessDesc,
      sortOrder: record.sortOrder,
    });
    void loadEditorOptions();
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateMdmAttribute(entityId, editing.id, {
          name: values.name,
          attrType: values.attrType,
          dataType: values.dataType,
          stdTypeId: values.stdTypeId,
          stdUnitId: values.stdUnitId,
          stdCodeSetCode: values.stdCodeSetCode,
          stdSecurityId: values.stdSecurityId,
          required: values.required,
          businessDesc: values.businessDesc,
          sortOrder: values.sortOrder,
        });
        message.success('属性已更新');
      } else {
        await createMdmAttribute(entityId, {
          code: values.code ?? '',
          name: values.name,
          attrType: values.attrType,
          dataType: values.dataType,
          stdTypeId: values.stdTypeId,
          stdUnitId: values.stdUnitId,
          stdCodeSetCode: values.stdCodeSetCode,
          stdSecurityId: values.stdSecurityId,
          required: values.required,
          businessDesc: values.businessDesc,
          sortOrder: values.sortOrder,
        });
        message.success('属性已创建');
      }
      setEditorOpen(false);
      await loadAttributes();
    } catch {
      message.error('保存失败（编码可能已存在或标准引用不可用），请检查后重试');
    } finally {
      setSaving(false);
    }
  };

  const removeAttribute = (record: MdmAttributeRecord) => {
    Modal.confirm({
      title: '删除属性',
      content: `确定删除「${record.name}（${record.code}）」？已被采集映射/清洗规则引用时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMdmAttribute(entityId, record.id);
          message.success('已删除');
          await loadAttributes();
        } catch {
          message.error('删除失败（可能存在采集映射或清洗规则引用）');
        }
      },
    });
  };

  const refOptions = (key: (typeof REF_SELECTS)[number]['key']): { label: string; value: string | number }[] =>
    key === 'stdCodeSetCode'
      ? codeSetOptions.map((option) => ({
          label: `${option.name} (${option.codeSetCode})`,
          value: option.codeSetCode,
        }))
      : (standardOptions[key === 'stdTypeId' ? 'TYPE' : key === 'stdUnitId' ? 'UNIT' : 'SECURITY'] ??
        []).map((standard) => ({
          label: `${standard.name}（${standard.code}）`,
          value: standard.id,
        }));

  const columns: ColumnsType<MdmAttributeRecord> = [
    { title: '属性编码', dataIndex: 'code', width: 140 },
    { title: '属性名称', dataIndex: 'name', width: 140 },
    {
      title: '角色',
      dataIndex: 'type',
      width: 120,
      render: (value: MdmAttributeType) => <Tag color={value === 'PK' ? 'geekblue' : 'default'}>{ATTR_TYPE_LABELS[value]}</Tag>,
    },
    { title: '数据类型', dataIndex: 'dataType', width: 120, render: (value?: string) => value || '-' },
    { title: '类型标准', dataIndex: 'stdTypeName', width: 160, render: (value?: string) => value || '-' },
    { title: '单位标准', dataIndex: 'stdUnitName', width: 160, render: (value?: string) => value || '-' },
    { title: '码集', dataIndex: 'stdCodeSetCode', width: 140, render: (value?: string) => value || '-' },
    { title: '安全标准', dataIndex: 'stdSecurityName', width: 160, render: (value?: string) => value || '-' },
    { title: '必填', dataIndex: 'required', width: 70, render: (value: boolean) => (value ? '是' : '否') },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          <Typography.Link type="danger" onClick={() => removeAttribute(record)}>
            删除
          </Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className="mb-3 flex justify-end">
        <YakButton type="primary" className="!h-8 !rounded-lg !px-3 !text-white" onClick={openCreate}>
          新建属性
        </YakButton>
      </div>
      <Table<MdmAttributeRecord>
        rowKey="id"
        columns={columns}
        dataSource={attributes}
        loading={loading}
        size="middle"
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无主数据属性"
              description="类型/单位/码值/安全引用语义中心数据标准，不自由填写"
            />
          ),
        }}
        pagination={false}
      />

      <Modal
        title={editing ? '编辑属性（编码不可修改）' : '新建属性'}
        open={editorOpen}
        onOk={submitEditor}
        confirmLoading={saving}
        onCancel={() => setEditorOpen(false)}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false} initialValues={{ required: false, sortOrder: 0 }}>
          <div className="grid grid-cols-2 gap-x-4">
            {!editing ? (
              <Form.Item
                name="code"
                label="属性编码"
                rules={[
                  { required: true, message: '请输入属性编码' },
                  { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '仅允许字母、数字和下划线，1~64 位' },
                ]}
              >
                <Input placeholder="如 cust_name" />
              </Form.Item>
            ) : null}
            <Form.Item
              name="name"
              label="属性名称"
              rules={[{ required: true, message: '请输入属性名称' }]}
            >
              <Input placeholder="如 客户名称" />
            </Form.Item>
            <Form.Item
              name="attrType"
              label="角色"
              rules={[{ required: true, message: '请选择属性角色' }]}
            >
              <Select options={ATTR_TYPE_OPTIONS} placeholder="PK/属性/关系" />
            </Form.Item>
            <Form.Item name="dataType" label="数据类型">
              <Input maxLength={64} placeholder="如 DECIMAL(18,2)" />
            </Form.Item>
            {REF_SELECTS.map((item) => (
              <Form.Item key={item.key} name={item.key} label={`${item.label}标准引用`}>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  loading={optionsLoading}
                  placeholder={item.key === 'stdCodeSetCode' ? '选择启用码集' : '选择启用标准（可留空）'}
                  options={refOptions(item.key)}
                />
              </Form.Item>
            ))}
            <Form.Item name="required" label="是否必填" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序（小在前）">
              <InputNumber className="!w-full" min={0} />
            </Form.Item>
          </div>
          <Form.Item name="businessDesc" label="业务描述">
            <Input.TextArea rows={2} maxLength={512} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AttributeTab;

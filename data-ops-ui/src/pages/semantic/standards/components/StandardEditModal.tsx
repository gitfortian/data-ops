import { Form, Input, Modal, message, Select, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { SEMANTIC_STANDARD_KINDS } from '@/pages/semantic/constants';
import { createSemanticStandard, updateSemanticStandard } from '@/services/semantic/api';
import type { SemanticStandardKind, SemanticStandardRecord } from '@/services/semantic/types';

interface StandardEditModalProps {
  open: boolean;
  editing: SemanticStandardRecord | null;
  onClose: () => void;
  onSaved: () => void;
  /** 新建时选 CODE 类别,切换到 CodeSetEditModal。 */
  onSwitchToCodeSet: () => void;
}

interface FormValues {
  kind: SemanticStandardKind;
  code: string;
  name: string;
  description?: string;
  sortOrder?: number;
  /** 命名标准 */
  scope?: string;
  ruleExpr?: string;
  example?: string;
  /** 类型标准 */
  typeCode?: string;
  stdType?: string;
  sourceMapping?: string;
  /** 单位标准 */
  unitCode?: string;
  unitType?: string;
  /** 口径标准 */
  caliberCode?: string;
  calRule?: string;
  businessDesc?: string;
  /** 安全标准 */
  levelCode?: string;
  maskRule?: string;
}

const StandardEditModal = ({ open, editing, onClose, onSaved, onSwitchToCodeSet }: StandardEditModalProps) => {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const kind = Form.useWatch('kind', form);

  const isEdit = Boolean(editing);

  const resetForm = useCallback(() => {
    if (editing) {
      form.setFieldsValue({
        kind: editing.kind,
        code: editing.code,
        name: editing.name,
        description: editing.description,
        sortOrder: editing.sortOrder,
        scope: editing.scope as FormValues['scope'],
        ruleExpr: editing.ruleExpr,
        example: editing.example,
        typeCode: editing.typeCode,
        stdType: editing.stdType,
        sourceMapping: editing.sourceMapping,
        unitCode: editing.unitCode,
        unitType: editing.unitType,
        caliberCode: editing.caliberCode,
        calRule: editing.calRule,
        businessDesc: editing.businessDesc,
        levelCode: editing.levelCode,
        maskRule: editing.maskRule,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ kind: 'NAMING' });
    }
  }, [editing, form]);

  useEffect(() => {
    if (open) {
      resetForm();
    }
  }, [open, resetForm]);

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        const { kind: _ignoredKind, code: _ignoredCode, ...rest } = values;
        await updateSemanticStandard(editing.id, rest);
        message.success('标准已更新');
      } else {
        await createSemanticStandard(values);
        message.success('标准已创建');
      }
      onSaved();
      onClose();
    } catch {
      // 请求失败提示由统一拦截器处理
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={isEdit ? '编辑数据标准' : '新建数据标准'}
      width={640}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      destroyOnClose
      onCancel={onClose}
      onOk={() => {
        void submit();
      }}
    >
      <Form form={form} layout="vertical" className="pt-2">
        <div className="grid grid-cols-2 gap-x-4">
          <Form.Item
            name="kind"
            label="类别"
            rules={[{ required: true, message: '请选择类别' }]}
            extra={
              isEdit
                ? '类别创建后不可修改'
                : '各类别将展示对应的专有字段；选择「码值标准」会转入码集维护（整组录入码值）'
            }
          >
            <Select
              disabled={isEdit}
              options={SEMANTIC_STANDARD_KINDS.map(({ value, label }) => ({ value, label }))}
              onChange={(value: SemanticStandardKind) => {
                if (value === 'CODE' && !isEdit) {
                  onClose();
                  onSwitchToCodeSet();
                }
              }}
            />
          </Form.Item>
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
            extra={isEdit ? <Typography.Text type="secondary">编码不可修改</Typography.Text> : undefined}
          >
            <Input disabled={isEdit} maxLength={64} placeholder="如 ods_table_prefix" />
          </Form.Item>
        </div>
        <div className="grid grid-cols-2 gap-x-4">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={128} placeholder="标准名称" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" placeholder="0" />
          </Form.Item>
        </div>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} maxLength={512} placeholder="标准的用途说明（可选）" />
        </Form.Item>

        {/* preserve={false}:切换类别即清除非当前类别的专有字段,避免脏值入库 */}
        {kind === 'NAMING' ? (
          <>
            <Form.Item name="scope" label="适用范围" preserve={false}>
              <Select
                allowClear
                placeholder="可空 = 通用"
                options={[
                  { label: '表（TABLE）', value: 'TABLE' },
                  { label: '字段（FIELD）', value: 'FIELD' },
                  { label: '库（DATABASE）', value: 'DATABASE' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="ruleExpr"
              label="规则表达式"
              preserve={false}
              rules={[{ required: true, message: '命名标准必须提供规则表达式' }]}
            >
              <Input.TextArea rows={2} maxLength={1024} placeholder="如 ^ods_[a-z][a-z0-9_]*$" />
            </Form.Item>
            <Form.Item name="example" label="示例" preserve={false}>
              <Input maxLength={256} placeholder="如 ods_trade_order" />
            </Form.Item>
          </>
        ) : null}

        {kind === 'TYPE' ? (
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="typeCode"
              label="类型编码"
              preserve={false}
              rules={[{ required: true, message: '类型标准必须提供类型编码' }]}
            >
              <Input maxLength={64} placeholder="如 AMOUNT" />
            </Form.Item>
            <Form.Item
              name="stdType"
              label="标准类型"
              preserve={false}
              rules={[{ required: true, message: '类型标准必须提供标准类型' }]}
            >
              <Input maxLength={64} placeholder="如 DECIMAL(18,2)" />
            </Form.Item>
            <Form.Item
              name="sourceMapping"
              label="源库类型映射（JSON）"
              preserve={false}
              className="col-span-2"
              rules={[
                {
                  validator: (_, value?: string) => {
                    if (!value) {
                      return Promise.resolve();
                    }
                    try {
                      JSON.parse(value);
                      return Promise.resolve();
                    } catch {
                      return Promise.reject(new Error('必须是合法 JSON'));
                    }
                  },
                },
              ]}
            >
              <Input.TextArea rows={2} placeholder='如 {"mysql":["DECIMAL","NUMERIC"]}' />
            </Form.Item>
          </div>
        ) : null}

        {/* CODE 类已拆分到 CodeSetEditModal,此处不再渲染 */}

        {kind === 'UNIT' ? (
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="unitCode"
              label="单位编码"
              preserve={false}
              rules={[{ required: true, message: '单位标准必须提供单位编码' }]}
            >
              <Input maxLength={64} placeholder="如 CNY" />
            </Form.Item>
            <Form.Item name="unitType" label="单位类型" preserve={false}>
              <Input maxLength={64} placeholder="如 金额（可选）" />
            </Form.Item>
          </div>
        ) : null}

        {kind === 'CALIBER' ? (
          <>
            <div className="grid grid-cols-2 gap-x-4">
              <Form.Item name="caliberCode" label="口径编码" preserve={false}>
                <Input maxLength={64} placeholder="如 active_user_cnt（可选）" />
              </Form.Item>
              <Form.Item name="businessDesc" label="业务说明" preserve={false}>
                <Input maxLength={512} placeholder="口径的业务含义（可选）" />
              </Form.Item>
            </div>
            <Form.Item
              name="calRule"
              label="口径规则"
              preserve={false}
              rules={[{ required: true, message: '口径标准必须提供口径规则' }]}
            >
              <Input.TextArea rows={2} maxLength={1024} placeholder="如 count(distinct user_id)" />
            </Form.Item>
          </>
        ) : null}

        {kind === 'SECURITY' ? (
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="levelCode"
              label="等级编码"
              preserve={false}
              rules={[{ required: true, message: '安全标准必须提供等级编码' }]}
            >
              <Input maxLength={64} placeholder="如 L3" />
            </Form.Item>
            <Form.Item
              name="maskRule"
              label="脱敏规则"
              preserve={false}
              tooltip="参考文案：标注该字段类别人期望的脱敏方式；实际执行由数据安全模块的脱敏算法负责，等级字典真源亦在数据安全模块"
            >
              <Input maxLength={512} placeholder="如 保留前 3 后 4（可选）" />
            </Form.Item>
          </div>
        ) : null}
      </Form>
    </Modal>
  );
};

export default StandardEditModal;

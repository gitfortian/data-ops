import { Form, Modal, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import CodeSetFormItems from '@/pages/semantic/standards/components/CodeSetFormItems';
import { saveCodeSet, updateCodeSet } from '@/services/semantic/api';
import type { CodeSetDetailRecord, CodeValueItem } from '@/services/semantic/types';

interface CodeSetEditModalProps {
  open: boolean;
  /** null=新建;有值=编辑(父组件提前加载码集详情)。 */
  detail: CodeSetDetailRecord | null;
  onClose: () => void;
  onSaved: () => void;
  /** 新建时码集编码已存在 → 转入编辑既有码集。 */
  onExists?: (codeSetCode: string) => void;
}

interface FormValues {
  codeSetCode: string;
  name: string;
  description?: string;
  codeValues: CodeValueItem[];
}

const CodeSetEditModal = ({ open, detail, onClose, onSaved, onExists }: CodeSetEditModalProps) => {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const isEdit = Boolean(detail);
  const isLegacy = Boolean(detail?.legacy);

  const resetForm = useCallback(() => {
    if (detail) {
      form.setFieldsValue({
        codeSetCode: detail.codeSetCode,
        name: detail.name,
        description: detail.description,
        codeValues: detail.values.map((row, index) => ({
          codeValue: row.codeValue ?? '',
          codeLabel: row.codeLabel ?? '',
          sortOrder: row.sortOrder ?? index,
        })),
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ codeValues: [{ codeValue: '', codeLabel: '', sortOrder: 0 }] });
    }
  }, [detail, form]);

  useEffect(() => {
    if (open) {
      resetForm();
    }
  }, [open, resetForm]);

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = {
        codeSetCode: values.codeSetCode,
        revision: detail?.revision,
        // 存量空码集行补全编码:携带原组键,后端据此把旧行采纳进新码集
        originCodeSetCode: isLegacy ? detail?.codeSetCode : undefined,
        name: values.name,
        description: values.description,
        values: (values.codeValues ?? []).map((row, index) => ({
          codeValue: row.codeValue ?? '',
          codeLabel: row.codeLabel || undefined,
          sortOrder: row.sortOrder ?? index,
        })),
      };
      if (detail) {
        await updateCodeSet(values.codeSetCode, payload);
      } else {
        await saveCodeSet(payload);
      }
      message.success(isEdit ? '码集已更新' : '码集已创建');
      onSaved();
      onClose();
    } catch {
      // 统一拦截器处理错误提示
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={isEdit ? '编辑码值标准' : '新建码值标准'}
      width={720}
      // 码值标准可加多行码值,720p 下正文会顶到底部按钮;正文内滚,页脚始终可见。
      styles={{ body: { maxHeight: 'calc(100dvh - 260px)', overflowY: 'auto' } }}
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
        <CodeSetFormItems
          confirmRemove={isEdit}
          persistedValues={(detail?.values ?? []).map((row) => row.codeValue ?? '').filter(Boolean)}
          codeDisabled={isEdit && !isLegacy}
          legacy={isLegacy}
          nameInconsistent={Boolean(detail?.nameInconsistent)}
          onExists={onExists}
        />
      </Form>
    </Modal>
  );
};

export default CodeSetEditModal;

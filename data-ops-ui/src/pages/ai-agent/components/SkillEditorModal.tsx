import { Alert, Form, Input, Modal, message } from 'antd';
import React from 'react';
import type { AgentSkillItem, AgentSkillSaveInput } from '@/services/agent';
import {
  parseSkillMetadata,
  SKILL_DESCRIPTION_MAX,
  SKILL_ID_PATTERN,
  SKILL_NAME_MAX,
  skillEditorInitialValues,
} from '../skill-runtime';

export interface SkillEditorModalProps {
  open: boolean;
  /** null=注册模式；非 null=更新模式（skillId 只读并回传原值）。 */
  editing: AgentSkillItem | null;
  template?: AgentSkillSaveInput;
  saving: boolean;
  onCancel: () => void;
  onSubmit: (input: AgentSkillSaveInput) => Promise<void> | void;
}

interface SkillEditorFormValues {
  skillId: string;
  name: string;
  description?: string;
  content: string;
  metadata?: string;
}

const MONO_FONT = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace';

/**
 * 技能编辑器（文档 §3.3）：注册/更新共用表单。
 * skillId 注册可编辑、更新只读；content 等宽大 TextArea；metadata 为 JSON 编辑区，
 * 提交前校验合法性（parseSkillMetadata，单一实现）。更新冲突由容器按 409 分流。
 */
const SkillEditorModal: React.FC<SkillEditorModalProps> = ({ open, editing, template, saving, onCancel, onSubmit }) => {
  const [form] = Form.useForm<SkillEditorFormValues>();
  const isUpdate = editing !== null;

  const handleFinish = async (values: SkillEditorFormValues) => {
    const metadata = parseSkillMetadata(values.metadata ?? '');
    if (metadata.error) {
      void message.error(metadata.error);
      return;
    }
    const input: AgentSkillSaveInput = {
      ...(editing ? { expectedVersion: editing.version } : {}),
      skillId: isUpdate ? editing!.skillId : values.skillId.trim(),
      name: values.name.trim(),
      content: values.content,
      ...(values.description?.trim() ? { description: values.description.trim() } : {}),
      ...(metadata.value ? { metadata: metadata.value } : {}),
    };
    await onSubmit(input);
  };

  return (
    <Modal
      open={open}
      title={isUpdate ? `编辑技能「${editing!.name}」` : '注册技能'}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={() => form.submit()}
      onCancel={onCancel}
      destroyOnHidden
    >
      {template && !isUpdate ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="已填入模板，可先调整正文。保存后将注册并启用此技能，下一轮对话生效。"
        />
      ) : null}
      <Form
        key={isUpdate ? editing!.skillId : 'create'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={skillEditorInitialValues(editing ?? template ?? null)}
        onFinish={(values) => void handleFinish(values)}
      >
        <Form.Item
          label="技能标识（skillId）"
          name="skillId"
          rules={
            isUpdate
              ? []
              : [
                  { required: true, message: '技能标识不能为空' },
                  { max: 64, message: '技能标识长度不能超过 64' },
                  { pattern: SKILL_ID_PATTERN, message: '技能标识仅支持字母、数字与中划线' },
                ]
          }
          extra={
            isUpdate
              ? '更新模式：skillId 为唯一逻辑名，不可修改'
              : template
                ? '模板标识由对应场景引用，不可修改'
                : '唯一逻辑名（字母、数字、中划线，≤64），将作为技能 Tag 展示'
          }
        >
          <Input disabled={isUpdate || !!template} placeholder="如 asset-yoy" />
        </Form.Item>
        <Form.Item
          label="技能名称（展示名）"
          name="name"
          rules={[
            { required: true, message: '技能名称不能为空' },
            { max: SKILL_NAME_MAX, message: `技能名称长度不能超过 ${SKILL_NAME_MAX}` },
          ]}
        >
          <Input placeholder="如 资产同比分析" />
        </Form.Item>
        <Form.Item
          label="技能描述（一句话，注入提示用）"
          name="description"
          rules={[{ max: SKILL_DESCRIPTION_MAX, message: `技能描述长度不能超过 ${SKILL_DESCRIPTION_MAX}` }]}
        >
          <Input.TextArea
            rows={2}
            placeholder="如 用户询问资产同比时按选定口径输出对比结论"
            maxLength={SKILL_DESCRIPTION_MAX}
          />
        </Form.Item>
        <Form.Item
          label="技能正文（instructions，注入 System Prompt）"
          name="content"
          rules={[{ required: true, message: '技能正文不能为空' }]}
        >
          <Input.TextArea
            rows={8}
            style={{ fontFamily: MONO_FONT }}
            placeholder="当用户询问 X 时，按口径 Y 输出。演示剧本：如「当用户询问资产同比分析时，先确认资产范围与期间，再按统一口径对比输出。」"
          />
        </Form.Item>
        <Form.Item
          label="元数据（可选，JSON 对象）"
          name="metadata"
          rules={[
            {
              validator: (_rule, value?: string) => {
                const parsed = parseSkillMetadata(value ?? '');
                return parsed.error ? Promise.reject(new Error(parsed.error)) : Promise.resolve();
              },
            },
          ]}
        >
          <Input.TextArea
            rows={4}
            style={{ fontFamily: MONO_FONT }}
            placeholder='如 {"tags":["资产","同比分析"],"owner":"数据组"}'
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default SkillEditorModal;

import { Button, Card, Modal, Space, Tag, Typography } from 'antd';
import React from 'react';
import type { AgentSkillItem, AgentSkillSaveInput } from '@/services/agent';
import { skillTemplateInput, skillTemplates } from '@/services/agent/skillTemplates';
import type { SkillTemplate } from '@/services/agent/skillTemplates';
import SkillDocumentViewer from './SkillDocumentViewer';

interface Props {
  items: AgentSkillItem[];
  registrationKnown: boolean;
  canManage: boolean;
  onRegister: (input: AgentSkillSaveInput) => void;
}

/** Templates are display materials. Only the managed list owns registration and activation state. */
const SkillTemplateGallery: React.FC<Props> = ({ items, registrationKnown, canManage, onRegister }) => {
  const [preview, setPreview] = React.useState<SkillTemplate | null>(null);
  return (
    <section style={{ marginTop: 24 }} aria-label="技能模板">
      <Typography.Title level={5}>技能模板</Typography.Title>
      <Typography.Paragraph type="secondary">
        已开发的场景指令与治理方法可在此查看。模板尚未自动注册，真实模型验收待统一进行；管理员保存注册后会启用，下一轮对话生效。
      </Typography.Paragraph>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
        {skillTemplates.map((template) => {
          const registered = items.find((item) => item.skillId === template.skillId);
          const available = registrationKnown && !registered;
          return (
            <Card key={template.skillId} size="small" title={template.name} role="group" aria-label={template.name}>
              <Space wrap size={4} style={{ marginBottom: 8 }}>
                <Tag>{template.skillId}</Tag>
                <Tag>{template.category}</Tag>
                <Tag color={registrationKnown && registered?.enabled ? 'green' : 'default'}>
                  {!registrationKnown
                    ? '登记状态待确认'
                    : registered
                      ? `已注册 · ${registered.enabled ? '已启用' : '已停用'}`
                      : '尚未注册'}
                </Tag>
              </Space>
              <Typography.Paragraph>{template.description}</Typography.Paragraph>
              <Typography.Paragraph type="secondary">使用位置：{template.entry}</Typography.Paragraph>
              <Space wrap>
                <Button onClick={() => setPreview(template)}>查看正文</Button>
                {canManage && !registered ? (
                  <Button type="primary" disabled={!available} onClick={() => onRegister(skillTemplateInput(template))}>
                    查看并注册
                  </Button>
                ) : null}
              </Space>
              {registrationKnown && registered ? (
                <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                  当前配置请在上方已注册技能中查看和管理。
                </Typography.Paragraph>
              ) : null}
            </Card>
          );
        })}
      </div>
      <Modal
        open={preview !== null}
        title={preview?.name}
        footer={null}
        onCancel={() => setPreview(null)}
        width={1280}
        style={{ top: 20, maxWidth: 'calc(100vw - 32px)' }}
        styles={{
          content: { height: 'min(88dvh, 960px)', display: 'flex', flexDirection: 'column' },
          body: { flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' },
        }}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          模板正文，仅供查看；登记和启用状态以上方已注册技能为准。
        </Typography.Paragraph>
        <SkillDocumentViewer key={preview?.skillId} source={preview?.content ?? ''} />
      </Modal>
    </section>
  );
};

export default SkillTemplateGallery;

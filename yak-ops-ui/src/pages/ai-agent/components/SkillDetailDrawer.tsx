import { Descriptions, Drawer, Empty, Space, Tag, Typography } from 'antd';
import React from 'react';
import type { AgentSkillItem } from '@/services/agent';
import { skillTagVisual } from '../skill-runtime';
import MarkdownContent from './MarkdownContent';

const formatTime = (value?: string) => value?.replace('T', ' ').slice(0, 19) ?? '-';

export interface SkillDetailDrawerProps {
  open: boolean;
  skill: AgentSkillItem | null;
  onClose: () => void;
}

/** 技能详情抽屉（文档 §3.4）：基本信息 + 正文 Markdown 渲染 + metadata 只读 JSON。 */
const SkillDetailDrawer: React.FC<SkillDetailDrawerProps> = ({ open, skill, onClose }) => {
  const visual = skill ? skillTagVisual(skill) : null;

  return (
    <Drawer title={skill ? `技能详情：${skill.name}` : '技能详情'} width={680} open={open} onClose={onClose}>
      {!skill ? (
        <Empty description="技能不存在（可能已被删除）" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions
            column={1}
            size="small"
            bordered
            items={[
              { key: 'skillId', label: '技能标识', children: <Typography.Text code>{skill.skillId}</Typography.Text> },
              { key: 'name', label: '技能名称', children: skill.name },
              {
                key: 'status',
                label: '启停状态',
                children: <Tag color={visual!.statusColor}>{visual!.statusText}</Tag>,
              },
              { key: 'version', label: '版本', children: skill.version },
              { key: 'createTime', label: '创建时间', children: formatTime(skill.createTime) },
              { key: 'updateTime', label: '更新时间', children: formatTime(skill.updateTime) },
            ]}
          />
          {skill.description ? (
            <div>
              <Typography.Text type="secondary">技能描述</Typography.Text>
              <Typography.Paragraph style={{ marginTop: 4 }}>{skill.description}</Typography.Paragraph>
            </div>
          ) : null}
          <div>
            <Typography.Text strong>技能正文（instructions，注入 System Prompt）</Typography.Text>
            <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: '8px 12px', marginTop: 8 }}>
              <MarkdownContent md={skill.content} />
            </div>
          </div>
          <div>
            <Typography.Text strong>元数据（能力标签等）</Typography.Text>
            <pre
              style={{
                marginTop: 8,
                marginBottom: 0,
                background: '#f6f8fa',
                padding: '10px 12px',
                borderRadius: 6,
                fontSize: 12.5,
                lineHeight: 1.6,
                overflowX: 'auto',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
              }}
            >
              {skill.metadata && Object.keys(skill.metadata).length
                ? JSON.stringify(skill.metadata, null, 2)
                : '- (未填写)'}
            </pre>
          </div>
        </Space>
      )}
    </Drawer>
  );
};

export default SkillDetailDrawer;

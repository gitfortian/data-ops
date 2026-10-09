import { Descriptions, Drawer, Empty, Tag, Typography } from 'antd';
import React from 'react';
import type { AgentSkillItem } from '@/services/agent';
import { skillTagVisual } from '../skill-runtime';
import SkillDocumentViewer from './SkillDocumentViewer';

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
    <Drawer
      title={skill ? `技能详情：${skill.name}` : '技能详情'}
      width="min(1280px, calc(100vw - 32px))"
      open={open}
      onClose={onClose}
    >
      {!skill ? (
        <Empty description="技能不存在（可能已被删除）" />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, height: '100%', minHeight: 0 }}>
          <Descriptions
            column={{ xs: 1, sm: 2, lg: 3 }}
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
          <div style={{ flex: 1, minHeight: 300 }}>
            <SkillDocumentViewer key={skill.skillId} source={skill.content} />
          </div>
          <details>
            <summary style={{ cursor: 'pointer' }}>元数据（能力标签等）</summary>
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
                maxHeight: 160,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
              }}
            >
              {skill.metadata && Object.keys(skill.metadata).length
                ? JSON.stringify(skill.metadata, null, 2)
                : '- (未填写)'}
            </pre>
          </details>
        </div>
      )}
    </Drawer>
  );
};

export default SkillDetailDrawer;

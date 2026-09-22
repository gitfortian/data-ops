import { Button, Popconfirm, Space, Switch, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React from 'react';
import type { AgentSkillItem } from '@/services/agent';
import { skillTagVisual } from '../skill-runtime';

const formatTime = (value?: string) => value?.replace('T', ' ').slice(0, 19) ?? '-';

export interface SkillListTableProps {
  items: AgentSkillItem[];
  loading: boolean;
  /** 无 manage 权限：列表只读（启停禁用、操作列仅详情）。 */
  canManage: boolean;
  /** 正在切换启停的 skillId（乐观 UI 防连点）。 */
  pendingToggle: string | null;
  onToggle: (skill: AgentSkillItem) => void;
  onDetail: (skill: AgentSkillItem) => void;
  onEdit: (skill: AgentSkillItem) => void;
  onDelete: (skill: AgentSkillItem) => void;
}

/** 技能列表表格（文档 §3.2）：name+skillId Tag / 描述 Tooltip / 启停 Switch / 版本 / 更新时间 / 操作。 */
const SkillListTable: React.FC<SkillListTableProps> = ({
  items,
  loading,
  canManage,
  pendingToggle,
  onToggle,
  onDetail,
  onEdit,
  onDelete,
}) => {
  const columns: ColumnsType<AgentSkillItem> = [
    {
      title: '技能名',
      dataIndex: 'name',
      width: 220,
      ellipsis: true,
      render: (name: string, record) => {
        const visual = skillTagVisual(record);
        return (
          <Space size={6} wrap>
            <Typography.Text strong>{name}</Typography.Text>
            <Tag color={record.enabled ? 'green' : 'default'}>{visual.id}</Tag>
          </Space>
        );
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (description?: string) =>
        description ? (
          <Tooltip title={description}>
            <Typography.Text type="secondary">{description}</Typography.Text>
          </Tooltip>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: '启停状态',
      dataIndex: 'enabled',
      width: 110,
      render: (enabled: boolean, record) => (
        <Tooltip title="切换后下一轮对话生效，不影响正在进行的推理">
          <Switch
            size="small"
            checked={enabled}
            disabled={!canManage || pendingToggle === record.skillId}
            loading={pendingToggle === record.skillId}
            onChange={() => onToggle(record)}
          />
        </Tooltip>
      ),
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 70,
      render: (version?: number) => version ?? '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: formatTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: canManage ? 150 : 70,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" onClick={() => onDetail(record)}>
            详情
          </Button>
          {canManage ? (
            <>
              <Button size="small" onClick={() => onEdit(record)}>
                编辑
              </Button>
              <Popconfirm
                title={`删除技能「${record.name}」？`}
                description="删除后不再注入下一轮推理，已开始推理与历史报告不受影响"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => onDelete(record)}
              >
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Table<AgentSkillItem>
      size="small"
      rowKey="skillId"
      loading={loading}
      columns={columns}
      dataSource={items}
      pagination={false}
    />
  );
};

export default SkillListTable;

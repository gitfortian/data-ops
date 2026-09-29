import { Button, InputNumber, message, Switch, Table, Typography } from 'antd';
import React from 'react';
import type { ConfigItem } from '@/services/agent';
import { agentConfigApi } from '@/services/agent';

/**
 * 运行时配置治理面板（Phase 2 P2#2）：登记键位清单 + 当前值编辑。
 * bool 键位用 Switch；int 键位用 InputNumber；清空即回退种子默认值。
 * 热生效（服务端 1s 微过期），无需重启。
 */
const ConfigPanel: React.FC = () => {
  const [items, setItems] = React.useState<ConfigItem[]>([]);
  const [loading, setLoading] = React.useState(false);
  // 草稿态：key -> 编辑中的值（bool 为 boolean；int 为 number；undefined 表示回退默认）
  const [drafts, setDrafts] = React.useState<Record<string, boolean | number | undefined>>({});
  const [saving, setSaving] = React.useState<string | null>(null);

  const reload = React.useCallback(async () => {
    setLoading(true);
    try {
      setItems(await agentConfigApi.list());
      setDrafts({});
    } catch (error) {
      message.error(`配置加载失败：${(error as Error).message}`);
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const save = async (item: ConfigItem) => {
    setSaving(item.key);
    try {
      const draft = drafts[item.key];
      const value =
        item.kind === 'bool' ? String(draft ?? false) : draft === undefined || draft === null ? '' : String(draft);
      // bool 未编辑过：按 dbValue（null=默认 true 语义视 kind）原样回写避免误清
      const payloadValue = draft === undefined && item.dbValue == null ? '' : value;
      await agentConfigApi.update(item.key, payloadValue);
      message.success('已保存并热生效');
      await reload();
    } catch (error) {
      message.error(`保存失败：${(error as Error).message}`);
    } finally {
      setSaving(null);
    }
  };

  const columns = [
    {
      title: '键位',
      dataIndex: 'key',
      width: 280,
      render: (key: string) => <Typography.Text code>{key}</Typography.Text>,
    },
    { title: '说明', dataIndex: 'description' },
    {
      title: '当前值',
      dataIndex: 'dbValue',
      width: 140,
      render: (value: string | null) =>
        value == null ? <Typography.Text type="secondary">默认</Typography.Text> : value,
    },
    {
      title: '编辑',
      width: 160,
      render: (_: unknown, item: ConfigItem) => {
        if (item.kind === 'bool') {
          const checked =
            typeof drafts[item.key] === 'boolean'
              ? (drafts[item.key] as boolean)
              : item.dbValue != null
                ? item.dbValue === 'true'
                : true; // 登记开关键位种子默认均为 true
          return <Switch checked={checked} onChange={(v) => setDrafts((prev) => ({ ...prev, [item.key]: v }))} />;
        }
        const num =
          typeof drafts[item.key] === 'number'
            ? (drafts[item.key] as number)
            : item.dbValue != null
              ? Number(item.dbValue)
              : undefined;
        return (
          <InputNumber
            min={1}
            value={num}
            onChange={(v) => setDrafts((prev) => ({ ...prev, [item.key]: v ?? undefined }))}
          />
        );
      },
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, item: ConfigItem) => (
        <Button size="small" type="primary" loading={saving === item.key} onClick={() => void save(item)}>
          保存
        </Button>
      ),
    },
  ];

  return (
    <Table<ConfigItem>
      rowKey="key"
      size="small"
      loading={loading}
      columns={columns as never}
      dataSource={items}
      pagination={false}
    />
  );
};

export default ConfigPanel;

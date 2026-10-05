import { Alert, Button, InputNumber, message, Switch, Table, Typography } from 'antd';
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
      const edited = Object.prototype.hasOwnProperty.call(drafts, item.key);
      const payloadValue = edited ? (draft == null ? '' : String(draft)) : (item.dbValue ?? '');
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
      dataIndex: 'effectiveValue',
      width: 140,
      render: (value: string | null, item: ConfigItem) => item.updateMode === 'NOT_CONNECTED'
        ? <Typography.Text type="secondary">尚未接入</Typography.Text>
        : value ?? item.dbValue ?? <Typography.Text type="secondary">默认</Typography.Text>,
    },
    { title: '来源', dataIndex: 'valueSource', width: 110,
      render: (source: string) => source === 'DYNAMIC' ? '动态配置' : source === 'STARTUP' ? '启动配置' : '预留键位' },
    {
      title: '编辑',
      width: 160,
      render: (_: unknown, item: ConfigItem) => {
        if (item.updateMode === 'NOT_CONNECTED') return <Typography.Text type="secondary">不可调整</Typography.Text>;
        if (item.kind === 'bool') {
          const checked =
            typeof drafts[item.key] === 'boolean'
              ? (drafts[item.key] as boolean)
              : item.effectiveValue === 'true';
          return <Switch checked={checked} onChange={(v) => setDrafts((prev) => ({ ...prev, [item.key]: v }))} />;
        }
        const num =
          Object.prototype.hasOwnProperty.call(drafts, item.key)
            ? drafts[item.key] as number | undefined
            : item.effectiveValue == null ? undefined : Number(item.effectiveValue);
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
        <Button size="small" type="primary" disabled={item.updateMode === 'NOT_CONNECTED'} loading={saving === item.key} onClick={() => void save(item)}>
          保存
        </Button>
      ),
    },
  ];

  return (
    <>
    <Alert type="info" showIcon style={{ marginBottom: 12 }} message="模型与工具预算修改启动配置后需重启；下列已接入键位可动态覆盖启动默认值，清空后恢复默认。预留键位尚未生效。" />
    <Table<ConfigItem>
      rowKey="key"
      size="small"
      loading={loading}
      columns={columns as never}
      dataSource={items}
      pagination={false}
    />
    </>
  );
};

export default ConfigPanel;

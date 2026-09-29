import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Drawer, message, Space, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import type { ModelingMainlineCoverage, ModelingMainlineLayer } from '@/services/modeling/types';
import { getModelingMainline } from '@/services/modeling/view';
import { listSemanticLayers } from '@/services/semantic/api';

const MainlinePage: React.FC = () => {
  const [coverage, setCoverage] = useState<ModelingMainlineCoverage[]>([]);
  const [layers, setLayers] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ModelingMainlineCoverage | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      setCoverage((await getModelingMainline()) ?? []);
    } catch {
      message.error('加载主线视图失败（语义服务可能不可用），请稍后重试');
      setCoverage([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
    listSemanticLayers()
      .then((list) => setLayers((list ?? []).map((item) => item.code)))
      .catch(() => setLayers([]));
  }, [loadAll]);

  const coverageCell = (record: ModelingMainlineCoverage, code: string) => {
    const layer: ModelingMainlineLayer | undefined = (record.layers ?? []).find((item) => item.layerCode === code);
    if (!layer || layer.modelCount === 0) {
      return <Tag color="default">未建</Tag>;
    }
    return (
      <Button type="link" size="small" onClick={() => setDetail(record)}>
        {layer.modelCount} 个模型
      </Button>
    );
  };

  const columns: TableColumnsType<ModelingMainlineCoverage> = [
    { title: '业务过程', dataIndex: 'processName', width: 160 },
    {
      title: '编码',
      dataIndex: 'processCode',
      width: 140,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    ...layers.map((code) => ({
      title: code,
      key: code,
      width: 110,
      render: (_value: unknown, record: ModelingMainlineCoverage) => coverageCell(record, code),
    })),
    {
      title: '合计',
      dataIndex: 'totalModels',
      width: 80,
      render: (value: number) => <Typography.Text strong>{value}</Typography.Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      render: (_value, record) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => setDetail(record)}>
            覆盖详情
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() =>
              history.push(
                `/modeling/impact?processId=${record.processId}&processName=${encodeURIComponent(record.processName)}`,
              )
            }
          >
            查看影响
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">业务过程主线视图</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            按业务过程盘点各层模型覆盖与状态，支撑资产盘点（哪些过程未建 DWS/ADS）
          </div>
        </div>
        <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => void loadAll()}>
          刷新
        </YakButton>
      </div>

      <Table<ModelingMainlineCoverage>
        className="mt-4"
        rowKey="processId"
        loading={loading}
        columns={columns}
        dataSource={coverage}
        pagination={false}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="还没有业务过程"
              description="在语义中心维护业务过程并完成派生建模后，这里展示各层覆盖"
            />
          ),
        }}
      />

      <Drawer
        open={Boolean(detail)}
        width={640}
        title={detail ? `覆盖详情：${detail.processName}（${detail.processCode}）` : ''}
        destroyOnClose
        onClose={() => setDetail(null)}
      >
        {(detail?.layers ?? []).length === 0 ? (
          <Typography.Text type="secondary">该业务过程还没有任何模型（未建）</Typography.Text>
        ) : (
          (detail?.layers ?? []).map((layer) => (
            <div key={layer.layerCode} className="mb-4">
              <Typography.Text strong className="!block !mb-2 !text-[13px]">
                {layer.layerCode}（{layer.modelCount}）
              </Typography.Text>
              {layer.models.map((model) => (
                <div
                  key={model.modelId}
                  className="mb-1 flex items-center justify-between rounded border border-[#f0f0f0] px-3 py-2"
                >
                  <Space>
                    <Typography.Text className="!text-[13px]">{model.name}</Typography.Text>
                    <Typography.Text code className="!text-[12px]">
                      {model.code}
                    </Typography.Text>
                  </Space>
                  <Space>
                    <Tag color={model.status === 'PUBLISHED' ? 'green' : 'blue'}>{model.status}</Tag>
                    <Button type="link" size="small" onClick={() => history.push(`/modeling/models/${model.modelId}`)}>
                      打开
                    </Button>
                  </Space>
                </div>
              ))}
            </div>
          ))
        )}
      </Drawer>
    </div>
  );
};

export default MainlinePage;

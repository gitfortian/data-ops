import { history, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, Button, Input, message, Space, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { YakButton } from '@/components/ui';
import type { ModelingImpactItem, ModelingImpactKind } from '@/services/modeling/types';
import { getModelingImpactBySource, getModelingImpactByStandardField } from '@/services/modeling/view';
import { listSemanticProcessSources } from '@/services/semantic/api';

/** 变更影响分析(ticket 46):影响范围预览 + 受影响项跳转;只读不自动改。
 * 03 号单:支持 URL 参数直达结果态——processFieldId 按标准字段;
 * processId(+processName) 取该业务过程主源表按来源分析;datasourceId/database/table/column 直填按来源分析。 */
const ImpactPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [items, setItems] = useState<ModelingImpactItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<string>();
  const [processFieldId, setProcessFieldId] = useState<string>(searchParams.get('processFieldId') ?? '');
  const [sourceDatasourceId, setSourceDatasourceId] = useState(searchParams.get('datasourceId') ?? '');
  const [sourceDatabase, setSourceDatabase] = useState(searchParams.get('database') ?? '');
  const [sourceTable, setSourceTable] = useState(searchParams.get('table') ?? '');
  const [sourceColumn, setSourceColumn] = useState(searchParams.get('column') ?? '');
  const autoRanRef = useRef(false);

  const loadByField = useCallback(async (targetId: string) => {
    if (!targetId) {
      message.error('请输入标准字段 ID');
      return;
    }
    setLoading(true);
    try {
      setNotice(undefined);
      setItems((await getModelingImpactByStandardField(Number(targetId))) ?? []);
    } catch {
      message.error('影响分析失败，请稍后重试');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadBySource = useCallback(
    async (override?: { datasourceId?: string; database?: string; table?: string; column?: string; notice?: string }) => {
      const datasourceId = override?.datasourceId ?? sourceDatasourceId;
      if (!datasourceId) {
        message.error('请输入数据源 ID');
        return;
      }
      setLoading(true);
      try {
        setNotice(override?.notice);
        setItems(
          (await getModelingImpactBySource(
            Number(datasourceId),
            (override?.database ?? sourceDatabase) || undefined,
            (override?.table ?? sourceTable) || undefined,
            (override?.column ?? sourceColumn) || undefined,
          )) ?? [],
        );
      } catch {
        message.error('影响分析失败，请稍后重试');
        setItems([]);
      } finally {
        setLoading(false);
      }
    },
    [sourceDatasourceId, sourceDatabase, sourceTable, sourceColumn],
  );

  // URL 参数进入即查询(一次):processFieldId 优先,其次 processId 主源表,最后直填来源参数
  useEffect(() => {
    if (autoRanRef.current) {
      return;
    }
    autoRanRef.current = true;
    const fieldId = searchParams.get('processFieldId');
    if (fieldId) {
      void loadByField(fieldId);
      return;
    }
    const processId = searchParams.get('processId');
    if (processId) {
      const processName = searchParams.get('processName') ?? `#${processId}`;
      listSemanticProcessSources(Number(processId))
        .then((sources) => {
          const list = sources ?? [];
          const main = list.find((item) => item.tableRole === 'MAIN') ?? list[0];
          if (!main) {
            setNotice(`业务过程「${processName}」尚未绑定源表（过程语义页维护后可按来源分析）；下方可改按标准字段查询`);
            setItems([]);
            return;
          }
          setSourceDatasourceId(String(main.datasourceId));
          setSourceTable(main.sourceTable);
          void loadBySource({
            datasourceId: String(main.datasourceId),
            database: '',
            table: main.sourceTable,
            column: '',
            notice:
              list.length > 1
                ? `按业务过程「${processName}」的主源表 ${main.sourceTable} 分析（该过程共绑定 ${list.length} 张源表，可切换后重查）`
                : `按业务过程「${processName}」绑定的源表 ${main.sourceTable} 分析`,
          });
        })
        .catch(() => message.error('加载业务过程源表失败，请稍后重试'));
      return;
    }
    if (searchParams.get('datasourceId')) {
      void loadBySource();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: TableColumnsType<ModelingImpactItem> = [
    {
      title: '影响维度',
      dataIndex: 'impactKind',
      width: 130,
      render: (value: ModelingImpactKind) =>
        value === 'STANDARD_FIELD' ? <Tag color="geekblue">标准字段落地</Tag> : <Tag color="orange">来源字段映射</Tag>,
    },
    {
      title: '受影响模型',
      dataIndex: 'modelId',
      width: 140,
      render: (value: number) => (
        <Button type="link" size="small" onClick={() => history.push(`/modeling/models/${value}`)}>
          模型 #{value}
        </Button>
      ),
    },
    { title: '目标字段', dataIndex: 'targetColumn', width: 170 },
    { title: '分层', dataIndex: 'layerId', width: 90, render: (v?: number) => (v ? `#${v}` : '-') },
    { title: '来源明细', dataIndex: 'sourceDetail', ellipsis: true, render: (v?: string) => v || '-' },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="text-[20px] font-semibold leading-7">变更影响分析</div>
      <div className="mt-1 text-[13px] text-[#667085]">
        变更前预览影响范围：标准字段变更按分层映射反查落地；来源字段变更按来源映射反查模型。只读预览，不自动修改。
      </div>

      {notice ? (
        <Alert className="mt-3" type="info" showIcon closable message={notice} onClose={() => setNotice(undefined)} />
      ) : null}

      <Space wrap className="mt-4" size={16}>
        <Input
          style={{ width: 200 }}
          placeholder="标准字段 ID"
          value={processFieldId}
          onChange={(event) => setProcessFieldId(event.target.value)}
        />
        <YakButton
          className="!h-9 !rounded-lg !px-4"
          onClick={() => {
            void loadByField(processFieldId);
          }}
        >
          按标准字段分析
        </YakButton>
        <Typography.Text type="secondary">或</Typography.Text>
        <Input
          style={{ width: 130 }}
          placeholder="数据源 ID"
          value={sourceDatasourceId}
          onChange={(event) => setSourceDatasourceId(event.target.value)}
        />
        <Input
          style={{ width: 140 }}
          placeholder="源库（可选）"
          value={sourceDatabase}
          onChange={(event) => setSourceDatabase(event.target.value)}
        />
        <Input
          style={{ width: 160 }}
          placeholder="源表（可选）"
          value={sourceTable}
          onChange={(event) => setSourceTable(event.target.value)}
        />
        <Input
          style={{ width: 150 }}
          placeholder="源字段（可选）"
          value={sourceColumn}
          onChange={(event) => setSourceColumn(event.target.value)}
        />
        <YakButton
          className="!h-9 !rounded-lg !px-4"
          onClick={() => {
            void loadBySource();
          }}
        >
          按来源分析
        </YakButton>
      </Space>

      <Table<ModelingImpactItem>
        className="mt-4"
        rowKey={(record) => `${record.impactKind}-${record.modelId}-${record.targetColumn}-${record.layerId ?? 0}`}
        loading={loading}
        columns={columns}
        dataSource={items}
        pagination={{ pageSize: 20, showTotal: (count) => `共 ${count} 个受影响项` }}
        locale={{
          emptyText: (
            <Typography.Text type="secondary">输入条件后预览影响范围；无结果说明当前没有受影响项</Typography.Text>
          ),
        }}
      />
    </div>
  );
};

export default ImpactPage;

import Table from '@/components/ReadableTable';
import { history } from '@umijs/max';
import { Alert, Button } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowUpRight, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { searchMetadata } from '@/services/metadata/api';
import { formatMetadataTime } from '@/services/metadata/presentation';
import type { CollectJobRecord, MetadataSearchItem } from '@/services/metadata/types';

export default function CollectedJobTables({ job, sourceId }: { job: CollectJobRecord; sourceId: string }) {
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<MetadataSearchItem[]>([]);
  const [total, setTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    setRows([]);
    setTotal(null);
    void searchMetadata({
      index: ['table'],
      queryFilter: { datasourceId: Number(sourceId), providerType: 'HARVESTED', collectJobId: job.id },
      from: (page - 1) * 8,
      size: 8,
      sortField: 'updateTime',
      sortOrder: 'desc',
    })
      .then((result) => {
        if (!active) return;
        if (
          result.items.some(
            (table) =>
              table.typeName !== 'table' ||
              table.providerType !== 'HARVESTED' ||
              String(table.dataSourceId) !== sourceId ||
              String(table.collectJobId) !== String(job.id),
          )
        )
          throw new Error('Task association mismatch');
        setRows(result.items);
        setTotal(result.total);
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [job.id, sourceId, page, version]);
  const columns: ColumnsType<MetadataSearchItem> = [
    {
      title: '已采集表',
      key: 'name',
      render: (_, table) => (
        <div>
          <strong>{table.tableName || table.name || table.displayName}</strong>
          <div className="source-workspace-scope">
            {[table.databaseName, table.schemaName].filter(Boolean).join(' / ')}
          </div>
        </div>
      ),
    },
    { title: '最近采集', dataIndex: 'lastCollectAt', width: 155, render: formatMetadataTime },
    {
      title: '',
      key: 'open',
      width: 108,
      render: (_, table) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            const params = new URLSearchParams({ view: 'entity', datasourceId: sourceId, entityId: String(table.id) });
            if (table.databaseName) params.set('databaseName', table.databaseName);
            history.push(`/data-asset/catalog?${params}`);
          }}
        >
          查看结构 <ArrowUpRight size={13} />
        </Button>
      ),
    },
  ];
  return (
    <div className="source-collection-tables">
      <div className="source-collection-detail-heading">
        <span>关联表 · {total ?? '—'}</span>
        <Button
          size="small"
          type="text"
          aria-label={`刷新${job.jobName}的关联表`}
          icon={<RefreshCw size={13} />}
          onClick={() => setVersion((value) => value + 1)}
        />
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          message="未能读取该任务的关联表"
          description="读取失败不能视为没有采集成果。"
          action={
            <Button size="small" onClick={() => setVersion((value) => value + 1)}>
              重试
            </Button>
          }
        />
      ) : (
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          locale={{ emptyText: loading ? '正在读取关联表…' : '当前目录尚无关联到该任务的表' }}
          pagination={{
            current: page,
            pageSize: 8,
            total: total ?? 0,
            showSizeChanger: false,
            hideOnSinglePage: true,
            onChange: setPage,
          }}
        />
      )}
      <p className="source-workspace-caption">当前目录的任务关联结果；单次运行的情况请查看运行历史。</p>
    </div>
  );
}

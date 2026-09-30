import { Alert, Descriptions, Drawer, Select, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';

import { YakEmpty } from '@/components/ui';
import { getMetricVersion } from '@/services/metric/api';
import type { MetricVersionRecord } from '@/services/metric/types';

interface MetricVersionComparisonDrawerProps {
  metricId: number;
  metricVersion: number;
  versions: MetricVersionRecord[];
  open: boolean;
  initialVersions: [number, number] | null;
  onClose: () => void;
}

interface DifferenceRecord {
  key: string;
  field: string;
  before: unknown;
  after: unknown;
  change: 'ADDED' | 'REMOVED' | 'CHANGED';
}

type ComparisonState =
  | { status: 'LOADING' }
  | { status: 'READY'; left: MetricVersionRecord; right: MetricVersionRecord }
  | { status: 'UNAVAILABLE' };

const FIELD_LABELS: Record<string, string> = {
  metricCode: '指标编码',
  metricName: '指标名称',
  domainId: '业务域 ID',
  processId: '业务过程 ID',
  metricType: '指标类型',
  caliberId: '口径标准 ID',
  calRule: '口径规则',
  measureExpr: '度量表达式',
  filterExpr: '过滤条件',
  dimModelIds: 'DIM 模型 ID',
  refMetricId: '引用指标 ID',
  refMetricVersion: '引用指标版本',
  dimConstraint: '限定条件',
  qualifiersJson: '结构化限定条件',
  modelId: '来源模型 ID',
  statDimensions: '统计维度',
  statPeriod: '统计周期',
  unitId: '度量单位 ID',
  businessDesc: '业务口径',
  owner: '负责人',
  status: '状态',
  compositions: '组成项',
  subMetricId: '子指标 ID',
  subMetricVersion: '子指标版本',
  operator: '运算符',
  expression: '表达式',
  sortOrder: '排序',
};

const flattenSnapshot = (value: unknown, path = '', output: Record<string, unknown> = {}) => {
  if (Array.isArray(value) && value.length > 0) {
    value.forEach((item, index) => flattenSnapshot(item, `${path}[${index}]`, output));
  } else if (value && typeof value === 'object' && Object.keys(value).length > 0) {
    Object.entries(value).forEach(([key, item]) =>
      flattenSnapshot(item, path ? `${path}.${key}` : key, output),
    );
  } else {
    output[path || 'snapshot'] = value;
  }
  return output;
};

const valueKey = (value: unknown) => JSON.stringify(value);

const formatValue = (value: unknown) => {
  if (value === undefined) return '（未设置）';
  if (value === null) return 'null';
  return typeof value === 'string' ? value || '（空字符串）' : JSON.stringify(value);
};

const fieldLabel = (path: string) => path
  .replace(/\[(\d+)\]/g, (_match, index: string) => ` 第${Number(index) + 1}项`)
  .split('.')
  .map((part) => FIELD_LABELS[part] ?? part)
  .join(' / ');

const toDifferences = (leftSnapshot: string, rightSnapshot: string): DifferenceRecord[] => {
  const left = flattenSnapshot(JSON.parse(leftSnapshot));
  const right = flattenSnapshot(JSON.parse(rightSnapshot));
  const paths = [...new Set([...Object.keys(left), ...Object.keys(right)])].sort();

  return paths.flatMap((path) => {
    if (valueKey(left[path]) === valueKey(right[path])) return [];
    const change = left[path] === undefined
      ? 'ADDED'
      : right[path] === undefined
        ? 'REMOVED'
        : 'CHANGED';
    return [{ key: path, field: fieldLabel(path), before: left[path], after: right[path], change }];
  });
};

export default function MetricVersionComparisonDrawer({
  metricId,
  metricVersion,
  versions,
  open,
  initialVersions,
  onClose,
}: MetricVersionComparisonDrawerProps) {
  const versionOptions = useMemo(
    () => [...new Set([...versions.map((version) => version.version), metricVersion])]
      .sort((left, right) => right - left)
      .map((version) => ({ label: `v${version}${version === metricVersion ? ' · 当前草稿' : ''}`, value: version })),
    [metricVersion, versions],
  );
  const [leftVersion, setLeftVersion] = useState(metricVersion);
  const [rightVersion, setRightVersion] = useState(metricVersion);
  const [comparison, setComparison] = useState<ComparisonState>({ status: 'LOADING' });

  useEffect(() => {
    if (!open) return;
    const first = initialVersions?.[0] ?? versionOptions[0]?.value ?? metricVersion;
    const second = initialVersions?.[1]
      ?? versionOptions.find((option) => option.value !== first)?.value
      ?? first;
    setLeftVersion(first);
    setRightVersion(second);
  }, [initialVersions?.[0], initialVersions?.[1], metricVersion, open, versionOptions]);

  useEffect(() => {
    if (!open || !leftVersion || !rightVersion) return;
    let cancelled = false;
    setComparison({ status: 'LOADING' });
    Promise.all([
      getMetricVersion(metricId, leftVersion),
      getMetricVersion(metricId, rightVersion),
    ]).then(([left, right]) => {
      if (!cancelled) setComparison({ status: 'READY', left, right });
    }).catch(() => {
      if (!cancelled) setComparison({ status: 'UNAVAILABLE' });
    });
    return () => {
      cancelled = true;
    };
  }, [leftVersion, metricId, open, rightVersion]);

  let differences: DifferenceRecord[] = [];
  let invalidSnapshot = false;
  if (comparison.status === 'READY') {
    try {
      differences = toDifferences(comparison.left.snapshot, comparison.right.snapshot);
    } catch {
      invalidSnapshot = true;
    }
  }

  const columns: ColumnsType<DifferenceRecord> = [
    {
      title: '字段',
      dataIndex: 'field',
      width: 220,
      render: (field: string, record) => <span title={record.key}>{field}</span>,
    },
    {
      title: `v${leftVersion}`,
      dataIndex: 'before',
      render: (value: unknown) => <span className="whitespace-pre-wrap break-words">{formatValue(value)}</span>,
    },
    {
      title: `v${rightVersion}`,
      dataIndex: 'after',
      render: (value: unknown) => <span className="whitespace-pre-wrap break-words">{formatValue(value)}</span>,
    },
    {
      title: '变化',
      dataIndex: 'change',
      width: 100,
      render: (change: DifferenceRecord['change']) => (
        <Tag color={change === 'ADDED' ? 'green' : change === 'REMOVED' ? 'red' : 'orange'}>
          {change === 'ADDED' ? '新增' : change === 'REMOVED' ? '移除' : '变更'}
        </Tag>
      ),
    },
  ];

  return (
    <Drawer title="指标版本差异" open={open} onClose={onClose} width={900}>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <span className="text-[13px] text-[#667085]">基准版本</span>
        <Select
          className="w-48"
          value={leftVersion}
          options={versionOptions}
          onChange={setLeftVersion}
          aria-label="基准版本"
        />
        <span className="text-[13px] text-[#667085]">对比版本</span>
        <Select
          className="w-48"
          value={rightVersion}
          options={versionOptions}
          onChange={setRightVersion}
          aria-label="对比版本"
        />
      </div>
      <div className="mb-4 text-[12px] text-[#667085]">
        比较不可变版本快照；此操作不会恢复定义或改变当前发布版本。
      </div>
      <Spin spinning={comparison.status === 'LOADING'}>
        {comparison.status === 'UNAVAILABLE' ? (
          <YakEmpty compact title="版本快照暂不可读" description="请稍后重试，当前无法判断两个版本的差异" />
        ) : invalidSnapshot ? (
          <Alert type="warning" showIcon message="版本快照格式无效，无法生成字段差异" />
        ) : comparison.status === 'READY' ? (
          <>
            <Descriptions
              size="small"
              column={2}
              items={[
                { key: 'left', label: `v${leftVersion} 变更说明`, children: comparison.left.changeDesc || '-' },
                { key: 'right', label: `v${rightVersion} 变更说明`, children: comparison.right.changeDesc || '-' },
              ]}
            />
            {differences.length ? (
              <Table<DifferenceRecord>
                className="mt-4"
                rowKey="key"
                size="small"
                pagination={{ pageSize: 20, showSizeChanger: false }}
                columns={columns}
                dataSource={differences}
                scroll={{ x: 760 }}
              />
            ) : (
              <div className="mt-4">
                <YakEmpty compact title="两个版本定义一致" description="快照中的定义字段没有变化" />
              </div>
            )}
          </>
        ) : null}
      </Spin>
    </Drawer>
  );
}

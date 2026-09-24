import {
  getProduct,
  productKeyValue,
  type DataProductView,
  type ProductLookupState,
} from '@/services/consumption';
import { history, useParams } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Result,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';

const { Title, Paragraph, Text } = Typography;

type DatasetColumn = {
  fieldId?: string;
  physicalName?: string;
  displayName?: string;
  dataType?: string;
  nullable?: boolean;
  description?: string;
  defaultRole?: string;
  sortOrder?: number;
};

const lookupResult = (state: ProductLookupState, reason?: string | null) => {
  if (state === 'FORBIDDEN') {
    return <Result status="403" title="无权查看该数据产品" subTitle={reason || '当前身份没有发现权限'} />;
  }
  if (state === 'UNAVAILABLE') {
    return <Result status="500" title="数据产品暂时不可用" subTitle={reason || '来源 Provider 暂时无法确认产品状态'} />;
  }
  return (
    <Result
      status="404"
      title={state === 'NOT_DISCOVERABLE' ? '该产品当前不可发现' : '数据产品不存在'}
      subTitle={reason || '请从数据消费目录重新进入'}
    />
  );
};

const owningPath = (product: DataProductView) => (
  product.productKey.productType === 'DATASET'
    ? `/dataset/${product.productKey.sourceIdentity}`
    : `/data-service/api/${product.productKey.sourceIdentity}`
);

export default function ConsumptionDetailPage() {
  const params = useParams<{ productKey: string }>();
  const productKey = decodeURIComponent(params.productKey || '');
  const [product, setProduct] = useState<DataProductView | null>(null);
  const [state, setState] = useState<ProductLookupState>('UNAVAILABLE');
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    void getProduct(productKey)
      .then((result) => {
        if (!active) return;
        setState(result.state);
        setProduct(result.product || null);
        setReason(result.reason || '');
      })
      .catch((cause) => {
        if (!active) return;
        setState('UNAVAILABLE');
        setProduct(null);
        setReason(cause instanceof Error ? cause.message : '加载数据产品失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [productKey]);

  const columns = useMemo<DatasetColumn[]>(() => {
    if (!product || product.productKey.productType !== 'DATASET') return [];
    const value = product.contractPayload.columns;
    return Array.isArray(value) ? value as DatasetColumn[] : [];
  }, [product]);

  const tableColumns: ColumnsType<DatasetColumn> = [
    { title: '字段', dataIndex: 'physicalName', key: 'physicalName' },
    { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
    { title: '类型', dataIndex: 'dataType', key: 'dataType' },
    { title: '角色', dataIndex: 'defaultRole', key: 'defaultRole', render: (value) => value || '-' },
    { title: '可空', dataIndex: 'nullable', key: 'nullable', render: (value) => value ? '是' : '否' },
    { title: '说明', dataIndex: 'description', key: 'description', render: (value) => value || '-' },
  ];

  if (loading) return <div style={{ padding: 48 }}><Text>加载中...</Text></div>;
  if (state !== 'FOUND' || !product) return <div style={{ padding: 24 }}>{lookupResult(state, reason)}</div>;

  const key = productKeyValue(product.productKey);
  const payload = product.contractPayload;
  const providerIssues = (product.sections || []).filter((section) => section.state !== 'READY');

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <Space wrap>
          <Button onClick={() => history.push('/data-analysis/consumption')}>返回数据消费</Button>
          <Button type="primary" onClick={() => history.push(owningPath(product))}>进入来源管理</Button>
          {product.assetRef?.domain === 'ASSET' ? (
            <Button onClick={() => history.push(`/data-asset/detail/${product.assetRef?.identity}`)}>查看资产</Button>
          ) : null}
        </Space>

        <div>
          <Space wrap align="center">
            <Title level={2} style={{ margin: 0 }}>{product.name}</Title>
            <Tag>{product.productKey.productType}</Tag>
            <Tag color="blue">{product.lifecycle}</Tag>
            <Tag color={product.availability === 'UNAVAILABLE' ? 'error' : undefined}>{product.availability}</Tag>
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>{product.description || '暂无描述'}</Paragraph>
          <Text type="secondary">{key}</Text>
        </div>

        {providerIssues.map((section) => (
          <Alert
            key={section.sectionKey}
            type={section.state === 'FORBIDDEN' ? 'error' : 'warning'}
            showIcon
            message={`${section.sectionKey}: ${section.state}`}
            description={`${section.ownerDomain}${section.reason ? ` · ${section.reason}` : ''}`}
          />
        ))}

        <Card title="消费契约">
          <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} bordered size="small">
            <Descriptions.Item label="Owner">{product.owner || '待治理证据'}</Descriptions.Item>
            <Descriptions.Item label="Visibility">{product.visibility || '待安全证据'}</Descriptions.Item>
            <Descriptions.Item label="Project">{product.projectId}</Descriptions.Item>
            <Descriptions.Item label="Active Version">{product.activeVersion?.displayVersion || product.activeVersion?.identity || '-'}</Descriptions.Item>
            <Descriptions.Item label="Access Provider">{product.access.providerState}</Descriptions.Item>
            <Descriptions.Item label="Access Decision">{product.access.decision || '待 #103 接入'}</Descriptions.Item>
            {product.producerRef ? (
              <Descriptions.Item label="Producer">{product.producerRef.domain}:{product.producerRef.identity}</Descriptions.Item>
            ) : null}
            {product.assetRef ? (
              <Descriptions.Item label="Asset">{product.assetRef.domain}:{product.assetRef.identity}</Descriptions.Item>
            ) : null}
          </Descriptions>
        </Card>

        {product.productKey.productType === 'DATASET' ? (
          <Card title={`字段契约 ${product.activeVersion?.displayVersion || ''}`}>
            <Table<DatasetColumn>
              rowKey={(record) => record.fieldId || record.physicalName || String(record.sortOrder)}
              columns={tableColumns}
              dataSource={columns}
              pagination={false}
              size="small"
            />
          </Card>
        ) : (
          <Card title="服务接口契约">
            <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} bordered size="small">
              <Descriptions.Item label="Runtime Path">{String(payload.runtimePath || '-')}</Descriptions.Item>
              <Descriptions.Item label="Auth Mode">{String(payload.authMode || '-')}</Descriptions.Item>
              <Descriptions.Item label="Enabled">{payload.enabled ? '是' : '否'}</Descriptions.Item>
              <Descriptions.Item label="Max Rows">{String(payload.maxRows ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="Timeout(s)">{String(payload.timeoutSeconds ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="Pagination">{payload.paginationEnabled ? '是' : '否'}</Descriptions.Item>
              <Descriptions.Item label="Source Revision">{String(payload.sourceRevisionNo ?? payload.sourceRevisionId ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="Runtime Generation">{String(payload.runtimeGeneration ?? '-')}</Descriptions.Item>
              <Descriptions.Item label="Parameters">
                {Array.isArray(payload.parameterNames) && payload.parameterNames.length
                  ? payload.parameterNames.join(', ')
                  : '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        )}
      </Space>
    </div>
  );
}

import YakOpsEmpty from '@/components/YakOpsEmpty';
import {
  discoverProducts,
  productKeyValue,
  type DataProductView,
  type ProductType,
} from '@/services/consumption';
import { history } from '@umijs/max';
import { Alert, Card, Input, Select, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

const { Text, Title, Paragraph } = Typography;

const TYPE_LABEL: Record<ProductType, string> = {
  DATASET: 'Dataset',
  DATA_SERVICE: 'Data Service',
};

const availabilityColor = (value: DataProductView['availability']) => {
  if (value === 'AVAILABLE') return 'success';
  if (value === 'UNAVAILABLE') return 'error';
  return 'default';
};

export default function ConsumptionDiscoveryPage() {
  const [keyword, setKeyword] = useState('');
  const [productType, setProductType] = useState<ProductType | undefined>();
  const [products, setProducts] = useState<DataProductView[]>([]);
  const [providerStates, setProviderStates] = useState<Record<string, string>>({});
  const [providerReasons, setProviderReasons] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (nextKeyword = keyword, nextType = productType) => {
    setLoading(true);
    setError('');
    try {
      const result = await discoverProducts({
        keyword: nextKeyword.trim() || undefined,
        productType: nextType,
      });
      setProducts(result.products || []);
      setProviderStates(result.providerStates || {});
      setProviderReasons(result.providerReasons || {});
    } catch (cause) {
      setProducts([]);
      setProviderStates({});
      setProviderReasons({});
      setError(cause instanceof Error ? cause.message : '加载数据消费目录失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, productType]);

  useEffect(() => {
    void load('', undefined);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const providerWarnings = useMemo(() => (
    Object.entries(providerStates)
      .filter(([, state]) => state !== 'READY')
      .map(([type, state]) => ({ type, state, reason: providerReasons[type] }))
  ), [providerReasons, providerStates]);

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <div>
          <Title level={2} style={{ marginBottom: 4 }}>数据消费</Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            统一发现已发布 Dataset 与 Data Service。这里展示的是来源域实时投影，不复制来源 Truth。
          </Paragraph>
        </div>

        <Space wrap>
          <Input.Search
            allowClear
            placeholder="按名称或描述搜索"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={(value) => void load(value, productType)}
            style={{ width: 360 }}
          />
          <Select<ProductType>
            allowClear
            placeholder="全部产品类型"
            value={productType}
            options={[
              { value: 'DATASET', label: 'Dataset' },
              { value: 'DATA_SERVICE', label: 'Data Service' },
            ]}
            onChange={(value) => {
              setProductType(value);
              void load(keyword, value);
            }}
            style={{ width: 180 }}
          />
        </Space>

        {error ? <Alert type="error" showIcon message="目录加载失败" description={error} /> : null}
        {providerWarnings.map(({ type, state, reason }) => (
          <Alert
            key={type}
            type="warning"
            showIcon
            message={`${TYPE_LABEL[type as ProductType] || type} Provider ${state}`}
            description={reason || '该来源暂时无法确认结果；当前列表可能是部分结果。'}
          />
        ))}

        <Spin spinning={loading}>
          {!loading && products.length === 0 ? (
            <YakOpsEmpty description="当前筛选条件下没有可发现的数据产品" />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
              {products.map((product) => {
                const key = productKeyValue(product.productKey);
                return (
                  <Card
                    key={key}
                    hoverable
                    onClick={() => history.push(`/data-analysis/consumption/${encodeURIComponent(key)}`)}
                    title={product.name}
                    extra={<Tag>{TYPE_LABEL[product.productKey.productType]}</Tag>}
                  >
                    <Space direction="vertical" size={10} style={{ width: '100%' }}>
                      <Text type="secondary">{product.description || '暂无描述'}</Text>
                      <Space wrap>
                        <Tag color="blue">{product.lifecycle}</Tag>
                        <Tag color={availabilityColor(product.availability)}>{product.availability}</Tag>
                        {product.activeVersion?.displayVersion ? <Tag>{product.activeVersion.displayVersion}</Tag> : null}
                      </Space>
                      <Text type="secondary">ProductKey: {key}</Text>
                      <Text type="secondary">Owner: {product.owner || '待治理证据'}</Text>
                    </Space>
                  </Card>
                );
              })}
            </div>
          )}
        </Spin>
      </Space>
    </div>
  );
}

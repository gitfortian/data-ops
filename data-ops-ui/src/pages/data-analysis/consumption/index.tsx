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
import { AVAILABILITY_LABEL, EVIDENCE_LABEL, LIFECYCLE_LABEL, PRODUCT_TYPE_LABEL } from './presentation';

const { Text, Title, Paragraph } = Typography;

const TYPE_LABEL: Record<ProductType, string> = {
  DATASET: PRODUCT_TYPE_LABEL.DATASET,
  DATA_SERVICE: PRODUCT_TYPE_LABEL.DATA_SERVICE,
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
    <div className="bg-white p-6 max-md:p-4">
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <div>
          <Title level={2} style={{ marginBottom: 4, fontSize: 20 }}>数据产品目录</Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            查找已发布的数据集和 API 服务，了解可用版本、负责人及消费方式。
          </Paragraph>
        </div>

        <Space wrap>
          <Input.Search
            allowClear
            placeholder="按名称或描述搜索"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={(value) => void load(value, productType)}
            style={{ width: 'min(360px, calc(100vw - 40px))' }}
          />
          <Select<ProductType>
            allowClear
            placeholder="全部产品类型"
            value={productType}
            options={[
              { value: 'DATASET', label: '数据集' },
              { value: 'DATA_SERVICE', label: 'API 服务' },
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
            message={`${TYPE_LABEL[type as ProductType] || type}来源：${EVIDENCE_LABEL[state] || state}`}
            description={reason || '该来源暂时无法确认结果；当前列表可能是部分结果。'}
          />
        ))}

        <Spin spinning={loading}>
          {!loading && products.length === 0 ? (
            <YakOpsEmpty title="暂无数据产品" description="当前筛选条件下没有可发现的数据产品" />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))', gap: 16 }}>
              {products.map((product) => {
                const key = productKeyValue(product.productKey);
                return (
                  <Card
                    key={key}
                    hoverable
                    role="link"
                    tabIndex={0}
                    aria-label={`查看数据产品：${product.name}`}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') history.push(`/data-analysis/consumption/${encodeURIComponent(key)}`);
                    }}
                    onClick={() => history.push(`/data-analysis/consumption/${encodeURIComponent(key)}`)}
                    title={<span className="whitespace-normal break-words text-[15px]">{product.name}</span>}
                    extra={<Tag>{TYPE_LABEL[product.productKey.productType]}</Tag>}
                  >
                    <Space direction="vertical" size={10} style={{ width: '100%' }}>
                      <Text type="secondary">{product.description || '暂无描述'}</Text>
                      <Space wrap>
                        <Tag color="blue">{LIFECYCLE_LABEL[product.lifecycle] || product.lifecycle}</Tag>
                        <Tag color={availabilityColor(product.availability)}>{AVAILABILITY_LABEL[product.availability] || product.availability}</Tag>
                        {product.activeVersion?.displayVersion ? <Tag>{product.activeVersion.displayVersion}</Tag> : null}
                      </Space>
                      <Text type="secondary">负责人：{product.owner || '尚未提供'}</Text>
                      <Text type="secondary" className="break-all text-[12px]">产品标识：{key}</Text>
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

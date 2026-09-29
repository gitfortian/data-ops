import {
  cancelSubscription,
  getConsumerImpact,
  getProduct,
  listSubscriptions,
  productKeyValue,
  subscribeToProduct,
  type ConsumerImpact,
  type DataProductView,
  type GovernanceEvidence,
  type ProductLookupState,
  type ProductNavigation,
  type Subscription,
} from '@/services/consumption';
import { history, useModel, useParams, useSearchParams } from '@umijs/max';
import { listDataServiceConsumers, type DataServiceConsumer } from '@/services/data-service/consumer';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  message,
  Result,
  Select,
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

type DataServiceParameter = {
  name: string;
  type?: string;
  required?: boolean;
  description?: string;
  example?: string;
};

type DataServiceResponseField = {
  name: string;
  type?: string;
  nullable?: boolean;
  description?: string;
  example?: string;
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

const factText = (facts: Record<string, unknown>) => {
  const entries = Object.entries(facts || {});
  if (!entries.length) return '无附加事实';
  return entries.map(([key, value]) => `${key}=${String(value)}`).join(' · ');
};

export default function ConsumptionDetailPage() {
  const params = useParams<{ productKey: string }>();
  const [searchParams] = useSearchParams();
  const { initialState } = useModel('@@initialState');
  const actor = initialState?.currentUser?.userName || '';
  const productKey = decodeURIComponent(params.productKey || '');
  const returnAssetId = searchParams.get('returnAssetId');
  const [product, setProduct] = useState<DataProductView | null>(null);
  const [navigation, setNavigation] = useState<ProductNavigation | null>(null);
  const [governanceEvidence, setGovernanceEvidence] = useState<GovernanceEvidence[]>([]);
  const [state, setState] = useState<ProductLookupState>('UNAVAILABLE');
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true);
  const [impact, setImpact] = useState<ConsumerImpact | null>(null);
  const [impactIssue, setImpactIssue] = useState('');
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [subscriptionIssue, setSubscriptionIssue] = useState('');
  const [relationshipLoading, setRelationshipLoading] = useState(false);
  const [subscriptionSaving, setSubscriptionSaving] = useState(false);
  const [dataServiceConsumers, setDataServiceConsumers] = useState<DataServiceConsumer[]>([]);
  const [selectedConsumerId, setSelectedConsumerId] = useState<number>();
  const [consumerListIssue, setConsumerListIssue] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setImpact(null);
    setImpactIssue('');
    setSubscriptions([]);
    setSubscriptionIssue('');
    setDataServiceConsumers([]);
    setSelectedConsumerId(undefined);
    setConsumerListIssue('');
    void getProduct(productKey)
      .then((result) => {
        if (!active) return;
        setState(result.state);
        setProduct(result.product || null);
        setNavigation(result.navigation || null);
        setGovernanceEvidence(result.governanceEvidence || []);
        setReason(result.reason || '');
        if (result.state === 'FOUND') {
          setRelationshipLoading(true);
          void getConsumerImpact(productKey)
            .then((value) => { if (active) setImpact(value); })
            .catch((cause) => {
              if (active) setImpactIssue(cause instanceof Error ? cause.message : '消费影响暂不可用');
            });
          void listSubscriptions(productKey)
            .then((value) => { if (active) setSubscriptions(value); })
            .catch((cause) => {
              if (active) setSubscriptionIssue(cause instanceof Error ? cause.message : '消费订阅暂不可用');
            })
            .finally(() => { if (active) setRelationshipLoading(false); });
          if (result.product?.productKey.productType === 'DATA_SERVICE') {
            void listDataServiceConsumers()
              .then((value) => {
                if (!active) return;
                setDataServiceConsumers(value);
                const apiId = Number(result.product?.productKey.sourceIdentity);
                const eligible = value.filter((consumer) => consumer.enabled
                  && (consumer.accessScope === 'ALL' || consumer.apiIds?.includes(apiId)));
                setSelectedConsumerId(eligible[0]?.id);
              })
              .catch((cause) => {
                if (active) setConsumerListIssue(cause instanceof Error
                  ? cause.message
                  : '读取 Data Service Consumer 失败');
              });
          }
        }
      })
      .catch((cause) => {
        if (!active) return;
        setState('UNAVAILABLE');
        setProduct(null);
        setNavigation(null);
        setGovernanceEvidence([]);
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

  const knownConsumerColumns: ColumnsType<NonNullable<ConsumerImpact['consumers']>[number]> = [
    {
      title: 'Consumer',
      key: 'consumer',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.consumerRef.displayHint || row.consumerRef.sourceIdentity}</Text>
          <Text type="secondary">{row.consumerRef.consumerType} · {row.consumerRef.sourceDomain}:{row.consumerRef.sourceIdentity}</Text>
        </Space>
      ),
    },
    { title: '已声明方式', dataIndex: 'declaredModes', key: 'declaredModes', render: (modes: string[]) => modes?.join(', ') || '-' },
    { title: '成功消费次数', dataIndex: 'successfulUsageCount', key: 'successfulUsageCount' },
    { title: '最近消费', dataIndex: 'lastObservedAt', key: 'lastObservedAt', render: (value) => value ? new Date(value).toLocaleString() : '-' },
    { title: '证据', dataIndex: 'providerEvidenceRefs', key: 'providerEvidenceRefs', render: (refs: string[]) => refs?.length ? refs.join(', ') : '-' },
  ];

  if (loading) return <div style={{ padding: 48 }}><Text>加载中...</Text></div>;
  if (state !== 'FOUND' || !product) return <div style={{ padding: 24 }}>{lookupResult(state, reason)}</div>;

  const key = productKeyValue(product.productKey);
  const payload = product.contractPayload;
  const providerIssues = (product.sections || []).filter((section) => section.state !== 'READY');
  const consumptionMode = product.productKey.productType === 'DATASET' ? 'QUERY' : 'API_INVOKE';
  const ownSubscription = subscriptions.find((item) =>
    item.status === 'ACTIVE'
      && item.consumptionMode === consumptionMode
      && (product.productKey.productType === 'DATASET'
        ? item.consumerRef.consumerType === 'USER'
          && item.consumerRef.sourceDomain === 'SECURITY_PRINCIPAL'
          && item.consumerRef.sourceIdentity === actor
        : item.consumerRef.consumerType === 'DATA_SERVICE'
          && item.consumerRef.sourceDomain === 'DATA_SERVICE_CONSUMER'
          && item.consumerRef.sourceIdentity === String(selectedConsumerId)));
  const eligibleDataServiceConsumers = dataServiceConsumers.filter((consumer) => {
    const apiId = Number(product.productKey.sourceIdentity);
    return consumer.enabled && (consumer.accessScope === 'ALL' || consumer.apiIds?.includes(apiId));
  });
  const changeSubscription = async () => {
    const consumerRef = product.productKey.productType === 'DATASET'
      ? actor
        ? { consumerType: 'USER' as const, sourceDomain: 'SECURITY_PRINCIPAL', sourceIdentity: actor, displayHint: actor }
        : null
      : eligibleDataServiceConsumers.find((consumer) => consumer.id === selectedConsumerId)
        ? {
          consumerType: 'DATA_SERVICE' as const,
          sourceDomain: 'DATA_SERVICE_CONSUMER',
          sourceIdentity: String(selectedConsumerId),
          displayHint: eligibleDataServiceConsumers.find((consumer) => consumer.id === selectedConsumerId)?.name,
        }
        : null;
    if (!consumerRef) {
      message.error('请先选择当前项目内已获此服务授权的 Consumer');
      return;
    }
    setSubscriptionSaving(true);
    try {
      const changed = ownSubscription
        ? await cancelSubscription(ownSubscription.id)
        : await subscribeToProduct(key, consumerRef, consumptionMode);
      setSubscriptions((current) => {
        const rest = current.filter((item) => item.id !== changed.id);
        return [changed, ...rest];
      });
      message.success(ownSubscription ? '已取消消费依赖' : '已声明消费依赖');
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '更新消费依赖失败');
    } finally {
      setSubscriptionSaving(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <Space wrap>
          <Button
            onClick={() => history.push(
              returnAssetId && /^\d+$/.test(returnAssetId)
                ? `/data-asset/detail/${returnAssetId}`
                : '/data-analysis/consumption',
            )}
          >
            {returnAssetId && /^\d+$/.test(returnAssetId) ? '返回资产详情' : '返回数据消费'}
          </Button>
          {navigation?.sourceHref ? (
            <Button
              type="primary"
              disabled={product.availability === 'UNAVAILABLE'}
              onClick={() => history.push(navigation.sourceHref!)}
            >
              {product.productKey.productType === 'DATASET' ? '进入 Dataset 查询' : '打开服务配置'}
            </Button>
          ) : null}
          {navigation?.assetHref ? (
            <Button onClick={() => history.push(navigation.assetHref!)}>查看资产</Button>
          ) : null}
          {navigation?.producerHref ? (
            <Button onClick={() => history.push(navigation.producerHref!)}>查看生产者</Button>
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
            <Descriptions.Item label="Access Decision">
              {product.access.decision || product.access.reason || '当前访问主体与调用平面尚未获得裁决'}
            </Descriptions.Item>
            <Descriptions.Item label="Access Subject">{product.access.subject || '未提供'}</Descriptions.Item>
            <Descriptions.Item label="Access Action">{product.access.action || '未提供'}</Descriptions.Item>
            <Descriptions.Item label="Access Plane">{product.access.plane || '未提供'}</Descriptions.Item>
            {product.access.nextStep ? (
              <Descriptions.Item label="下一步">{product.access.nextStep}</Descriptions.Item>
            ) : null}
            {product.producerRef ? (
              <Descriptions.Item label="Producer">{product.producerRef.domain}:{product.producerRef.identity}</Descriptions.Item>
            ) : null}
            {product.assetRef ? (
              <Descriptions.Item label="Asset">{product.assetRef.domain}:{product.assetRef.identity}</Descriptions.Item>
            ) : null}
          </Descriptions>
        </Card>

        <Card title="治理证据">
          {governanceEvidence.length ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {governanceEvidence.map((evidence, index) => (
                <div key={`${evidence.sectionKey}-${index}`}>
                  <Space wrap>
                    <Text strong>{evidence.sectionKey}</Text>
                    <Tag color={evidence.state === 'READY' ? 'success' : evidence.state === 'FORBIDDEN' ? 'error' : 'warning'}>
                      {evidence.state}
                    </Tag>
                    <Text type="secondary">{evidence.ownerDomain}</Text>
                  </Space>
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary">{factText(evidence.facts)}</Text>
                    {evidence.reason ? <Text type="danger"> · {evidence.reason}</Text> : null}
                  </div>
                </div>
              ))}
            </Space>
          ) : (
            <Text type="secondary">暂无治理证据</Text>
          )}
        </Card>

        <Card title="消费关系与影响">
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space wrap>
              <Button
                type={ownSubscription ? 'default' : 'primary'}
                loading={subscriptionSaving || relationshipLoading}
                disabled={!!subscriptionIssue
                  || (product.productKey.productType === 'DATASET' ? !actor : !selectedConsumerId)}
                onClick={() => { void changeSubscription(); }}
              >
                {ownSubscription ? '取消此消费依赖' : `声明${consumptionMode}依赖`}
              </Button>
              {product.productKey.productType === 'DATA_SERVICE' ? (
                <Select
                  aria-label="Data Service Consumer"
                  style={{ minWidth: 240 }}
                  value={selectedConsumerId}
                  placeholder="选择已授权此服务的 Consumer"
                  options={eligibleDataServiceConsumers.map((consumer) => ({
                    value: consumer.id,
                    label: `${consumer.name} · ${consumer.activeKeyCount} 个有效 Key`,
                  }))}
                  onChange={setSelectedConsumerId}
                  loading={relationshipLoading}
                />
              ) : null}
              <Text type="secondary">订阅只声明依赖，不会授予 Dataset 或 Data Service 的访问权限。</Text>
            </Space>
            {subscriptionIssue ? <Alert type="warning" showIcon message="订阅记录暂不可用" description={subscriptionIssue} /> : null}
            {consumerListIssue ? (
              <Alert
                type="warning"
                showIcon
                message="无法读取可用的 Data Service Consumer"
                description={<>{consumerListIssue} · <Button type="link" onClick={() => history.push('/data-service/access')}>打开 API 调用管理</Button></>}
              />
            ) : null}
            {product.productKey.productType === 'DATA_SERVICE' && !consumerListIssue && eligibleDataServiceConsumers.length === 0 ? (
              <Alert
                type="info"
                showIcon
                message="没有已授权此服务的启用 Consumer"
                description={<Button type="link" onClick={() => history.push('/data-service/access')}>前往配置 Consumer、API 权限和 Key</Button>}
              />
            ) : null}
            {impactIssue ? <Alert type="warning" showIcon message="消费影响暂不可用" description={impactIssue} /> : null}
            {impact ? (
              <>
                <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
                  <Descriptions.Item label="Subscription Evidence">{impact.subscriptionState}</Descriptions.Item>
                  <Descriptions.Item label="Usage Evidence">{impact.usageState}</Descriptions.Item>
                </Descriptions>
                {impact.usageState === 'UNAVAILABLE' || impact.subscriptionState === 'UNAVAILABLE' ? (
                  <Alert type="warning" showIcon message="已知 Consumer 覆盖不完整" description={impact.coverageNote} />
                ) : null}
                {impact.consumers.length ? (
                  <Table
                    rowKey={(row) => `${row.consumerRef.consumerType}:${row.consumerRef.sourceDomain}:${row.consumerRef.sourceIdentity}`}
                    columns={knownConsumerColumns}
                    dataSource={impact.consumers}
                    pagination={false}
                    size="small"
                  />
                ) : impact.subscriptionState === 'EMPTY' && impact.usageState === 'EMPTY' ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前已核对来源窗口内没有订阅或成功消费证据" />
                ) : (
                  <Text type="secondary">没有可显示的完整 Consumer 清单；请查看上方覆盖状态和说明。</Text>
                )}
                <Text type="secondary">{impact.coverageNote}</Text>
              </>
            ) : !impactIssue ? (
              <Text type="secondary">正在读取消费关系与来源证据…</Text>
            ) : null}
          </Space>
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
                {Array.isArray(payload.parameters) && payload.parameters.length
                  ? (payload.parameters as DataServiceParameter[]).map((item) => item.name).join(', ')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Documentation">
                {payload.schemaStale ? '已过期' : payload.documented ? '已维护' : '由当前运行契约生成'}
              </Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5}>请求参数</Typography.Title>
            <Table<DataServiceParameter>
              rowKey="name"
              size="small"
              pagination={false}
              dataSource={Array.isArray(payload.parameters) ? payload.parameters as DataServiceParameter[] : []}
              columns={[
                { title: '名称', dataIndex: 'name', key: 'name' },
                { title: '类型', dataIndex: 'type', key: 'type' },
                { title: '必填', dataIndex: 'required', key: 'required', render: (value) => value ? '是' : '否' },
                { title: '说明', dataIndex: 'description', key: 'description' },
                { title: '示例', dataIndex: 'example', key: 'example' },
              ]}
            />
            <Typography.Title level={5}>响应字段</Typography.Title>
            <Table<DataServiceResponseField>
              rowKey="name"
              size="small"
              pagination={false}
              dataSource={Array.isArray(payload.responseFields) ? payload.responseFields as DataServiceResponseField[] : []}
              columns={[
                { title: '名称', dataIndex: 'name', key: 'name' },
                { title: '类型', dataIndex: 'type', key: 'type' },
                { title: '可空', dataIndex: 'nullable', key: 'nullable', render: (value) => value ? '是' : '否' },
                { title: '说明', dataIndex: 'description', key: 'description' },
                { title: '示例', dataIndex: 'example', key: 'example' },
              ]}
            />
            <Alert
              type="info"
              showIcon
              message="调用需使用此服务配置的 Consumer/API Key"
              description={`Runtime endpoint: ${String(payload.runtimePath || '未提供')}`}
            />
          </Card>
        )}
      </Space>
    </div>
  );
}

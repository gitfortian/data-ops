import {
  cancelSubscription,
  resumeSubscription,
  suspendSubscription,
  getProduct,
  productKeyValue,
  subscribeToProduct,
  type ConsumerImpact,
  type DataProductView,
  type ObservedVersion,
  type GovernanceEvidence,
  type ProductLookupState,
  type ProductNavigation,
  type Subscription,
} from '@/services/consumption';
import { history, useModel, useParams, useSearchParams } from '@umijs/max';
import { listDataServiceConsumers, type DataServiceConsumer } from '@/services/data-service/consumer';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  message,
  Popconfirm,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { loadConsumptionRelationships } from './relationship-load';
import { formatObservedVersion } from './version-evidence';
import { findManagedSubscription, nextSubscriptionAction, type SubscriptionAction } from './subscription-actions';
import { AVAILABILITY_LABEL, EVIDENCE_LABEL, LIFECYCLE_LABEL, PRODUCT_TYPE_LABEL } from './presentation';

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
  const { can } = usePermissionAccess();
  const actor = initialState?.currentUser?.userName || '';
  const productKey = decodeURIComponent(params.productKey || '');
  const returnAssetId = searchParams.get('returnAssetId');
  const returnMetricId = searchParams.get('returnMetricId');
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
  const relationshipRequestId = useRef(0);
  const [subscriptionSaving, setSubscriptionSaving] = useState(false);
  const [dataServiceConsumers, setDataServiceConsumers] = useState<DataServiceConsumer[]>([]);
  const [selectedConsumerId, setSelectedConsumerId] = useState<number>();
  const [consumerListIssue, setConsumerListIssue] = useState('');

  const reloadRelationships = useCallback(async () => {
    const requestId = ++relationshipRequestId.current;
    setRelationshipLoading(true);
    setImpact(null);
    setImpactIssue('');
    setSubscriptions([]);
    setSubscriptionIssue('');
    try {
      const result = await loadConsumptionRelationships(productKey);
      if (requestId !== relationshipRequestId.current) return;
      setImpact(result.impact);
      setImpactIssue(result.impactIssue);
      setSubscriptions(result.subscriptions);
      setSubscriptionIssue(result.subscriptionIssue);
    } catch (cause) {
      if (requestId !== relationshipRequestId.current) return;
      const reason = cause instanceof Error ? cause.message : '消费关系读取失败';
      setImpactIssue(reason);
      setSubscriptionIssue(reason);
    } finally {
      if (requestId === relationshipRequestId.current) setRelationshipLoading(false);
    }
  }, [productKey]);

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
          void reloadRelationships();
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
    return () => { active = false; relationshipRequestId.current += 1; };
  }, [productKey, reloadRelationships]);

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
    { title: '成功消费（本次窗口）', dataIndex: 'successfulUsageCount', key: 'successfulUsageCount' },
    {
      title: '实际使用版本（本次窗口）', dataIndex: 'observedVersions', key: 'observedVersions',
      render: (versions?: ObservedVersion[]) => versions?.length ? (
        <Space direction="vertical" size={2}>
          {versions.map((version) => (
            <Tooltip key={version.sourceVersion.identity}
              title={(version.providerEvidenceRefs || []).join(' · ') || '暂无来源证据编号'}>
              <Text>{formatObservedVersion(version)}</Text>
            </Tooltip>
          ))}
        </Space>
      ) : '-',
    },
    { title: '最近消费', dataIndex: 'lastObservedAt', key: 'lastObservedAt', render: (value) => value ? new Date(value).toLocaleString() : '-' },
    { title: '证据', dataIndex: 'providerEvidenceRefs', key: 'providerEvidenceRefs', render: (refs: string[]) => refs?.length ? refs.join(', ') : '-' },
  ];

  if (loading) return <div style={{ padding: 48 }}><Text>加载中...</Text></div>;
  if (state !== 'FOUND' || !product) return <div style={{ padding: 24 }}>
    {returnMetricId && /^[1-9]\d*$/.test(returnMetricId) && Number.isSafeInteger(Number(returnMetricId)) &&
      <Button onClick={() => history.push(`/metric/manage/${returnMetricId}`)}>返回指标定义与发布证据</Button>}
    {lookupResult(state, reason)}
  </div>;

  const key = productKeyValue(product.productKey);
  const payload = product.contractPayload;
  const providerIssues = governanceEvidence.filter((section) =>
    section.state === 'UNAVAILABLE' || section.state === 'FORBIDDEN');
  const consumptionMode = product.productKey.productType === 'DATASET' ? 'QUERY' : 'API_INVOKE';
  const ownSubscription = findManagedSubscription(
    subscriptions, key, product.productKey.productType, consumptionMode, actor, selectedConsumerId,
  );
  const subscriptionAction = nextSubscriptionAction(ownSubscription);
  // The controller requires Asset UPDATE; external service Consumers additionally need ACCESS.
  const canManageSubscription = can('data-asset:update')
    && (product.productKey.productType === 'DATASET' || can('data-service:access'));
  const eligibleDataServiceConsumers = dataServiceConsumers.filter((consumer) => {
    const apiId = Number(product.productKey.sourceIdentity);
    return consumer.enabled && (consumer.accessScope === 'ALL' || consumer.apiIds?.includes(apiId));
  });
  const changeSubscription = async (action: SubscriptionAction | 'REVOKE') => {
    if (action === 'NONE') return;
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
    if (action === 'SUBSCRIBE' && !consumerRef) {
      message.error('请先选择当前项目内已获此服务授权的 Consumer');
      return;
    }
    if (action !== 'SUBSCRIBE' && !ownSubscription) {
      message.error('当前依赖状态尚未确认，请刷新后重试');
      return;
    }
    setSubscriptionSaving(true);
    try {
      let changed: Subscription;
      switch (action) {
        case 'SUBSCRIBE':
          changed = await subscribeToProduct(key, consumerRef!, consumptionMode);
          break;
        case 'SUSPEND':
          changed = await suspendSubscription(ownSubscription!.id);
          break;
        case 'RESUME':
          changed = await resumeSubscription(ownSubscription!.id);
          break;
        case 'REVOKE':
          changed = await cancelSubscription(ownSubscription!.id);
          break;
      }
      setSubscriptions((current) => {
        const rest = current.filter((item) => item.id !== changed.id);
        return [changed, ...rest];
      });
      const successMessage = {
        SUBSCRIBE: '已声明消费依赖', SUSPEND: '已暂停消费依赖',
        RESUME: '已恢复消费依赖', REVOKE: '已永久撤销消费依赖',
      };
      message.success(successMessage[action]);
      // Subscription is only a declared relationship. Re-read owning usage/impact evidence
      // rather than synthesizing a Consumer from the mutation response.
      await reloadRelationships();
    } catch (cause) {
      message.error(cause instanceof Error ? cause.message : '更新消费依赖失败');
    } finally {
      setSubscriptionSaving(false);
    }
  };

  return (
    <div className="bg-white p-6 max-md:p-4">
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
          {returnMetricId && /^[1-9]\d*$/.test(returnMetricId) && Number.isSafeInteger(Number(returnMetricId)) && (
            <Button onClick={() => history.push(`/metric/manage/${returnMetricId}`)}>返回指标定义与发布证据</Button>
          )}
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
            <Title level={2} style={{ margin: 0, fontSize: 20 }}>{product.name}</Title>
            <Tag>{PRODUCT_TYPE_LABEL[product.productKey.productType] || product.productKey.productType}</Tag>
            <Tag color="blue">{LIFECYCLE_LABEL[product.lifecycle] || product.lifecycle}</Tag>
            <Tag color={product.availability === 'UNAVAILABLE' ? 'error' : undefined}>{AVAILABILITY_LABEL[product.availability] || product.availability}</Tag>
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8 }}>{product.description || '暂无描述'}</Paragraph>
          <Text type="secondary">{key}</Text>
        </div>

        {providerIssues.length > 0 && <details className="rounded-lg border border-[#fedf89] bg-[#fffaeb] p-3">
          <summary className="cursor-pointer text-[13px] font-medium text-[#93370d]">{providerIssues.length} 项治理证据未就绪 · 展开查看原因</summary>
          <div className="mt-3 space-y-2">{providerIssues.map((section) => (
          <Alert
            key={section.sectionKey}
            type={section.state === 'FORBIDDEN' ? 'error' : 'warning'}
            showIcon
            message={`${section.sectionKey}: ${EVIDENCE_LABEL[section.state] || section.state}`}
            description={`${section.ownerDomain}${section.reason ? ` · ${section.reason}` : ''}`}
          />
        ))}</div></details>}

        <Card title="消费契约">
          <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} bordered size="small">
            <Descriptions.Item label="负责人">{product.owner || '待治理证据'}</Descriptions.Item>
            <Descriptions.Item label="可见范围">{product.visibility || '待安全证据'}</Descriptions.Item>
            <Descriptions.Item label="工作空间">{product.projectId}</Descriptions.Item>
            <Descriptions.Item label="当前生效版本">{product.activeVersion?.displayVersion || product.activeVersion?.identity || '-'}</Descriptions.Item>
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
                      {EVIDENCE_LABEL[evidence.state] || evidence.state}
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

        <Card title="消费关系与影响" extra={
          <Button size="small" loading={relationshipLoading} disabled={subscriptionSaving}
            onClick={() => { void reloadRelationships(); }}>重新核对关系与影响</Button>
        }>
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space wrap>
              {ownSubscription ? (
                <Tag color={ownSubscription.status === 'ACTIVE' ? 'success'
                  : ownSubscription.status === 'SUSPENDED' ? 'warning' : 'default'}>
                  依赖状态：{ownSubscription.status === 'ACTIVE' ? '有效'
                    : ownSubscription.status === 'SUSPENDED' ? '已暂停' : '已永久撤销'}
                </Tag>
              ) : null}
              {subscriptionAction !== 'NONE' && (
                <Button
                  type="primary"
                  loading={subscriptionSaving || relationshipLoading}
                  disabled={!!subscriptionIssue || !canManageSubscription
                    || (product.productKey.productType === 'DATASET' ? !actor : !selectedConsumerId)}
                  onClick={() => { void changeSubscription(subscriptionAction); }}
                >
                  {subscriptionAction === 'SUBSCRIBE' ? `声明${consumptionMode}依赖`
                    : subscriptionAction === 'SUSPEND' ? '暂停依赖' : '恢复依赖'}
                </Button>
              )}
              {ownSubscription && ownSubscription.status !== 'REVOKED' && (
                <Popconfirm
                  title="确定永久撤销这条消费依赖？"
                  description="撤销是终态，不能恢复或再次声明相同身份的依赖。临时停止请选择“暂停”。"
                  okText="永久撤销"
                  cancelText="保留依赖"
                  onConfirm={() => { void changeSubscription('REVOKE'); }}
                >
                  <Button danger
                    loading={subscriptionSaving}
                    disabled={relationshipLoading || !!subscriptionIssue || !canManageSubscription}
                  >永久撤销</Button>
                </Popconfirm>
              )}
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
            {ownSubscription?.status === 'REVOKED' ? (
              <Alert showIcon type="info" message="这条消费依赖已永久撤销"
                description="后端将撤销视为终态，不能恢复或用相同的产品、Consumer 与消费方式重复声明。"
              />
            ) : null}
            {!canManageSubscription ? (
              <Alert showIcon type="info" message="当前身份仅可查看消费依赖"
                description="声明、暂停、恢复和撤销需要资产编辑权限；Data Service Consumer 还需要调用方访问管理权限。"
              />
            ) : null}
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
                <Text type="secondary">成功次数与版本仅来自本次最多 200 条成功消费证据，而非历史总量或已发布最新版本；鼠标悬停版本可查看来源证据编号。来源故障时清单可能不完整。</Text>
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

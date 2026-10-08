import type { ConsumerImpact, DataProductView } from '@/services/consumption';
import { history } from '@umijs/max';
import { Alert, Button, Card, Checkbox, Select, Space, Table, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { consumptionEvidenceTarget } from './evidence-navigation';
import {
  reviewableVersions,
  reviewVersionImpact,
  versionImpactReviewText,
  type VersionImpactRow,
} from './version-impact-review';

const { Text } = Typography;

export default function VersionChangeImpactReview({
  product,
  impact,
  impactIssue,
  loading,
}: {
  product: DataProductView;
  impact: ConsumerImpact | null;
  impactIssue: string;
  loading: boolean;
}) {
  const [requestedVersion, setRequestedVersion] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const versions = useMemo(
    () => reviewableVersions(impact, product.activeVersion),
    [impact, product.activeVersion],
  );
  const selected = versions.find((v) => v.identity === requestedVersion) || versions[0];
  const review = reviewVersionImpact(impact, selected);
  // An acknowledgement never survives a new evidence response or a version switch.
  useEffect(() => setAcknowledged(false), [impact, selected?.identity, loading]);

  const copyReview = async () => {
    if (!acknowledged || !impact || !review || loading) return;
    const text = versionImpactReviewText(
      product.productKey.productType + ':' + product.productKey.sourceIdentity,
      String(product.projectId),
      impact,
      review,
      new Date().toISOString(),
    );
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制本次核对快照；未写入审批或发布记录');
    } catch {
      message.error('浏览器剪贴板不可用，未生成持久核对记录');
    }
  };

  const columns = [
    {
      title: '已知 Consumer',
      key: 'consumer',
      render: (_: unknown, row: VersionImpactRow) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.consumer.consumerRef.displayHint || row.consumer.consumerRef.sourceIdentity}</Text>
          <Text type="secondary">
            {row.consumer.consumerRef.consumerType} · {row.consumer.consumerRef.sourceDomain}:{row.consumer.consumerRef.sourceIdentity}
          </Text>
        </Space>
      ),
    },
    {
      title: '版本影响依据',
      key: 'basis',
      render: (_: unknown, row: VersionImpactRow) => row.declaredOnly
        ? '存在有效声明依赖（未观察到此版本成功消费）'
        : '观察到此版本成功消费 ' + row.successfulUsageCount + ' 次',
    },
    {
      title: '最近成功消费',
      key: 'observed',
      render: (_: unknown, row: VersionImpactRow) =>
        row.lastObservedAt ? new Date(row.lastObservedAt).toLocaleString() : '—',
    },
    {
      title: '该版本来源证据',
      key: 'evidence',
      render: (_: unknown, row: VersionImpactRow) => (
        <Space direction="vertical" size={0}>
          {row.evidenceRefs.length ? row.evidenceRefs.map((ref) => {
            const target = consumptionEvidenceTarget(product.productKey, ref);
            return target ? (
              <Button key={ref} type="link" size="small"
                style={{ height: 'auto', whiteSpace: 'normal', textAlign: 'left', padding: 0 }}
                onClick={() => history.push(target.href)} title={target.description}>
                {ref}
              </Button>
            ) : <Text key={ref} type="secondary">{ref}</Text>;
          }) : <Text type="secondary">无版本级来源证据</Text>}
        </Space>
      ),
    },
  ];

  return (
    <Card title="版本变更前 · 已知消费者影响核对">
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Text type="secondary">
          请选择准备替换或修改的精确来源版本。此处只展示已知订阅与本次来源窗口内真实成功消费；
          不将当前发布版本、声明依赖或 Lineage 推断为某次执行的版本。
        </Text>
        <Space wrap>
          <Text>待变更来源版本</Text>
          <Select
            aria-label="待变更来源版本"
            style={{ minWidth: 280 }}
            value={selected?.identity}
            disabled={loading || !versions.length}
            placeholder="当前没有可确认的来源版本"
            options={versions.map((version) => ({
              value: version.identity,
              label: (version.displayVersion || '未标注版本') + ' · ID ' + version.identity
                + (version.identity === product.activeVersion?.identity ? '（当前生效）' : ''),
            }))}
            onChange={setRequestedVersion}
          />
        </Space>
        {impactIssue ? (
          <Alert type="warning" showIcon message="不能核对消费影响来源" description={impactIssue} />
        ) : !impact ? (
          <Text type="secondary">{loading ? '正在重新核对消费事实…' : '尚无可读取的消费者影响快照'}</Text>
        ) : !review ? (
          <Alert type="info" showIcon message="没有可核对的精确来源版本" />
        ) : (
          <>
            <Alert
              type={review.incomplete ? 'warning' : 'info'}
              showIcon
              message={review.incomplete ? '覆盖不完整，需要补充核对' : '仅限当前来源窗口，并非全量历史'}
              description={impact.coverageNote}
            />
            <Space wrap>
              <Text>已观察到该版本：<strong>{review.observedConsumerCount}</strong> 个 Consumer</Text>
              <Text>仅声明依赖：<strong>{review.declaredOnlyConsumerCount}</strong> 个 Consumer</Text>
              <Text>该版本成功使用：<strong>{review.observedSuccessCount}</strong> 次（本次窗口）</Text>
            </Space>
            <Table<VersionImpactRow>
              size="small"
              pagination={false}
              rowKey={(row) => row.consumer.consumerRef.consumerType + ':'
                + row.consumer.consumerRef.sourceDomain + ':' + row.consumer.consumerRef.sourceIdentity}
              columns={columns}
              dataSource={review.rows}
              locale={{ emptyText: '窗口内未发现此版本的成功使用或有效声明；不代表没有历史消费者' }}
              scroll={{ x: 850 }}
            />
            <Text type="secondary">
              {review.incomplete
                ? '至少一个来源不可用或无权访问：必须联系对应证据 Owner 补核，不能视为无影响。'
                : '未观察到目标版本消费不等于不受变更影响；订阅只表示声明依赖，不绑定版本。'}
            </Text>
            <Checkbox checked={acknowledged} disabled={loading} onChange={(event) => setAcknowledged(event.target.checked)}>
              我已核对当前可见证据及其覆盖缺口，理解本次结果不代表所有消费者。
            </Checkbox>
            <div>
              <Button onClick={() => { void copyReview(); }} disabled={!acknowledged || loading}>
                复制本次版本影响核对记录
              </Button>
            </div>
            <Text type="secondary">
              这是一次性人工核对快照，不持久化、不产生审批通过状态，也不阻断 Dataset / Data Service 原发布流程。
            </Text>
          </>
        )}
      </Space>
    </Card>
  );
}

import type { ConsumerImpact, DataProductView, ConsumerRef } from '@/services/consumption';
import { getConsumerImpact, productKeyValue } from '@/services/consumption';
import type { DataServiceConsumer } from '@/services/data-service/consumer';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { history, useSearchParams } from '@umijs/max';
import { Alert, Button, Card, Checkbox, Input, Select, Space, Table, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { consumptionEvidenceTarget } from './evidence-navigation';
import { consumerSourceTarget, consumptionReviewReturnPath } from '@/config/consumer-source-navigation';
import ManagedConsumerConfigurationHint from './ManagedConsumerConfigurationHint';
import type { ManagedConsumerSourceState } from './managed-consumer-configuration';
import { impactEvidenceWindowFacts } from './impact-evidence-coverage';
import { consumerVersionOutreachDraft } from './version-impact-outreach';
import {
  buildVersionChangeCoordinationWorkpack,
  versionChangeCoordinationWorkpackText,
} from './version-change-coordination';
import {
  reviewableVersions,
  selectReviewableVersion,
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
  sourceConsumerState,
  sourceConsumers,
}: {
  product: DataProductView;
  impact: ConsumerImpact | null;
  impactIssue: string;
  loading: boolean;
  sourceConsumerState: ManagedConsumerSourceState;
  sourceConsumers: readonly DataServiceConsumer[];
}) {
  const { can } = usePermissionAccess();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedVersion = searchParams.get('reviewVersion');
  const productKey = productKeyValue(product.productKey);
  const [exactVersionSnapshot, setExactVersionSnapshot] = useState<{
    productKey: string;
    identity: string;
    impact: ConsumerImpact | null;
    error: string;
  } | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [proposedChange, setProposedChange] = useState('');
  const validRequestedVersion = !!requestedVersion && /^[1-9]\d{0,29}$/.test(requestedVersion);
  // Product overview and exact historical version reads are distinct bounded
  // views. Never treat the source reconciliation window as complete history.
  useEffect(() => {
    if (!validRequestedVersion || !requestedVersion || !impact) {
      setExactVersionSnapshot(null);
      return;
    }
    let active = true;
    setExactVersionSnapshot(null);
    void getConsumerImpact(productKey, requestedVersion)
      .then((response) => {
        if (active) setExactVersionSnapshot({
          productKey, identity: requestedVersion, impact: response, error: '',
        });
      })
      .catch((cause) => {
        if (active) setExactVersionSnapshot({
          productKey, identity: requestedVersion, impact: null,
          error: cause instanceof Error ? cause.message : '精确版本证据读取失败',
        });
      });
    return () => { active = false; };
  }, [impact, productKey, requestedVersion, validRequestedVersion]);

  const exact = exactVersionSnapshot?.identity === requestedVersion
    && exactVersionSnapshot.productKey === productKey ? exactVersionSnapshot : null;
  const exactLoading = !!requestedVersion && validRequestedVersion && !exact;
  const reviewLoading = loading || exactLoading;
  const evidenceImpact = requestedVersion
    ? (validRequestedVersion ? exact?.impact ?? null : null)
    : impact;
  const reviewIssue = impactIssue || (requestedVersion && !validRequestedVersion
    ? '精确版本 ID 必须是规范十进制正整数' : exact?.error || '');
  const versions = useMemo(() => {
    const known = reviewableVersions(impact, product.activeVersion);
    if (requestedVersion && exact?.impact) {
      for (const version of reviewableVersions(exact.impact)) {
        if (version.identity === requestedVersion
            && !known.some((item) => item.identity === version.identity)) {
          known.push(version);
        }
      }
    }
    return known;
  }, [impact, product.activeVersion, requestedVersion, exact]);
  const selected = selectReviewableVersion(versions, requestedVersion);
  const review = reviewVersionImpact(evidenceImpact, selected);
  const workpack = evidenceImpact && review
    ? buildVersionChangeCoordinationWorkpack(evidenceImpact, review) : null;
  // An acknowledgement never survives a new evidence response or a version switch.
  useEffect(() => setAcknowledged(false), [impact, exact, selected?.identity, reviewLoading]);
  // Change descriptions belong to one exact source revision, never carry them across versions.
  useEffect(() => setProposedChange(''), [impact, selected?.identity]);

  const copyReview = async () => {
    if (!acknowledged || !evidenceImpact || !review || reviewLoading || reviewIssue) return;
    const text = versionImpactReviewText(
      product.productKey.productType + ':' + product.productKey.sourceIdentity,
      String(product.projectId),
      evidenceImpact,
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

  const copyOutreach = async (row: VersionImpactRow) => {
    if (!acknowledged || !evidenceImpact || !review || reviewLoading || reviewIssue) return;
    try {
      await navigator.clipboard.writeText(consumerVersionOutreachDraft(
        product, evidenceImpact, review, row, proposedChange, new Date().toISOString(),
        { state: sourceConsumerState, consumers: sourceConsumers },
      ));
      message.success('已复制人工沟通草稿；未发送任何通知或生成审批记录');
    } catch {
      message.error('复制沟通草稿失败，请检查浏览器剪贴板权限');
    }
  };

  const copyWorkpack = async () => {
    if (!acknowledged || !evidenceImpact || !review || reviewLoading || reviewIssue) return;
    try {
      await navigator.clipboard.writeText(versionChangeCoordinationWorkpackText(
        product, evidenceImpact, review, proposedChange, new Date().toISOString(),
        { state: sourceConsumerState, consumers: sourceConsumers },
      ));
      message.success('已复制人工协同工作清单；未写入变更计划、通知、确认或审批');
    } catch {
      message.error('复制协同清单失败，请检查浏览器剪贴板权限');
    }
  };

  const consumerSourceLink = (ref: ConsumerRef) => {
    const returnPath = consumptionReviewReturnPath(
      product.productKey.productType, product.productKey.sourceIdentity, selected?.identity,
    );
    const target = consumerSourceTarget(ref, returnPath);
    if (!target || (target.requiredPermission && !can(target.requiredPermission))) return null;
    return (
      <Button type="link" size="small" style={{ padding: 0, height: 'auto' }}
        title={target.description} onClick={() => history.push(target.href)}>
        {target.label}
      </Button>
    );
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
          <ManagedConsumerConfigurationHint
            consumerRef={row.consumer.consumerRef}
            productType={product.productKey.productType}
            productSourceIdentity={product.productKey.sourceIdentity}
            sourceState={sourceConsumerState}
            consumers={sourceConsumers}
          />
          {consumerSourceLink(row.consumer.consumerRef)}
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
    {
      title: '人工沟通',
      key: 'outreach',
      render: (_: unknown, row: VersionImpactRow) => (
        <Button
          type="link"
          size="small"
          disabled={!acknowledged || reviewLoading || !!reviewIssue}
          onClick={() => { void copyOutreach(row); }}
        >
          复制该 Consumer 沟通草稿
        </Button>
      ),
    },
  ];

  return (
    <Card title="版本变更前 · 已知消费者影响核对">
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Text type="secondary">
          请选择准备替换或修改的精确来源版本。普通概览仅展示近期成功消费；选定版本后将单独读取当前 Project 内已持久化的该版本归一化 Usage（仍有 200 条上限）；
          不将当前发布版本、声明依赖或 Lineage 推断为某次执行的版本。可核对的来源对象链接只用于定位真实配置，不代表已确认负责人或授权。
        </Text>
        <Space wrap>
          <Text>待变更来源版本</Text>
          <Select
            aria-label="待变更来源版本"
            style={{ minWidth: 280 }}
            value={selected?.identity}
            disabled={reviewLoading || !versions.length}
            placeholder="当前没有可确认的来源版本"
            options={versions.map((version) => ({
              value: version.identity,
              label: (version.displayVersion || '未标注版本') + ' · ID ' + version.identity
                + (version.identity === product.activeVersion?.identity ? '（当前生效）' : ''),
            }))}
            onChange={(nextIdentity) => {
              const next = new URLSearchParams(searchParams);
              next.set('reviewVersion', nextIdentity);
              setSearchParams(next, { replace: true });
            }}
          />
          {!requestedVersion && selected ? (
            <Button size="small" disabled={loading} onClick={() => {
              const next = new URLSearchParams(searchParams);
              next.set('reviewVersion', selected.identity);
              setSearchParams(next, { replace: true });
            }}>
              按该版本核对历史已归一化 Usage
            </Button>
          ) : null}
        </Space>
        {reviewIssue ? (
          <Alert type="warning" showIcon message="不能核对消费影响来源" description={reviewIssue} />
        ) : exactLoading ? (
          <Text type="secondary">正在按精确来源版本读取当前 Project 内已归一化的历史成功消费…</Text>
        ) : !evidenceImpact ? (
          <Text type="secondary">{loading ? '正在重新核对消费事实…' : '尚无可读取的消费者影响快照'}</Text>
        ) : !review ? (
          <Alert type="warning" showIcon
            message={requestedVersion ? '指定来源版本已不在本次可核对证据中' : '没有可核对的精确来源版本'}
            description={requestedVersion
              ? '当前 Project 的持久化归一化 Usage 中未发现该精确版本；不代表没有历史消费，请核查来源审计。系统不会自动切到别的版本。'
              : undefined}
          />
        ) : (
          <>
            <Alert
              type={review.incomplete ? 'warning' : 'info'}
              showIcon
              message={review.incomplete ? '覆盖不完整，需要补充核对' : '仅限当前来源窗口，并非全量历史'}
              description={evidenceImpact.coverageNote}
            />
            <Space direction="vertical" size={0}>
              {impactEvidenceWindowFacts(evidenceImpact).map((fact) => (
                <Text key={fact} type="secondary">{fact}</Text>
              ))}
            </Space>
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
                ? '来源可能不可用、归一化有缺口或读取窗口达到上限；须按实际原因补核，不能视为无影响。'
                : '未观察到目标版本消费不等于不受变更影响；订阅只表示声明依赖，不绑定版本。'}
            </Text>
            <div>
              <Text strong>拟变更内容（供人工沟通，不持久化）</Text>
              <Input.TextArea
                aria-label="拟变更内容"
                value={proposedChange}
                maxLength={600}
                showCount
                rows={2}
                placeholder="例如：拟调整响应字段；兼容方案和时间尚待双方确认。不要填写密钥、令牌或敏感样本。"
                onChange={(event) => setProposedChange(event.target.value)}
              />
            </div>
            <div>
              <Text strong>本次可见的人工协同任务（不代表已联系或已完成）</Text>
              <Space wrap>
                <Text>实际使用待核对：{workpack?.observedCount ?? 0} 位</Text>
                <Text>声明依赖待确认：{workpack?.declaredOnlyCount ?? 0} 位</Text>
                <Text>来源证据待补核：{workpack?.evidenceGaps.length ?? 0} 项</Text>
              </Space>
              <Text type="secondary">
                还须核查来源窗口之外的历史使用及未登记的外部使用方；即使当前清单为空，也不能视作无影响。
              </Text>
              {workpack?.evidenceGaps.map((gap) => (
                <div key={gap}><Text type="warning">待补核：{gap}</Text></div>
              ))}
            </div>
            <Alert type="info" showIcon message="人工沟通准备，不是已通知状态"
              description="请使用已知 Consumer 身份自行找到真实负责人。复制草稿不会发送消息、记录已读、获得变更确认或形成 Approval/Audit。"
            />
            <Checkbox checked={acknowledged} disabled={reviewLoading} onChange={(event) => setAcknowledged(event.target.checked)}>
              我已核对当前可见证据及其覆盖缺口，理解本次结果不代表所有消费者。
            </Checkbox>
            <div>
              <Button onClick={() => { void copyReview(); }} disabled={!acknowledged || reviewLoading || !!reviewIssue}>
                复制本次版本影响核对记录
              </Button>
              <Button
                style={{ marginLeft: 8 }}
                onClick={() => { void copyWorkpack(); }}
                disabled={!acknowledged || reviewLoading || !!reviewIssue}
              >
                复制完整人工协同工作清单
              </Button>
              <Text type="secondary"> · 逐个 Consumer 的沟通草稿可从上方表格复制（需先勾选覆盖说明）。</Text>
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

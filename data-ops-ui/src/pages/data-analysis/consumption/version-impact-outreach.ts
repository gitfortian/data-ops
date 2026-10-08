import type { ConsumerImpact, DataProductView } from '@/services/consumption';
import type { VersionImpactReview, VersionImpactRow } from './version-impact-review';
import { impactEvidenceWindowFacts } from './impact-evidence-coverage';
import { inspectManagedConsumerConfiguration, type ManagedConsumerReadSnapshot } from './managed-consumer-configuration';

/**
 * This is a copyable human outreach draft, not a dispatched notification or an
 * approval. ConsumerRef is a stable identity, not a verified contact address.
 */
export const consumerVersionOutreachDraft = (
  product: DataProductView,
  impact: ConsumerImpact,
  review: VersionImpactReview,
  row: VersionImpactRow,
  proposedChange: string,
  preparedAt: string,
  sourceSnapshot?: ManagedConsumerReadSnapshot,
): string => {
  const ref = row.consumer.consumerRef;
  const productKey = product.productKey.productType + ':' + product.productKey.sourceIdentity;
  const evidenceState = review.incomplete ? 'PARTIAL / NEEDS_FOLLOW_UP' : 'WINDOW_ONLY / NOT_COMPLETE_HISTORY';
  const basis = row.declaredOnly
    ? '已发现该 Consumer 的有效声明依赖；本次来源窗口内未观察到它成功使用指定版本。声明不绑定任何来源版本，不能因此判断没有影响。'
    : '本次来源窗口内观察到该 Consumer 对指定版本的 ' + row.successfulUsageCount + ' 次成功消费。';
  const note = proposedChange.trim().slice(0, 600);
  return [
    '【人工沟通草稿，尚未发送】数据产品来源版本拟变更核对',
    '产品：' + product.name,
    'ProductKey：' + productKey,
    'Project ID：' + String(product.projectId),
    '待评估来源版本 ID：' + review.version.identity,
    '版本展示：' + (review.version.displayVersion || '未提供'),
    'Consumer：' + (ref.displayHint || ref.sourceIdentity),
    'Consumer 稳定身份：' + ref.consumerType + ':' + ref.sourceDomain + ':' + ref.sourceIdentity,
    '拟变更内容：' + (note || '尚未明确，请来源 Owner 补充'),
    '变更时间/替代版本：待来源 Owner 与消费者确认（本草稿不作承诺）',
    '已知关系依据：' + basis,
    '最近该版本成功使用：' + (row.lastObservedAt || '当前窗口无此版本成功记录'),
    '该 Consumer 的该版本来源证据：' + (row.evidenceRefs.join(', ') || '无'),
    '当前来源配置（仅为读取时快照）：' + (sourceSnapshot
      ? inspectManagedConsumerConfiguration(
        ref, product.productKey.productType, product.productKey.sourceIdentity,
        sourceSnapshot.state, sourceSnapshot.consumers,
      ).label
      : '未核对'),
    '证据范围：' + evidenceState,
    'Subscription Provider：' + impact.subscriptionState,
    'Usage Provider：' + impact.usageState,
    '覆盖说明：' + impact.coverageNote,
    ...impactEvidenceWindowFacts(impact),
    '草稿生成时间：' + preparedAt,
    '',
    '请 Consumer 负责人核对：当前是否依赖此数据产品/来源版本，版本变更可能影响哪些接口、任务或报表，是否需要兼容性评估、迁移安排和沟通窗口。如无法确认请明确说明所需证据。',
    '本清单仅包含当前 Project 内可见、可归属的已知 Consumer 和本次来源窗口，不保证枚举全部历史/外部使用方。ConsumerRef 不提供可信联系方式，需要人工寻找正确负责人。',
    '此内容未经发送，不代表消费者已阅读/回复/同意，也不是发布授权、审批或持久审计。',
  ].join('\n');
};

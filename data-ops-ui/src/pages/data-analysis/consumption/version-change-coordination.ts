import type { ConsumerImpact, DataProductView } from '@/services/consumption';
import type { VersionImpactReview } from './version-impact-review';
import { impactEvidenceGaps, impactEvidenceWindowFacts } from './impact-evidence-coverage';

export interface VersionChangeCoordinationTask {
  consumerIdentity: string;
  basis: 'OBSERVED_VERSION_USAGE' | 'DECLARED_UNBOUND_DEPENDENCY';
  successfulUsageCount: number;
  evidenceRefs: string[];
  summary: string;
}

export interface VersionChangeCoordinationWorkpack {
  tasks: VersionChangeCoordinationTask[];
  evidenceGaps: string[];
  observedCount: number;
  declaredOnlyCount: number;
  incomplete: boolean;
}

/**
 * Derives *tasks for a human*, not notification/response/approval statuses.
 * Both the input and its source evidence are scoped by the existing project-bound backend.
 */
export const buildVersionChangeCoordinationWorkpack = (
  impact: ConsumerImpact,
  review: VersionImpactReview,
): VersionChangeCoordinationWorkpack => {
  const tasks = review.rows.map((row): VersionChangeCoordinationTask => {
    const ref = row.consumer.consumerRef;
    const consumerIdentity = ref.consumerType + ':' + ref.sourceDomain + ':' + ref.sourceIdentity;
    return {
      consumerIdentity,
      basis: row.declaredOnly ? 'DECLARED_UNBOUND_DEPENDENCY' : 'OBSERVED_VERSION_USAGE',
      successfulUsageCount: row.successfulUsageCount,
      evidenceRefs: row.evidenceRefs,
      summary: row.declaredOnly
        ? '确认声明依赖是否仍有效、是否依赖拟变更来源；当前窗口无该版本执行证据'
        : '与 Consumer 负责人核对版本兼容性、实际依赖、迁移方式和沟通窗口',
    };
  });
  const evidenceGaps = impactEvidenceGaps(impact);
  return {
    tasks,
    evidenceGaps,
    observedCount: tasks.filter((task) => task.basis === 'OBSERVED_VERSION_USAGE').length,
    declaredOnlyCount: tasks.filter((task) => task.basis === 'DECLARED_UNBOUND_DEPENDENCY').length,
    incomplete: evidenceGaps.length > 0 || review.incomplete,
  };
};

/** A portable human coordination brief, never a persisted plan or a dispatched notice. */
export const versionChangeCoordinationWorkpackText = (
  product: DataProductView,
  impact: ConsumerImpact,
  review: VersionImpactReview,
  proposedChange: string,
  preparedAt: string,
): string => {
  const pack = buildVersionChangeCoordinationWorkpack(impact, review);
  const note = proposedChange.trim().slice(0, 600) || '尚未明确，需来源 Owner 补充';
  return [
    '【手动变更协同工作清单，尚未发送/确认/批准】',
    'Project ID：' + String(product.projectId),
    'ProductKey：' + product.productKey.productType + ':' + product.productKey.sourceIdentity,
    '产品：' + product.name,
    '待评估来源版本 ID：' + review.version.identity,
    '版本展示：' + (review.version.displayVersion || '未提供'),
    '拟变更内容：' + note,
    '变更时间/替代版本：待双方明确；本清单不承诺发布或退休',
    '生成时间：' + preparedAt,
    '本次窗口已观察版本消费的已知 Consumer：' + pack.observedCount,
    '仅声明依赖的已知 Consumer：' + pack.declaredOnlyCount,
    '本次窗口内该版本成功使用次数：' + review.observedSuccessCount,
    'Subscription Provider：' + impact.subscriptionState,
    'Usage Provider：' + impact.usageState,
    '证据范围：' + (pack.incomplete ? 'PARTIAL / NEEDS_FOLLOW_UP' : 'WINDOW_ONLY / NOT_COMPLETE_HISTORY'),
    '覆盖说明：' + impact.coverageNote,
    ...impactEvidenceWindowFacts(impact),
    '',
    '一、逐 Consumer 人工核对任务（不是已联系/已完成的状态）',
    ...(pack.tasks.length ? pack.tasks.flatMap((task, index) => [
      (index + 1) + '. ' + task.consumerIdentity,
      '   依据：' + (task.basis === 'OBSERVED_VERSION_USAGE'
        ? '该精确版本观察到 ' + task.successfulUsageCount + ' 次成功使用'
        : '有效声明依赖，不绑定版本；当前窗口未观察到该精确版本成功使用'),
      '   待办：' + task.summary,
      '   该版本来源证据：' + (task.evidenceRefs.join(', ') || '无'),
    ]) : ['本次窗口没有可识别的 Consumer；不能据此推断无人受影响。']),
    '',
    '二、来源证据补核',
    ...(pack.evidenceGaps.length ? pack.evidenceGaps.map((gap) => '- ' + gap) : ['- 本次查询未报告 Provider 失联或拒绝；不意味着已覆盖全部历史。']),
    '- 必须核对当前证据窗口之外的历史消费、可能未登记的外部消费方及 Consumer 负责人归属。',
    '- 无法证明的消费者影响保持未知，不得作为发布授权或无影响结论。',
    '',
    '三、协同执行提示',
    '- 从独立 Consumer 草稿核对各方兼容性、迁移建议和拟议时间；人工寻找可信负责人。',
    '- 将回复及后续决策交由经正式批准的归属流程保存；本页面不创建回执、审批、通知或审计记录。',
    '- 此工作清单只是当前 Project 可见的证据衍生文本，不持久化、不保证完整性、尚未送达任何 Consumer。',
  ].join('\n');
};

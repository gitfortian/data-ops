import { formatAssetTime } from '../constants';

export interface ConsumerUsageFactsValue {
  consumerCount?: number | null;
  userCount?: number | null;
  teamCount?: number | null;
  dashboardCount?: number | null;
  dataServiceCount?: number | null;
  jobCount?: number | null;
  successfulUsageCount?: number | null;
  activeSubscriptionCount?: number | null;
  lastObservedAt?: string | null;
  coverageNote?: string | null;
  subscriptionState?: string | null;
  usageState?: string | null;
  subscriptionWindowLimit?: number | null;
  usageWindowLimit?: number | null;
  subscriptionWindowState?: string | null;
  usageWindowState?: string | null;
  sourceReconciliation?: string | null;
}

const PROVIDER_LABEL: Record<string, string> = { READY: '可读', EMPTY: '窗口内为空',
  UNAVAILABLE: '暂不可用', FORBIDDEN: '无权读取' };
const EXTENT_LABEL: Record<string, string> = { LIMIT_REACHED: '已满窗，可能遗漏更早记录',
  WITHIN_LIMIT: '未满窗，不证明历史完整', UNKNOWN: '范围未知' };
const providerText = (state?: string | null) => PROVIDER_LABEL[state || ''] || '状态未知';
const extentText = (state?: string | null) => EXTENT_LABEL[state || ''] || '范围未知';

/** The source owns counts and scope; a missing side never renders as a successful zero. */
export default function ConsumerUsageFacts({ value }: { value: ConsumerUsageFactsValue }) {
  return (
    <div className="flex flex-col gap-1">
      <span>
        窗口内已知 {value.consumerCount ?? '未知'} 个消费者（用户 {value.userCount ?? '未知'}、团队 {value.teamCount ?? '未知'}、仪表盘 {value.dashboardCount ?? '未知'}、服务 {value.dataServiceCount ?? '未知'}、任务 {value.jobCount ?? '未知'}）
      </span>
      <span>
        成功使用 {value.successfulUsageCount ?? '未知'} 次，活动订阅 {value.activeSubscriptionCount ?? '未知'} 个；最近业务消费 {formatAssetTime(value.lastObservedAt)}
      </span>
      <span>有效订阅来源：{providerText(value.subscriptionState)}；窗口最多 {value.subscriptionWindowLimit ?? '未知'} 行，{extentText(value.subscriptionWindowState)}。</span>
      <span>归一化使用来源：{providerText(value.usageState)}；窗口最多 {value.usageWindowLimit ?? '未知'} 行，{extentText(value.usageWindowState)}。</span>
      <span>{value.sourceReconciliation === 'NOT_PERFORMED' ? '本次未同步原始来源，时效与历史覆盖需人工核对。' : '原始来源同步范围未知。'}已知消费者仅为可读窗口的去重并集。</span>
      {value.coverageNote ? <span className="text-[12px] text-[#98a2b3]">{value.coverageNote}</span> : null}
    </div>
  );
}

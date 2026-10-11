import { Alert, Button, Card, Collapse, Space, Tag, Typography } from 'antd';
import type { ProcessJourneyReview, ProcessJourneyStatus } from './processJourneyReview';

interface Props {
  report?: ProcessJourneyReview;
  loading: boolean;
  onReload: () => void;
  onOpenProcess: (key: 'grain' | 'source' | 'standard') => void;
  onOpenLogical: () => void;
  onOpenPhysical: () => void;
  onOpenEvidence: (key: 'source' | 'standard', id: number) => void;
}
const meta: Record<ProcessJourneyStatus, { label: string; color: string }> = {
  RECORDED: { label: '已有当前记录', color: 'blue' },
  NEEDS_ATTENTION: { label: '需要核对', color: 'orange' },
  NOT_STARTED: { label: '尚未开始', color: 'default' },
  UNAVAILABLE: { label: '证据不可读取', color: 'red' },
};

export default function ProcessJourneyReviewPanel({
  report, loading, onReload, onOpenProcess, onOpenLogical, onOpenPhysical, onOpenEvidence,
}: Props) {
  return (
    <Card className="mb-4" loading={loading} title="从业务过程到数仓设计 · 建设接续"
      extra={<Button onClick={onReload} loading={loading}>重新核验</Button>}>
      {!report ? <Alert showIcon type="warning" message="暂无完整的建设上下文"
        description="业务过程或当前授权事实尚不可用；不能按无数据处理。" /> : <>
        <Alert showIcon className="mb-3" type={report.unknownCount ? 'warning' : 'info'}
          message={report.unknownCount
            ? `有 ${report.unknownCount} 项领域事实不可读取，请先核对项目权限或重试`
            : report.attentionCount
              ? `有 ${report.attentionCount} 项建设信息需要补充或人工核对`
              : '当前基础设计事实可查看，仍需继续完成领域审核与实际数据交付'}
          description={report.explanation} />
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
          {report.steps.map((step, index) => (
            <div key={step.key} className="min-w-0 rounded-lg border border-[#e4e7ec] p-3">
              <Space wrap size={4}>
                <Typography.Text strong>{index + 1}. {step.name}</Typography.Text>
                <Tag color={meta[step.status].color}>{meta[step.status].label}</Tag>
              </Space>
              <Typography.Paragraph className="!mb-2 !mt-2" type="secondary">
                {step.summary}
              </Typography.Paragraph>
              {step.evidence?.length ? (
                <Collapse ghost size="small" items={[{
                  key: step.key, label: `逐项查看（${step.evidence.length}）`,
                  children: <div className="max-h-52 overflow-y-auto">
                    {step.evidence.map(item => (
                      <div key={item.id} className="mb-2 rounded border border-[#f0f0f0] p-2">
                        <Space size={4} wrap>
                          <Typography.Text strong>{item.label}</Typography.Text>
                          {item.status === 'REVIEW' && <Tag color="orange">需核对</Tag>}
                        </Space>
                        <Typography.Paragraph type="secondary" className="!mb-0 !text-xs">
                          {item.detail}
                        </Typography.Paragraph>
                        {(step.key === 'source' || step.key === 'standard')
                          && item.status === 'REVIEW' && (
                          <Button type="link" size="small" className="!px-0"
                            onClick={() => onOpenEvidence(step.key as 'source' | 'standard', Number(item.id))}>
                            定位此对象
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>,
                }]} />
              ) : step.status === 'UNAVAILABLE' ? (
                <Typography.Text type="danger">不能读取对象清单，请核对权限并重新核验。</Typography.Text>
              ) : null}
              {step.action === 'process' && <Button type="link" className="!px-0"
                onClick={() => onOpenProcess(step.key as 'grain' | 'source' | 'standard')}>查看过程配置</Button>}
              {step.action === 'logical' && <Button type="link" className="!px-0"
                onClick={onOpenLogical}>进入逻辑设计</Button>}
              {step.action === 'physical' && <Button type="link" className="!px-0"
                onClick={onOpenPhysical}>查看物理模型</Button>}
            </div>
          ))}
        </div>
        <Typography.Paragraph type="secondary" className="!mb-0 !mt-3">
          业务过程、逻辑草稿、物理模型由各自领域管理；这里只给出当前事实与下一步，不表示数据已经部署、加工、验证或开放消费。
        </Typography.Paragraph>
      </>}
    </Card>
  );
}

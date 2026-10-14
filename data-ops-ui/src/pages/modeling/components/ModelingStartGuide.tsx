import { Button, Tag, Typography } from 'antd';
import type { SemanticProcessRecord } from '@/services/semantic/types';

export default function ModelingStartGuide({
  process, onBusiness, onLogical, onSource, onCreate,
  canCreate, loading,
}: {
  process?: SemanticProcessRecord;
  onBusiness: () => void;
  onLogical: () => void;
  onSource: () => void;
  onCreate: () => void;
  canCreate: boolean;
  loading: boolean;
}) {
  const stages = [
    { title: '业务过程与粒度', detail: process
      ? `${process.name} · ${process.grain || '粒度待确认'}` : '选择当前项目业务过程', status: process ? '已选上下文' : '待选择' },
    { title: '逻辑实体与关系', detail: '编辑逻辑草稿，明确主键并冻结版本', status: '单独核验' },
    { title: '物理模型与字段', detail: '创建表结构草稿、核对逻辑依据和标准', status: '当前工作区' },
    { title: '来源、版本与执行', detail: '分别核对映射与版本；DataDev 另行交接', status: '独立证据' },
  ];
  return <section aria-label="建模工作路径" className="mb-4 rounded-xl border border-[#dfe6ed] bg-white p-4 shadow-[0_4px_18px_rgba(15,23,42,.025)]">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <Typography.Text strong className="!text-[15px]">从业务理解到物理建模</Typography.Text>
        <Typography.Paragraph type="secondary" className="!mb-0 !mt-1 !text-[12px]">
          逻辑设计描述业务粒度，物理模型承载技术实现；ODS 可从已验证来源表直接建模。每一步独立保存与核对，不以进入页面代表任务完成。
        </Typography.Paragraph>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="small" onClick={onBusiness}>业务过程</Button>
        <Button size="small" onClick={onLogical}>逻辑模型</Button>
        <Button size="small" onClick={onSource}>从来源表建 ODS</Button>
        <Button size="small" type="primary" disabled={!canCreate || loading} onClick={onCreate}>创建物理草稿</Button>
      </div>
    </div>
    <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
      {stages.map((stage, index) => <div key={stage.title}
        className="min-w-0 rounded-lg border border-[#e7edf2] bg-[#f8fafc] px-3 py-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-[13px] font-semibold text-[#344054]">{index + 1}. {stage.title}</span>
          <Tag className="!m-0">{stage.status}</Tag>
        </div>
        <div className="mt-2 break-words text-[12px] leading-5 text-[#667085]">{stage.detail}</div>
      </div>)}
    </div>
  </section>;
}

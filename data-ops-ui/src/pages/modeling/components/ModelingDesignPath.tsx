import { Tag, theme } from 'antd';
import type { ModelingModelRecord } from '@/services/modeling/types';

const LAYER_GUIDANCE: Record<string, { title: string; purpose: string; mapping: string }> = {
  ODS: { title: '贴源结构设计', purpose: '核对来源表与字段，保存目标结构和来源映射。', mapping: 'mapping' },
  DIM: { title: '公共维度设计', purpose: '核对业务键、维度属性与历史策略，再落实字段和版本。', mapping: 'mapping' },
  DWD: { title: '明细事实设计', purpose: '以业务粒度组织事实字段、维度关联与明细来源。', mapping: 'mapping' },
  DWS: { title: '主题汇总设计', purpose: '明确统计周期、分组维度与聚合口径，核对分层映射。', mapping: 'layer-mapping' },
  ADS: { title: '应用结果设计', purpose: '围绕应用结果粒度核对字段与上游来源，形成可复核的表结构。', mapping: 'layer-mapping' },
};

export default function ModelingDesignPath({ model, activeTab, onChangeTab }: {
  model: ModelingModelRecord;
  activeTab: string;
  onChangeTab: (key: string) => void;
}) {
  const { token } = theme.useToken();
  const layer = model.layerCode?.toUpperCase() || '';
  const guidance = LAYER_GUIDANCE[layer] || {
    title: '物理模型设计', purpose: '核对业务依据、表结构、来源和版本，完成当前模型设计。', mapping: 'mapping',
  };
  const steps = [
    { key: layer === 'ODS' ? 'overview' : 'logical', name: layer === 'ODS' ? '来源与归属' : '业务设计', detail: layer === 'ODS' ? '核对来源表及过程' : '核对逻辑实体与粒度' },
    { key: 'structure', name: '表结构设计', detail: '字段、键、索引与分区' },
    { key: guidance.mapping, name: layer === 'DWS' || layer === 'ADS' ? '分层来源' : '来源确认', detail: '人工确认字段来源' },
    { key: 'version', name: '版本交付', detail: '复核与发布设计版本' },
  ];
  return <section aria-label="当前模型设计路径" className="mt-5 rounded-xl border border-[#e4e9f0] bg-[#f8fafc] p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <div className="flex items-center gap-2"><Tag className="!m-0">{layer || '未分层'}</Tag><span className="text-[14px] font-semibold text-[#344054]">{guidance.title}</span></div>
        <p className="mb-0 mt-2 text-[12px] text-[#667085]">{guidance.purpose}</p>
      </div>
      <span className="text-[12px] text-[#667085]">{model.publishedVersionId ? `已发布设计版本 #${model.publishedVersionId}` : '发布版本在版本页核对'}</span>
    </div>
    <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
      {steps.map((step, index) => <button type="button" key={step.key}
        aria-current={activeTab === step.key ? 'step' : undefined}
        onClick={() => onChangeTab(step.key)}
        style={activeTab === step.key ? { borderColor: token.colorPrimaryBorder, background: token.colorPrimaryBg } : undefined}
        className="rounded-lg border border-[#e4e9f0] bg-white px-3 py-3 text-left transition-colors hover:bg-[#f2f4f7] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#667085]">
        <span className="block text-[13px] font-medium text-[#344054]">{index + 1}. {step.name}</span>
        <span className="mt-1 block text-[11px] text-[#98a2b3]">{step.detail}</span>
      </button>)}
    </div>
    <p className="mb-0 mt-3 text-[11px] text-[#98a2b3]">各步骤独立保存与核验。物理部署和加工运行需在数据开发中查看实际证据。</p>
  </section>;
}

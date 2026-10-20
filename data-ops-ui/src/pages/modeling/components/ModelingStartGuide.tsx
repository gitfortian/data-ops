import { ArrowRight, Database, GitBranch, Workflow } from 'lucide-react';
import type { SemanticProcessRecord } from '@/services/semantic/types';

/** Human-operated journey entry, not a fabricated stage-completion indicator. */
export default function ModelingStartGuide({
  process, onBusiness, onLogical, onSource, onCreate, canCreate, loading,
}: {
  process?: SemanticProcessRecord;
  onBusiness: () => void;
  onLogical: () => void;
  onSource: () => void;
  onCreate: () => void;
  canCreate: boolean;
  loading: boolean;
}) {
  return (
    <section className="modeling-studio-guide" aria-label="建模任务入口">
      <div className="modeling-studio-section-header">
        <div>
          <span className="modeling-studio-overline">从业务定义到可追溯设计</span>
          <h2>选择当前要完成的建模任务</h2>
          <p>{process
            ? '当前业务过程：' + process.name + ' · ' + (process.grain || '粒度待确认')
            : '先选择业务过程与粒度，再设计逻辑和物理模型；ODS 可从来源表直接建立。'}</p>
        </div>
        <span className="modeling-studio-hint">各步骤独立保存、审核与发布</span>
      </div>
      <div className="modeling-studio-guide-grid">
        <button type="button" onClick={onBusiness} className="modeling-studio-guide-item">
          <span className="modeling-studio-guide-number">01</span>
          <span className="modeling-studio-guide-symbol"><Workflow size={22} aria-hidden /></span>
          <strong>理解业务与粒度</strong><span>选择过程，明确一行数据代表什么</span>
          <span className="modeling-studio-guide-link">业务过程 <ArrowRight size={15} aria-hidden /></span>
        </button>
        <button type="button" onClick={onLogical} className="modeling-studio-guide-item">
          <span className="modeling-studio-guide-number">02</span>
          <span className="modeling-studio-guide-symbol"><GitBranch size={22} aria-hidden /></span>
          <strong>设计业务逻辑模型</strong><span>定义实体、属性、关系并冻结设计版本</span>
          <span className="modeling-studio-guide-link">逻辑建模 <ArrowRight size={15} aria-hidden /></span>
        </button>
        <div className="modeling-studio-guide-item modeling-studio-guide-item-final">
          <span className="modeling-studio-guide-number">03</span>
          <span className="modeling-studio-guide-symbol"><Database size={22} aria-hidden /></span>
          <strong>构建物理模型</strong><span>选择分层和目标，编辑表结构与可信映射</span>
          <div className="modeling-studio-guide-operations">
            <button type="button" onClick={onCreate} disabled={!canCreate || loading}>创建物理草稿</button>
            <button type="button" onClick={onSource}>从来源表建 ODS</button>
          </div>
        </div>
      </div>
    </section>
  );
}

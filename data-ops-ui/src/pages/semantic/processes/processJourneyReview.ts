import type { LogicalModelDraft } from '@/services/modeling/logical';
import type { ModelingMainlineCoverage } from '@/services/modeling/types';
import type {
  SemanticFieldRecord, SemanticProcessRecord, SemanticProcessSourceRecord,
} from '@/services/semantic/types';

/** A presentation-only projection. Domain services remain the only fact owners. */
export type SourceState<T> =
  | { state: 'AVAILABLE'; value: T }
  | { state: 'UNAVAILABLE' };
export const available = <T,>(value: T): SourceState<T> => ({ state: 'AVAILABLE', value });
export const unavailable = <T,>(): SourceState<T> => ({ state: 'UNAVAILABLE' });

export type ProcessJourneyStatus = 'RECORDED' | 'NEEDS_ATTENTION' | 'NOT_STARTED' | 'UNAVAILABLE';
export interface ProcessJourneyEvidence {
  id: string;
  label: string;
  detail: string;
  status: 'OK' | 'REVIEW';
  /** Only supported owner evidence may offer cross-workspace governance navigation. */
  governanceTarget?: 'logical' | 'physical';
}
export interface ProcessJourneyStep {
  key: 'grain' | 'source' | 'standard' | 'logical' | 'physical';
  name: string;
  status: ProcessJourneyStatus;
  summary: string;
  evidence?: ProcessJourneyEvidence[];
  action: 'process' | 'logical' | 'physical';
}
export interface ProcessJourneyReview {
  processId: number;
  steps: ProcessJourneyStep[];
  unknownCount: number;
  attentionCount: number;
  /** Recorded means an owner returned records, not delivery success or approval. */
  explanation: string;
}

export const buildProcessJourneyReview = (input: {
  process: SemanticProcessRecord;
  sources: SourceState<SemanticProcessSourceRecord[]>;
  standards: SourceState<SemanticFieldRecord[]>;
  logical: SourceState<LogicalModelDraft[]>;
  physical: SourceState<ModelingMainlineCoverage>;
}): ProcessJourneyReview => {
  const { process } = input;
  const steps: ProcessJourneyStep[] = [];
  const add = (key: ProcessJourneyStep['key'], name: string,
    status: ProcessJourneyStatus, summary: string, action: ProcessJourneyStep['action'],
    evidence?: ProcessJourneyEvidence[]) =>
    steps.push({ key, name, status, summary, action, evidence });

  add('grain', '业务过程与粒度', process.grain?.trim() ? 'RECORDED' : 'NEEDS_ATTENTION',
    process.grain?.trim() ? `当前定义：${process.grain}` : '尚未明确业务粒度；不能根据源表名称猜测', 'process',
    [{ id: String(process.id), label: process.name, status: process.grain?.trim() ? 'OK' : 'REVIEW',
      detail: process.grain?.trim() || '业务粒度尚未填写，需业务负责人确认订单头/明细粒度' }]);

  if (input.sources.state === 'UNAVAILABLE') {
    add('source', '业务过程来源', 'UNAVAILABLE', '来源读取失败或当前无权访问；不能视为没有来源', 'process');
  } else {
    const sources = input.sources.value;
    const main = sources.filter(item => item.tableRole === 'MAIN');
    add('source', '业务过程来源',
      !sources.length ? 'NOT_STARTED' : main.length === 1 ? 'RECORDED' : 'NEEDS_ATTENTION',
      !sources.length ? '尚未关联来源表' : main.length === 1
        ? `已关联 ${sources.length} 张来源表；MAIN 关系已声明，未证明数据已经采集或可运行`
        : `已关联 ${sources.length} 张来源表，但 MAIN 数量为 ${main.length}，需要人工核对`, 'process',
      sources.map(item => ({
        id: String(item.id), label: item.sourceTable,
        detail: `数据源 #${item.datasourceId} · ${item.tableRole} · ${item.joinCondition || '未声明关联条件'}`,
        status: item.tableRole === 'MAIN' && main.length === 1 ? 'OK'
          : main.length === 1 ? 'OK' : 'REVIEW',
      })));
  }

  if (input.standards.state === 'UNAVAILABLE') {
    add('standard', '过程标准字段', 'UNAVAILABLE', 'Semantic 标准引用无法核验；不能当作零个标准', 'process');
  } else {
    const fields = input.standards.value;
    const invalid = fields.filter(f => f.status !== 'ENABLED');
    add('standard', '过程标准字段', !fields.length ? 'NOT_STARTED'
      : invalid.length ? 'NEEDS_ATTENTION' : 'RECORDED',
    !fields.length ? '尚未引用正式标准字段'
      : invalid.length ? `已引用 ${fields.length} 个标准字段，其中 ${invalid.length} 个当前未确认启用，需人工复核`
        : `已引用 ${fields.length} 个当前启用的标准字段；未证明物理类型与发布版本一致`, 'process',
      fields.map(item => ({
        id: String(item.id), label: `${item.name} (${item.code})`,
        detail: `标准 #${item.id} · 角色 ${item.role} · 当前状态 ${item.status || '未知'}`,
        status: item.status === 'ENABLED' ? 'OK' : 'REVIEW',
      })));
  }

  if (input.logical.state === 'UNAVAILABLE') {
    add('logical', '逻辑业务设计', 'UNAVAILABLE', 'Modeling 逻辑草稿暂不可访问，不能认为没有草稿', 'logical');
  } else {
    const models = input.logical.value.filter(m => m.processId === process.id);
    add('logical', '逻辑业务设计', models.length ? 'RECORDED' : 'NOT_STARTED',
      models.length ? `该过程有 ${models.length} 份逻辑草稿；草稿和冻结快照均不是物理发布`
        : '该过程尚无逻辑设计草稿，可从正式业务过程开始', 'logical',
      models.map(item => ({ id: String(item.id), label: `${item.name} (${item.code})`,
        detail: `逻辑草稿 #${item.id} · ${item.status} · 仅供设计，不是已发布物理版本`,
        status: 'OK' as const, governanceTarget: 'logical' as const })));
  }

  if (input.physical.state === 'UNAVAILABLE'
      || (input.physical.state === 'AVAILABLE' && input.physical.value?.processId !== process.id)) {
    add('physical', '物理模型设计', 'UNAVAILABLE', 'Modeling 物理模型覆盖不可访问或过程身份不符', 'physical');
  } else {
    const count = input.physical.value.totalModels;
    add('physical', '物理模型设计', count > 0 ? 'RECORDED' : 'NOT_STARTED',
      count > 0 ? `该过程关联 ${count} 个物理模型；建模发布不等于部署、执行或合法消费`
        : '尚无该过程的物理模型；ODS 来源镜像可以先于逻辑模型建立', 'physical',
      input.physical.value.layers.flatMap(layer => layer.models.map(model => ({
        id: String(model.modelId), label: `${model.name} (${model.code})`,
        detail: `${layer.layerCode} · ${model.status || '状态未知'} · 发布不等于部署运行`,
        status: 'OK' as const,
        governanceTarget: ['DWD', 'DIM'].includes(layer.layerCode?.toUpperCase() || '')
          ? 'physical' as const : undefined,
      }))));
  }

  return {
    processId: process.id, steps,
    unknownCount: steps.filter(s => s.status === 'UNAVAILABLE').length,
    attentionCount: steps.filter(s => s.status === 'NEEDS_ATTENTION' || s.status === 'NOT_STARTED').length,
    explanation: '仅展示当前有权访问的 Semantic/Modeling 事实与待办提示；不创建第二套进度记录，不表示质量、执行、发布或消费已验收。',
  };
};

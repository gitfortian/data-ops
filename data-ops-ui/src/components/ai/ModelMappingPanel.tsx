import StructuredSuggestionPanel from './StructuredSuggestionPanel';
import { agentChatApi } from '@/services/agent';
import { parseModelMapping, type ModelMappingSuggestion } from '@/services/agent/modelMapping';
import type { ModelMappingTarget } from '@/services/agent/governance';

interface Props { target: ModelMappingTarget; definition: string; disabled: boolean; onApply: (column: string) => void }
export default function ModelMappingPanel({ target, definition, disabled, onApply }: Props) {
  return <StructuredSuggestionPanel<ModelMappingTarget, ModelMappingSuggestion['candidates'][number], ModelMappingSuggestion> target={target} definition={definition} disabled={disabled}
    title="AI 来源字段建议" notice="依据已保存目标字段和所选源表；请核对类型差异。带入会清空旧转换表达式，检查后人工保存。"
    summary={<p>{target.columnName} ← {target.database}.{target.table} · {target.businessDescription || '可补充业务含义以减少歧义'}</p>}
    question="为绑定目标字段推荐所选源表中的来源字段；解释业务依据和类型差异，缺依据时列出待确认项。"
    generateLabel="生成来源候选" adoptLabel="带入来源字段"
    bindTarget={modelMapping => ({ purpose: 'MODEL_MAPPING', modelMapping })} selectTarget={v => v?.modelMapping}
    parse={parseModelMapping} validate={agentChatApi.validateModelMapping}
    candidateKey={c => c.sourceColumn} renderCandidate={c => <><p>{c.sourceColumn} · {c.type || '类型未知'} · {c.nullable ? '可空' : '非空'}</p><p>{c.reason}</p></>}
    onApply={c => onApply(c.sourceColumn)} />;
}

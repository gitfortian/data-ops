import StructuredSuggestionPanel from './StructuredSuggestionPanel';
import { agentChatApi } from '@/services/agent';
import { parseStandardMatch, type StandardMatchSuggestion } from '@/services/agent/standardMatch';
import type { StandardMatchTarget } from '@/services/agent/governance';

interface Props { target: StandardMatchTarget; definition: string; disabled: boolean; onApply: (id: number) => void }
export default function StandardMatchPanel({ target, definition, disabled, onApply }: Props) {
  return <StructuredSuggestionPanel<StandardMatchTarget, StandardMatchSuggestion['candidates'][number], StandardMatchSuggestion> target={target} definition={definition} disabled={disabled}
    title="AI 类型标准匹配" notice="依据当前未保存字段草稿；带入后仍需在原编辑器人工保存。首期仅匹配类型标准。"
    summary={<p>{target.columnName} · {target.dataType} · {target.businessDescription || '尚未填写业务说明，可在字段中补充。'}</p>}
    question="为绑定的未保存字段匹配类型标准；缺少业务依据时列出待确认项。"
    generateLabel="生成类型候选" adoptLabel="带入类型引用"
    bindTarget={standardMatch => ({ purpose: 'STANDARD_MATCH', standardMatch })} selectTarget={v => v?.standardMatch}
    parse={parseStandardMatch} validate={agentChatApi.validateStandardMatch}
    candidateKey={c => c.standardId} renderCandidate={c => <><p>{c.name}（{c.code}） · v{c.version} · {c.stdType}</p><p>{c.reason}</p></>}
    onApply={c => onApply(c.standardId)} />;
}

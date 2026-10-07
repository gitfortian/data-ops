import type { SaveRulePayload } from '@/services/data-quality';
import { relatedFormRules, sameRuleConditions } from '@/services/data-quality/ruleComparison';

const conditionText = (rule: SaveRulePayload) => [
  `${rule.operator || 'EQ'} ${rule.threshold ?? '未提供'}`,
  rule.thresholdEnd != null ? `～${rule.thresholdEnd}` : '',
  rule.enumValues?.length ? `枚举：${rule.enumValues.join('、')}` : '',
].filter(Boolean).join(' ');

export default function QualityRuleComparison({ candidate, rules }: {
  candidate: SaveRulePayload; rules: SaveRulePayload[];
}) {
  const related = relatedFormRules(candidate, rules);
  return <div className="my-3 text-sm">
    <p>对照来源：当前表单，包含未保存修改；仅比较同模板、同字段。</p>
    <table className="w-full text-left" aria-label={`规则对照：${candidate.name}`}>
      <thead><tr><th>规则</th><th>条件</th><th>启用状态</th></tr></thead>
      <tbody>
        <tr><td>AI 候选：{candidate.name}</td><td>{conditionText(candidate)}</td><td>带入后停用</td></tr>
        {related.slice(0, 5).map((rule, index) => <tr key={index}>
          <td>{rule.name}{sameRuleConditions(candidate, rule) ? '（相同条件）' : '（不同条件）'}</td>
          <td>{conditionText(rule)}</td><td>{rule.enabled ? '启用' : '停用'}</td>
        </tr>)}
      </tbody>
    </table>
    {!related.length && <p>当前表单没有同模板、同字段的规则，此候选将追加为停用规则。</p>}
    {related.length > 5 && <p>另有 {related.length - 5} 条未在此显示，请在上方规则编辑器核对；重复检查包含全部条目。</p>}
  </div>;
}

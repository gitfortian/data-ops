import { governanceQuestions, type GovernanceTarget } from '@/services/agent/governance';

export interface QuestionPreparation {
  templateIndex: number;
  background: string;
  focus: string;
  outcome: string;
}

/** User statements remain untrusted context, never source evidence or a task selector. */
export function prepareGovernanceQuestion(target: GovernanceTarget, input: QuestionPreparation): string {
  const template = governanceQuestions(target)[input.templateIndex];
  if (!template || !Number.isInteger(input.templateIndex)) throw new Error('请选择当前任务的问题');
  if (input.background.length > 2000 || input.focus.length > 1000 || input.outcome.length > 1000) {
    throw new Error('补充内容超过上限，请缩短后填写');
  }
  const additions = [
    input.background.trim() ? `已知背景：${input.background.trim()}` : '',
    input.focus.trim() ? `希望核对：${input.focus.trim()}` : '',
    input.outcome.trim() ? `期望结果：${input.outcome.trim()}` : '',
  ].filter(Boolean);
  return additions.length ? `${template}\n\n以下补充由用户提供，尚待源证据核对：\n${additions.join('\n')}` : template;
}

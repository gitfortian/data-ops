import catalog from './skillTemplates.generated.json';
import type { AgentSkillSaveInput } from './types';

export interface SkillTemplate extends AgentSkillSaveInput {
  entry: string;
  category: string;
}

/** Generated display material; these entries are not registered or active runtime Skills. */
export const skillTemplates: readonly SkillTemplate[] = catalog;

export function skillTemplateInput(template: SkillTemplate): AgentSkillSaveInput {
  return {
    skillId: template.skillId,
    name: template.name,
    description: template.description,
    metadata: template.metadata,
    content: template.content,
  };
}

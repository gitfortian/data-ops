import type { ScreenTemplate } from './model';
import {
  commandCenterTemplate,
  dataCenterTemplate,
  operationCenterTemplate,
  simpleDashboardTemplate,
} from './templates';
import { assertValidScreenTemplate } from './validator';

const templates = [
  commandCenterTemplate,
  operationCenterTemplate,
  dataCenterTemplate,
  simpleDashboardTemplate,
].map(assertValidScreenTemplate);
const templateMap = new Map<string, ScreenTemplate>();

for (const template of templates) {
  if (templateMap.has(template.id)) throw new Error(`Duplicate screen template id "${template.id}"`);
  templateMap.set(template.id, template);
}

export const builtinScreenTemplates: readonly ScreenTemplate[] = Object.freeze(templates);

export const getScreenTemplateById = (id: string) => templateMap.get(id);

export const listScreenTemplates = (category?: string) => {
  if (!category) return [...builtinScreenTemplates];
  return builtinScreenTemplates.filter((template) => template.category === category);
};

export const listScreenTemplateCategories = () => [...new Set(builtinScreenTemplates.map((template) => template.category))];

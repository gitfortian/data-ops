import type { ScreenComponent, ScreenTemplate } from './model';

export interface ScreenTemplateValidationResult {
  valid: boolean;
  errors: string[];
}

const positive = (value: number) => Number.isFinite(value) && value > 0;
const nonNegative = (value: number) => Number.isFinite(value) && value >= 0;

const validateComponent = (template: ScreenTemplate, component: ScreenComponent) => {
  const errors: string[] = [];
  const prefix = `component "${component.id || '<empty>'}"`;

  if (!component.id.trim()) errors.push(`${prefix}: id is required`);
  if (!nonNegative(component.x) || !nonNegative(component.y)) {
    errors.push(`${prefix}: x and y must be finite values greater than or equal to 0`);
  }
  if (!positive(component.width) || !positive(component.height)) {
    errors.push(`${prefix}: width and height must be finite values greater than 0`);
  }
  if (component.x + component.width > template.width || component.y + component.height > template.height) {
    errors.push(`${prefix}: bounds exceed the ${template.width}x${template.height} canvas`);
  }

  if ((component.type === 'line' || component.type === 'bar') && component.data) {
    const categoryCount = component.data.categories.length;
    for (const series of component.data.series) {
      if (series.values.length !== categoryCount) {
        errors.push(`${prefix}: series "${series.name}" has ${series.values.length} values for ${categoryCount} categories`);
      }
    }
  }

  if (component.type === 'table' && component.data) {
    const columnKeys = new Set<string>();
    for (const column of component.data.columns) {
      if (!column.key.trim()) errors.push(`${prefix}: table column key is required`);
      if (columnKeys.has(column.key)) errors.push(`${prefix}: duplicate table column key "${column.key}"`);
      columnKeys.add(column.key);
    }
  }

  return errors;
};

export const validateScreenTemplate = (template: ScreenTemplate): ScreenTemplateValidationResult => {
  const errors: string[] = [];

  if (template.version !== 1) errors.push('template version must be 1');
  if (!template.id.trim()) errors.push('template id is required');
  if (!template.name.trim()) errors.push('template name is required');
  if (!template.category.trim()) errors.push('template category is required');
  if (!positive(template.width) || !positive(template.height)) {
    errors.push('template width and height must be finite values greater than 0');
  }

  const ids = new Set<string>();
  for (const component of template.components) {
    if (ids.has(component.id)) errors.push(`duplicate component id "${component.id}"`);
    ids.add(component.id);
    errors.push(...validateComponent(template, component));
  }

  return { valid: errors.length === 0, errors };
};

export const assertValidScreenTemplate = <T extends ScreenTemplate>(template: T): T => {
  const result = validateScreenTemplate(template);
  if (!result.valid) {
    throw new Error(`Invalid screen template "${template.id}":\n${result.errors.map((item) => `- ${item}`).join('\n')}`);
  }
  return template;
};

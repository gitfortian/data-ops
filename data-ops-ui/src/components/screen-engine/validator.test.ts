import { dataCenterTemplate, operationCenterTemplate, simpleDashboardTemplate } from './templates';
import { validateScreenTemplate } from './validator';

describe('screen template validator', () => {
  it.each([operationCenterTemplate, dataCenterTemplate, simpleDashboardTemplate])(
    'accepts builtin template $id',
    (template) => {
      expect(validateScreenTemplate(template)).toEqual({ valid: true, errors: [] });
    },
  );

  it('rejects duplicate component ids and out-of-bounds components', () => {
    const template = {
      ...simpleDashboardTemplate,
      id: 'invalid-template',
      components: [
        simpleDashboardTemplate.components[0],
        {
          ...simpleDashboardTemplate.components[0],
          x: simpleDashboardTemplate.width - 10,
          width: 100,
        },
      ],
    };

    const result = validateScreenTemplate(template);
    expect(result.valid).toBe(false);
    expect(result.errors.some((error) => error.includes('duplicate component id'))).toBe(true);
    expect(result.errors.some((error) => error.includes('bounds exceed'))).toBe(true);
  });
});

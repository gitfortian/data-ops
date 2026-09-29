import type { DynamicFormField } from '../../../types';
import {
  getConfigInitialValues,
  getFieldDependencies,
  isDynamicFieldVisible,
  normalizeConfigValuesForForm,
  normalizeFormSections,
  toKeyValueRows,
  transformRules,
} from './formUtils';

const field = (
  overrides: Partial<DynamicFormField> = {},
): DynamicFormField => ({
  key: 'target',
  label: '目标字段',
  type: 'INPUT',
  ...overrides,
});

describe('dynamic data source form utils', () => {
  it('marks number field rules as numeric validation rules', () => {
    expect(
      transformRules(
        [
          {
            required: true,
            min: 1,
            max: 65535,
            message: '端口必须在 1 到 65535 之间',
          },
        ],
        'NUMBER',
      ),
    ).toEqual([
      {
        type: 'number',
        required: true,
        min: 1,
        max: 65535,
        message: '端口必须在 1 到 65535 之间',
      },
    ]);
  });

  it('keeps text field length rules as non-numeric rules', () => {
    expect(
      transformRules([{ max: 10, message: '最多输入 10 个字符' }], 'INPUT'),
    ).toEqual([{ max: 10, message: '最多输入 10 个字符' }]);
  });

  it('parses number defaults into numbers', () => {
    expect(
      getConfigInitialValues([
        {
          key: 'port',
          label: '端口',
          type: 'NUMBER',
          defaultValue: '3306',
        },
      ]),
    ).toEqual({ port: 3306 });
  });

  it('converts object properties into key value rows for editing', () => {
    expect(toKeyValueRows({ useSSL: false, serverTimezone: 'UTC' })).toEqual([
      { key: 'useSSL', value: 'false' },
      { key: 'serverTimezone', value: 'UTC' },
    ]);

    expect(
      normalizeConfigValuesForForm(
        [field({ key: 'properties', type: 'CUSTOM_SELECT' })],
        { properties: { useSSL: 'false' } },
      ),
    ).toEqual({ properties: [{ key: 'useSSL', value: 'false' }] });
  });

  it('maps visibleWhen conditions to dependsOn and evaluates equals', () => {
    const target = field({
      dependsOn: ['mode'],
      visibleWhen: [{ operator: 'EQUALS', value: 'ADVANCED' }],
    });

    expect(getFieldDependencies(target)).toEqual(['mode']);
    expect(isDynamicFieldVisible(target, { mode: 'ADVANCED' })).toBe(true);
    expect(isDynamicFieldVisible(target, { mode: 'BASIC' })).toBe(false);
  });

  it('merges explicit and inferred dependencies without duplicates', () => {
    const target = field({
      dependsOn: ['enabled'],
      visibleWhen: [
        { field: 'enabled', operator: 'TRUTHY' },
        { field: 'mode', operator: 'IN', values: ['A', 'B'] },
      ],
    });

    expect(getFieldDependencies(target)).toEqual(['enabled', 'mode']);
  });

  it('uses AND semantics for multiple visibility conditions', () => {
    const target = field({
      visibleWhen: [
        { field: 'enabled', operator: 'TRUTHY' },
        { field: 'mode', operator: 'IN', values: ['A', 'B'] },
      ],
    });

    expect(isDynamicFieldVisible(target, { enabled: true, mode: 'A' })).toBe(true);
    expect(isDynamicFieldVisible(target, { enabled: false, mode: 'A' })).toBe(false);
    expect(isDynamicFieldVisible(target, { enabled: true, mode: 'C' })).toBe(false);
  });

  it('supports not-equals, not-in, falsy and nested dependency paths', () => {
    expect(
      isDynamicFieldVisible(
        field({
          visibleWhen: {
            field: 'auth.type',
            operator: 'NOT_EQUALS',
            value: 'PASSWORD',
          },
        }),
        { auth: { type: 'PRIVATE_KEY' } },
      ),
    ).toBe(true);

    expect(
      isDynamicFieldVisible(
        field({
          visibleWhen: {
            field: 'mode',
            operator: 'NOT_IN',
            values: ['A', 'B'],
          },
        }),
        { mode: 'C' },
      ),
    ).toBe(true);

    expect(
      isDynamicFieldVisible(
        field({
          visibleWhen: { field: 'disabled', operator: 'FALSY' },
        }),
        { disabled: false },
      ),
    ).toBe(true);
  });

  it('keeps legacy schemas compatible while normalizing dependency metadata', () => {
    const sections = normalizeFormSections({
      formFields: [
        field({
          key: 'legacy',
          dependsOn: ['enabled'],
          visibleWhen: [{ operator: 'TRUTHY' }],
        }),
      ],
    });

    expect(sections).toHaveLength(1);
    expect(sections[0].fields[0].dependsOn).toEqual(['enabled']);
    expect(sections[0].fields[0].visibleWhen).toEqual([
      { field: 'enabled', operator: 'TRUTHY' },
    ]);
  });

  it('orders standard capability sections and upgrades legacy properties textarea', () => {
    const sections = normalizeFormSections({
      sections: [
        {
          key: 'connection',
          title: '连接参数',
          fields: [field({ key: 'host' })],
        },
        {
          key: 'ssh',
          title: 'SSH 隧道',
          fields: [field({ key: 'sshTunnel', type: 'SSH' })],
        },
        {
          key: 'driver',
          title: '驱动配置',
          fields: [field({ key: 'driverClassName' })],
        },
        {
          key: 'advanced',
          title: '高级配置',
          fields: [field({ key: 'properties', type: 'TEXTAREA' })],
        },
      ],
    });

    expect(sections.map((section) => section.key)).toEqual([
      'connection',
      'driver',
      'ssh',
      'advanced',
    ]);
    expect(sections[3].fields[0].type).toBe('CUSTOM_SELECT');
    expect(sections[3].fields[0].placeholder).toContain('useSSL = false');
  });
});

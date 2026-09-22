import {
  directCrossFiltersForWidget,
  pruneRuntimeSelections,
  sameDashboardSelection,
} from './interaction-runtime';
import type { AnalysisSelection, DashboardWidget } from './model';

const selection = (fieldId: string, value: string): AnalysisSelection => ({
  fieldId,
  value,
  label: `${fieldId}: ${value}`,
  rowIndex: 0,
});

const widgets = [
  {
    id: 'source',
    inlineAnalysis: {
      dashboardBehavior: {
        crossFilters: [
          {
            id: 'link-1',
            sourceField: 'province',
            targetWidgetId: 'target',
            targetField: 'region',
          },
        ],
      },
    },
  },
  { id: 'target' },
] as DashboardWidget[];

describe('dashboard direct cross-filter runtime', () => {
  it('maps an active source selection into the target field', () => {
    expect(directCrossFiltersForWidget(
      widgets,
      { source: selection('province', '广东') },
      'target',
    )).toEqual([
      {
        id: 'dashboard-cross-source-link-1',
        field: 'region',
        operator: 'eq',
        value: '广东',
      },
    ]);
  });

  it('ignores rules when the emitted source field does not match', () => {
    expect(directCrossFiltersForWidget(
      widgets,
      { source: selection('city', '深圳') },
      'target',
    )).toEqual([]);
  });

  it('compares semantic selections for click-to-clear behavior', () => {
    expect(sameDashboardSelection(
      selection('province', '广东'),
      selection('province', '广东'),
    )).toBe(true);
    expect(sameDashboardSelection(
      selection('province', '广东'),
      selection('province', '浙江'),
    )).toBe(false);
  });

  it('removes runtime selections for widgets that no longer exist', () => {
    expect(pruneRuntimeSelections(
      widgets,
      {
        source: selection('province', '广东'),
        removed: selection('province', '浙江'),
      },
    )).toEqual({ source: selection('province', '广东') });
  });
});

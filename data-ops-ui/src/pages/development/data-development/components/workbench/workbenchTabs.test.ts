import { closeTabs, tabActionTargets } from './workbenchTabs';

describe('workbench tab close rules', () => {
  const open = ['1', '2', '3', '4'];

  it('resolves action targets relative to the active tab', () => {
    expect(tabActionTargets('close-current', open, '3')).toEqual(['3']);
    expect(tabActionTargets('close-others', open, '3')).toEqual(['1', '2', '4']);
    expect(tabActionTargets('close-left', open, '3')).toEqual(['1', '2']);
    expect(tabActionTargets('close-right', open, '3')).toEqual(['4']);
    expect(tabActionTargets('close-all', open, '3')).toEqual(open);
  });

  it('keeps focus when closing unrelated tabs', () => {
    expect(closeTabs(open, '3', ['1', '4'])).toEqual({
      nextOpenNodeIds: ['2', '3'],
      nextActiveNodeId: '3',
    });
  });

  it('focuses the nearest surviving tab after closing the active tab', () => {
    expect(closeTabs(open, '3', ['3'])).toEqual({
      nextOpenNodeIds: ['1', '2', '4'],
      nextActiveNodeId: '4',
    });
    expect(closeTabs(open, '4', ['4'])).toEqual({
      nextOpenNodeIds: ['1', '2', '3'],
      nextActiveNodeId: '3',
    });
  });

  it('clears focus when all tabs close', () => {
    expect(closeTabs(open, '2', open)).toEqual({
      nextOpenNodeIds: [],
      nextActiveNodeId: undefined,
    });
  });
});

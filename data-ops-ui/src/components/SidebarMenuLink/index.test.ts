import { shouldNavigateInApp } from './index';

describe('SidebarMenuLink', () => {
  const click = {
    button: 0,
    ctrlKey: false,
    metaKey: false,
    shiftKey: false,
    altKey: false,
  };

  it('uses in-app navigation for an ordinary left click', () => {
    expect(shouldNavigateInApp(click)).toBe(true);
  });

  it.each([
    { ...click, button: 1 },
    { ...click, ctrlKey: true },
    { ...click, metaKey: true },
    { ...click, shiftKey: true },
    { ...click, altKey: true },
  ])('leaves new-page gestures to the browser (%o)', (event) => {
    expect(shouldNavigateInApp(event)).toBe(false);
  });
});

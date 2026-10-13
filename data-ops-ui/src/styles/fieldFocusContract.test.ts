import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Global focus contracts: Ant Design input components own their rounded field
 * focus feedback. Do not reintroduce a generic dark outline on nested inputs.
 * Text-entry fields match :focus-visible even after a mouse click.
 */
describe('commercial workspace focus styles', () => {
  const stylesheet = readFileSync(resolve(__dirname, '../global.less'), 'utf8');
  const workspaceStyles = stylesheet.match(/\[data-yak-workspace='commercial'\] \{([\s\S]*?)\n\}/)?.[1];

  it('keeps a visible focus outline on keyboard-operable buttons and links', () => {
    expect(workspaceStyles).toContain(":is(button, a, [role='button']):focus-visible");
    expect(workspaceStyles).toMatch(/outline:\s*2px solid #475467/);
  });

  it('does not draw a generic square outline on input or textarea elements', () => {
    const genericFocusSelectors = [...(workspaceStyles ?? '').matchAll(/:is\(([^)]*)\):focus-visible/g)];
    expect(genericFocusSelectors.length).toBeGreaterThan(0);
    for (const selector of genericFocusSelectors) {
      expect(selector[1]).not.toMatch(/\binput\b|\btextarea\b/);
    }
  });

  it('does not globally suppress native or Ant Design text focus visibility', () => {
    expect(workspaceStyles).not.toMatch(/input[^{}]*:focus[^{}]*\{[^{}]*outline:\s*(?:0|none)/);
  });
});

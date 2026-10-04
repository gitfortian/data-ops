# Header dropdown click regression — 2026-10-04

## Existing contract and scope

Signed-in users can open the workspace and user menus, select a workspace,
open workspace management, and enter user settings or log out. Navigation and
interaction presentation belong to the UI; Yak Security continues to own
projects, membership, permissions, and authentication. This repair reuses the
existing F-008 navigation and Project Space contracts.

## Reproduction and cause

The real `SiteLayout`, `SecurityProjectProvider`, `SecurityProjectSwitcher`,
Ant Design components, compiled `global.less`, and Tailwind styles were mounted
in an isolated local browser fixture. Identity, unread-message requests, and
navigation destinations were fixtures; no account credentials or backend state
were changed. The browser applied `prefers-reduced-motion: reduce`.

With the original stylesheet, clicking a trigger created a dropdown in the DOM
but positioned it outside the viewport. The user-settings menu item was at
`y = -7125px`; workspace management was at `y = -7098.875px` in a 720px-high
viewport. Clicking the settings item did not reach the navigation destination.

The reduced-motion rule assigned `transition-duration: 0.01ms` to every
descendant of the commercial workspace, including dropdown portals under
`body[data-yak-workspace='commercial']`. Elements with no declared transition
property therefore acquired the default `transition-property: all`.
During popup alignment, resetting inline coordinates to `0px` still yielded
computed coordinates of `-1000vw` / `-1000vh` until the transition finished.
The positioning calculation then used the old rectangle and moved the menu
outside the viewport.

The project switcher outside the commercial layout worked. Returning only the
original transition declaration to the complete fixture reproduced the failure
again, confirming the stylesheet as the cause.

## Repair and verification

The reduced-motion rule now uses `transition: none !important`. Coordinate
changes are synchronous and reduced-motion users get no transition animation.
The existing short animation duration remains unchanged.

Browser verification with the complete layout and repaired stylesheet:

- User settings item: `y = 119px`, center hit test reached “设置”; navigation
  destination `/settings` was observed.
- Workspace management item: `y = 147.125px`, center hit test reached
  “管理工作空间”; navigation destination `/system/projects` was observed.
- Language menu opened and exposed its existing English option.
- Both popup and menu item were inside the viewport; computed transition was
  `none`.

Focused component checks use the real layout, provider, and Ant Design dropdown
with mocked Umi navigation and account boundaries:

```text
npm test -- --runInBand src/layouts/SiteLayout/HeaderDropdown.test.tsx
4 tests passed: workspace/user menu opening and both navigation destinations.

npm run check:types
TypeScript debt gate passed (139 existing diagnostics).

npm test -- --runInBand
120 suites / 539 tests passed.

npm run build
Production build and frontend artifact manifest passed.
```

The component checks protect trigger and menu-action wiring. jsdom does not
implement CSS transitions or popup geometry and cannot reproduce this positioning
failure; the root-cause regression evidence is the browser comparison above.
This is frontend interaction evidence, not backend authorization or a real-user
project-switch acceptance claim.

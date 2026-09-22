import type { MouseEvent, ReactNode } from 'react';
import { history } from '@umijs/max';

interface SidebarMenuLinkProps {
  children: ReactNode;
  path?: string;
}

export const shouldNavigateInApp = (
  event: Pick<MouseEvent<HTMLAnchorElement>, 'button' | 'ctrlKey' | 'metaKey' | 'shiftKey' | 'altKey'>,
) => event.button === 0
  && !event.ctrlKey
  && !event.metaKey
  && !event.shiftKey
  && !event.altKey;

/**
 * Give sidebar entries a real URL so the browser's context menu can open them
 * in a new tab/window, while keeping ordinary clicks as SPA navigation.
 */
const SidebarMenuLink = ({ children, path }: SidebarMenuLinkProps) => {
  if (!path) return children;

  return (
    <a
      href={path}
      onClick={(event) => {
        if (!shouldNavigateInApp(event)) return;
        event.preventDefault();
        history.push(path);
      }}
    >
      {children}
    </a>
  );
};

export default SidebarMenuLink;

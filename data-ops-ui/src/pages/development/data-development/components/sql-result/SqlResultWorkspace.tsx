import { LoaderCircle, X } from 'lucide-react';
import { useLayoutEffect, useRef, useState } from 'react';

import type { DevelopmentTaskRunResult } from '../../types';
import SqlResultWorkspaceContent from './SqlResultWorkspaceContent';

interface SqlResultWorkspaceProps {
  result?: DevelopmentTaskRunResult;
}

const statusText = (result?: DevelopmentTaskRunResult) => {
  if (!result) return undefined;
  if (result.status === 'RUNNING') return '运行中';
  if (result.status === 'SUCCESS') return `完成 · ${result.durationMs} ms`;
  if (result.status === 'CANCELLED') return '已取消';
  if (result.status === 'TIMEOUT') return `超时 · ${result.durationMs} ms`;
  if (result.status === 'FAILED') return `失败 · ${result.durationMs} ms`;
  return result.status;
};

/**
 * Data Service / Dataset render SqlResultWorkspace directly inside their resizable
 * bottom container, while SQL tasks already have RunResultPanel around it.
 * Detect the standalone container so all three SQL editors share the same
 * "run -> open -> resize -> close -> run again -> reopen" result-panel UX.
 */
const findStandalonePanel = (root: HTMLDivElement | null) => {
  const contentHost = root?.parentElement;
  const panel = contentHost?.parentElement;
  if (!panel) return undefined;

  const hasDirectHorizontalSeparator = Array.from(panel.children).some((child) =>
    child instanceof HTMLElement
    && child.getAttribute('role') === 'separator'
    && child.getAttribute('aria-orientation') === 'horizontal',
  );
  return hasDirectHorizontalSeparator ? panel : undefined;
};

const findNodeName = (panel?: HTMLElement) => {
  const statusBar = panel?.previousElementSibling;
  if (!(statusBar instanceof HTMLElement)) return undefined;
  const value = statusBar.querySelectorAll('span').item(1)?.textContent?.trim();
  return value || undefined;
};

const SqlResultWorkspace = ({ result }: SqlResultWorkspaceProps) => {
  const rootRef = useRef<HTMLDivElement>(null);
  const [standalonePanel, setStandalonePanel] = useState<HTMLElement>();
  const [nodeName, setNodeName] = useState<string>();
  const [closed, setClosed] = useState(!result);

  useLayoutEffect(() => {
    const panel = findStandalonePanel(rootRef.current);
    setStandalonePanel(panel);
    setNodeName(findNodeName(panel));
  }, []);

  useLayoutEffect(() => {
    if (!standalonePanel) return;
    if (!result) {
      setClosed(true);
      return;
    }
    if (result.status === 'RUNNING') setClosed(false);
  }, [result?.status, standalonePanel]);

  useLayoutEffect(() => {
    if (!standalonePanel) return;
    if (closed) standalonePanel.style.display = 'none';
    else standalonePanel.style.removeProperty('display');

    return () => {
      standalonePanel.style.removeProperty('display');
    };
  }, [closed, standalonePanel]);

  const standalone = Boolean(standalonePanel);

  return (
    <div ref={rootRef} className="h-full min-h-0 overflow-hidden bg-white">
      {standalone ? (
        <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
          <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
            <div className="flex min-w-0 items-center gap-3">
              <span className="shrink-0 text-[12px] font-medium text-[#344054]">
                运行结果
              </span>
              {nodeName ? (
                <span className="truncate text-[11px] text-[#98a2b3]">
                  当前节点：{nodeName}
                </span>
              ) : null}
              {result?.status === 'RUNNING' ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[11px] text-[#667085]">
                  <LoaderCircle size={12} className="animate-spin" />
                  运行中
                </span>
              ) : statusText(result) ? (
                <span className="shrink-0 text-[11px] text-[#667085]">
                  {statusText(result)}
                </span>
              ) : null}
            </div>
            <button
              type="button"
              title="关闭"
              aria-label="关闭运行结果面板"
              onClick={() => setClosed(true)}
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
            >
              <X size={14} strokeWidth={1.8} />
            </button>
          </div>

          <div className="min-h-0 flex-1 overflow-hidden bg-white">
            <SqlResultWorkspaceContent result={result} />
          </div>
        </div>
      ) : (
        <SqlResultWorkspaceContent result={result} />
      )}
    </div>
  );
};

export default SqlResultWorkspace;

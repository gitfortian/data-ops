import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { NodeResizer, type NodeProps } from 'reactflow';
import WorkflowNoteToolbar from './WorkflowNoteToolbar';
import type { WorkflowNoteData, WorkflowNoteTheme } from './types';

const NOTE_RICH_TEXT_PREFIX = '__YAK_NOTE_RICH__:';

const NOTE_THEME_META: Record<WorkflowNoteTheme, {
  background: string;
  accent: string;
  border: string;
}> = {
  blue: { background: '#eef7ff', accent: '#8ec5ff', border: '#5aa7f8' },
  cyan: { background: '#ecfbfb', accent: '#20b8b0', border: '#0f9d96' },
  green: { background: '#effaf2', accent: '#39a66a', border: '#248c55' },
  yellow: { background: '#fff9e8', accent: '#e3a400', border: '#c78e00' },
  pink: { background: '#fff1f5', accent: '#e85f88', border: '#d94773' },
  violet: { background: '#f5f0ff', accent: '#8b5cf6', border: '#7245da' },
};

const escapeHtml = (value: string) => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#039;')
  .replace(/\n/g, '<br>');

const normalizeLink = (value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  if (/^(https?:|mailto:)/i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
};

const sanitizeNoteHtml = (raw: string) => {
  if (typeof window === 'undefined' || typeof DOMParser === 'undefined') return raw;

  const parser = new DOMParser();
  const documentNode = parser.parseFromString(`<div>${raw}</div>`, 'text/html');
  const root = documentNode.body.firstElementChild;
  if (!root) return '';

  const allowedTags = new Set([
    'B', 'STRONG', 'I', 'EM', 'S', 'STRIKE', 'A', 'UL', 'LI', 'DIV', 'P', 'BR', 'FONT', 'SPAN',
  ]);

  const sanitizeElement = (element: Element) => {
    [...element.children].forEach(sanitizeElement);

    if (!allowedTags.has(element.tagName)) {
      const parent = element.parentNode;
      if (!parent) return;
      while (element.firstChild) parent.insertBefore(element.firstChild, element);
      parent.removeChild(element);
      return;
    }

    const href = element.tagName === 'A' ? normalizeLink(element.getAttribute('href') || '') : undefined;
    const fontSize = element.tagName === 'FONT' ? element.getAttribute('size') || '' : '';
    [...element.attributes].forEach((attribute) => element.removeAttribute(attribute.name));

    if (element.tagName === 'A' && href) {
      element.setAttribute('href', href);
      element.setAttribute('target', '_blank');
      element.setAttribute('rel', 'noopener noreferrer');
    }
    if (element.tagName === 'FONT' && ['1', '3', '5'].includes(fontSize)) {
      element.setAttribute('size', fontSize);
    }
  };

  [...root.children].forEach(sanitizeElement);
  return root.innerHTML;
};

const decodeNoteHtml = (value: string) => value.startsWith(NOTE_RICH_TEXT_PREFIX)
  ? value.slice(NOTE_RICH_TEXT_PREFIX.length)
  : escapeHtml(value);

const normalizeFontSize = (value: string): '1' | '3' | '5' => {
  if (value === '5' || value === '16px' || value === '6') return '5';
  if (value === '3' || value === '14px' || value === '4') return '3';
  return '1';
};

const WorkflowNoteNode = ({ id, data, selected }: NodeProps<WorkflowNoteData>) => {
  const theme = NOTE_THEME_META[data.theme] || NOTE_THEME_META.blue;
  const editable = selected && !data.locked;
  const editorRef = useRef<HTMLDivElement>(null);
  const savedRangeRef = useRef<Range | null>(null);
  const [currentFontSize, setCurrentFontSize] = useState<'1' | '3' | '5'>('1');
  const resolvedHtml = decodeNoteHtml(data.text || '');

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor || document.activeElement === editor) return;
    const sanitized = sanitizeNoteHtml(resolvedHtml);
    if (editor.innerHTML !== sanitized) editor.innerHTML = sanitized;
  }, [resolvedHtml]);

  const captureSelection = useCallback(() => {
    const editor = editorRef.current;
    const selection = window.getSelection();
    if (!editor || !selection?.rangeCount) return;
    const range = selection.getRangeAt(0);
    if (!editor.contains(range.commonAncestorContainer)) return;
    savedRangeRef.current = range.cloneRange();
    setCurrentFontSize(normalizeFontSize(String(document.queryCommandValue('fontSize') || '1')));
  }, []);

  const restoreSelection = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    editor.focus();
    const range = savedRangeRef.current;
    if (!range) return;
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
  }, []);

  const syncEditorValue = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const html = sanitizeNoteHtml(editor.innerHTML);
    if (editor.innerHTML !== html) editor.innerHTML = html;
    data.onChange?.(id, {
      text: `${NOTE_RICH_TEXT_PREFIX}${html}`,
    });
  }, [data, id]);

  const runCommand = useCallback((command: 'bold' | 'italic' | 'strikeThrough' | 'createLink' | 'insertUnorderedList') => {
    if (!editable) return;
    restoreSelection();

    if (command === 'createLink') {
      const rawUrl = window.prompt('请输入链接地址');
      const url = rawUrl ? normalizeLink(rawUrl) : undefined;
      if (!url) return;
      document.execCommand('createLink', false, url);
    } else {
      document.execCommand(command, false);
    }

    captureSelection();
    syncEditorValue();
    data.onCommit?.(id, '注释格式已修改');
  }, [captureSelection, data, editable, id, restoreSelection, syncEditorValue]);

  const changeFontSize = useCallback((size: '1' | '3' | '5') => {
    if (!editable) return;
    restoreSelection();
    document.execCommand('fontSize', false, size);
    setCurrentFontSize(size);
    captureSelection();
    syncEditorValue();
    data.onCommit?.(id, '注释字号已修改');
  }, [captureSelection, data, editable, id, restoreSelection, syncEditorValue]);

  const visibleText = editorRef.current?.innerText
    || (data.text.startsWith(NOTE_RICH_TEXT_PREFIX) ? '' : data.text);

  return (
    <div
      className="relative flex h-full w-full flex-col overflow-visible rounded-md border shadow-[0_1px_2px_rgba(22,24,35,.06)] transition-shadow hover:shadow-[0_4px_12px_rgba(22,24,35,.08)]"
      style={{
        backgroundColor: theme.background,
        borderColor: selected ? theme.border : 'rgba(22,24,35,.06)',
      }}
    >
      <NodeResizer
        isVisible={Boolean(selected && !data.locked)}
        minWidth={240}
        minHeight={88}
        color={theme.border}
        lineStyle={{ borderWidth: 1 }}
        handleStyle={{
          width: 8,
          height: 8,
          borderRadius: 2,
          backgroundColor: theme.border,
          border: '1px solid rgba(255,255,255,.95)',
        }}
        onResizeEnd={() => data.onCommit?.(id, '注释大小已调整')}
      />

      {selected && !data.locked ? (
        <div className="absolute -top-[40px] left-1/2 z-40 -translate-x-1/2">
          <WorkflowNoteToolbar
            theme={data.theme}
            currentFontSize={currentFontSize}
            onThemeChange={(nextTheme) => {
              data.onChange?.(id, { theme: nextTheme });
              data.onCommit?.(id, '注释主题已修改');
            }}
            onFontSizeChange={changeFontSize}
            onCommand={runCommand}
            onCopyText={() => {
              const text = editorRef.current?.innerText || visibleText;
              void navigator.clipboard?.writeText(text);
            }}
            onDuplicate={() => data.onDuplicate?.(id)}
            onDelete={() => data.onDelete?.(id)}
          />
        </div>
      ) : null}

      <div className="relative min-h-0 flex-1 overflow-auto px-3 py-2.5">
        {!visibleText && !resolvedHtml ? (
          <div className="pointer-events-none absolute left-3 top-2.5 text-[12px] leading-5 text-[#98a2b3]">
            输入注释...
          </div>
        ) : null}
        <div
          ref={editorRef}
          contentEditable={editable}
          suppressContentEditableWarning
          className={[
            'min-h-full break-words text-[12px] leading-5 text-[#475467] outline-none',
            editable ? 'nodrag nopan nowheel cursor-text' : 'pointer-events-none cursor-default',
            '[&_a]:text-[#155eef] [&_a]:underline [&_p]:my-0 [&_ul]:my-0 [&_ul]:list-disc [&_ul]:pl-5',
          ].join(' ')}
          onInput={syncEditorValue}
          onBlur={() => {
            syncEditorValue();
            data.onCommit?.(id, '注释内容已修改');
          }}
          onMouseUp={captureSelection}
          onKeyUp={captureSelection}
          onKeyDown={(event) => event.stopPropagation()}
        />
      </div>
    </div>
  );
};

export default memo(WorkflowNoteNode);

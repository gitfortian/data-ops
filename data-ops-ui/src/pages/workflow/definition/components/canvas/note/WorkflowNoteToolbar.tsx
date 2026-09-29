import { Dropdown, Popover, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import {
  Bold,
  Italic,
  Link2,
  List,
  MoreHorizontal,
  Strikethrough,
} from 'lucide-react';
import { memo, useMemo, useState } from 'react';
import type { WorkflowNoteTheme } from './types';

interface ThemeMeta {
  accent: string;
  border: string;
}

interface WorkflowNoteToolbarProps {
  theme: WorkflowNoteTheme;
  currentFontSize: '1' | '3' | '5';
  onThemeChange: (theme: WorkflowNoteTheme) => void;
  onFontSizeChange: (size: '1' | '3' | '5') => void;
  onCommand: (command: 'bold' | 'italic' | 'strikeThrough' | 'createLink' | 'insertUnorderedList') => void;
  onCopyText: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}

const THEME_META: Record<WorkflowNoteTheme, ThemeMeta> = {
  blue: { accent: '#8ec5ff', border: '#5aa7f8' },
  cyan: { accent: '#20b8b0', border: '#0f9d96' },
  green: { accent: '#39a66a', border: '#248c55' },
  yellow: { accent: '#e3a400', border: '#c78e00' },
  pink: { accent: '#e85f88', border: '#d94773' },
  violet: { accent: '#8b5cf6', border: '#7245da' },
};

const THEMES = Object.keys(THEME_META) as WorkflowNoteTheme[];

const toolbarButtonClass = 'flex h-8 w-8 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] transition-colors hover:bg-[#f2f4f7] hover:text-[#344054]';

const Divider = () => <div className="mx-0.5 h-4 w-px bg-[#e4e7ec]" />;

const WorkflowNoteToolbar = ({
  theme,
  currentFontSize,
  onThemeChange,
  onFontSizeChange,
  onCommand,
  onCopyText,
  onDuplicate,
  onDelete,
}: WorkflowNoteToolbarProps) => {
  const [colorOpen, setColorOpen] = useState(false);
  const [fontSizeOpen, setFontSizeOpen] = useState(false);

  const moreItems = useMemo<MenuProps['items']>(() => [
    {
      key: 'copy-text',
      label: '复制文本',
      onClick: onCopyText,
    },
    {
      key: 'duplicate',
      label: '创建副本',
      onClick: onDuplicate,
    },
    { type: 'divider' },
    {
      key: 'delete',
      danger: true,
      label: '删除',
      onClick: onDelete,
    },
  ], [onCopyText, onDelete, onDuplicate]);

  const fontSizeLabel = currentFontSize === '5' ? '大' : currentFontSize === '3' ? '中' : '小';

  return (
    <div
      className="nodrag nopan nowheel pointer-events-auto flex h-9 items-center rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_4px_12px_rgba(22,24,35,.12)]"
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => event.stopPropagation()}
    >
      <Popover
        trigger="click"
        open={colorOpen}
        onOpenChange={setColorOpen}
        placement="topLeft"
        arrow={false}
        overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
        content={(
          <div className="grid grid-cols-3 gap-0.5 rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_8px_24px_rgba(22,24,35,.14)]">
            {THEMES.map((item) => (
              <button
                key={item}
                type="button"
                aria-label={`切换为${item}主题`}
                className="group relative flex h-8 w-8 items-center justify-center rounded-md border-0 bg-transparent hover:bg-[#f5f6f7]"
                onClick={(event) => {
                  event.stopPropagation();
                  onThemeChange(item);
                  setColorOpen(false);
                }}
              >
                <span
                  className="absolute h-5 w-5 rounded-full border-[1.5px] opacity-0 transition-opacity group-hover:opacity-100"
                  style={{ borderColor: THEME_META[item].border }}
                />
                <span
                  className="h-4 w-4 rounded-full border border-black/5"
                  style={{ backgroundColor: THEME_META[item].accent }}
                />
              </button>
            ))}
          </div>
        )}
      >
        <button type="button" aria-label="注释颜色" className={toolbarButtonClass}>
          <span
            className="h-4 w-4 rounded-full border border-black/5"
            style={{ backgroundColor: THEME_META[theme].accent }}
          />
        </button>
      </Popover>

      <Divider />

      <Popover
        trigger="click"
        open={fontSizeOpen}
        onOpenChange={setFontSizeOpen}
        placement="bottomLeft"
        arrow={false}
        overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
        content={(
          <div className="w-[120px] rounded-lg border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(22,24,35,.14)]">
            {([
              ['1', '小', '12px'],
              ['3', '中', '14px'],
              ['5', '大', '16px'],
            ] as const).map(([value, label, fontSize]) => (
              <button
                key={value}
                type="button"
                className="flex h-8 w-full items-center justify-between rounded-md border-0 bg-transparent px-2.5 text-left text-[#475467] hover:bg-[#f5f6f7]"
                onClick={(event) => {
                  event.stopPropagation();
                  onFontSizeChange(value);
                  setFontSizeOpen(false);
                }}
              >
                <span style={{ fontSize }}>{label}</span>
                {currentFontSize === value ? <span className="text-[11px] text-[#fe2c55]">✓</span> : null}
              </button>
            ))}
          </div>
        )}
      >
        <button
          type="button"
          aria-label="字号"
          className="flex h-8 items-center rounded-md border-0 bg-transparent px-2 text-[12px] font-medium text-[#667085] transition-colors hover:bg-[#f2f4f7] hover:text-[#344054]"
        >
          <span className="text-[12px] font-medium">Aa / {fontSizeLabel}</span>
        </button>
      </Popover>

      <Divider />

      <Tooltip title="加粗">
        <button type="button" aria-label="加粗" className={toolbarButtonClass} onMouseDown={(event) => event.preventDefault()} onClick={() => onCommand('bold')}>
          <Bold size={15} />
        </button>
      </Tooltip>
      <Tooltip title="斜体">
        <button type="button" aria-label="斜体" className={toolbarButtonClass} onMouseDown={(event) => event.preventDefault()} onClick={() => onCommand('italic')}>
          <Italic size={15} />
        </button>
      </Tooltip>
      <Tooltip title="删除线">
        <button type="button" aria-label="删除线" className={toolbarButtonClass} onMouseDown={(event) => event.preventDefault()} onClick={() => onCommand('strikeThrough')}>
          <Strikethrough size={15} />
        </button>
      </Tooltip>
      <Tooltip title="链接">
        <button type="button" aria-label="链接" className={toolbarButtonClass} onMouseDown={(event) => event.preventDefault()} onClick={() => onCommand('createLink')}>
          <Link2 size={15} />
        </button>
      </Tooltip>
      <Tooltip title="无序列表">
        <button type="button" aria-label="无序列表" className={toolbarButtonClass} onMouseDown={(event) => event.preventDefault()} onClick={() => onCommand('insertUnorderedList')}>
          <List size={15} />
        </button>
      </Tooltip>

      <Divider />

      <Dropdown trigger={['click']} menu={{ items: moreItems }} placement="bottomRight">
        <button type="button" aria-label="更多" className={toolbarButtonClass} onMouseDown={(event) => event.stopPropagation()}>
          <MoreHorizontal size={16} />
        </button>
      </Dropdown>
    </div>
  );
};

export { THEME_META };
export default memo(WorkflowNoteToolbar);

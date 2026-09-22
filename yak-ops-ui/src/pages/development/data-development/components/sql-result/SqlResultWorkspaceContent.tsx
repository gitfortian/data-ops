import {
  Check,
  CheckCircle2,
  Columns3,
  Copy,
  Download,
  Expand,
  Info,
  LoaderCircle,
  Search,
  Shrink,
  TriangleAlert,
  X,
  XCircle,
} from 'lucide-react';
import {
  type PointerEvent as ReactPointerEvent,
  useMemo,
  useState,
} from 'react';
import { createPortal } from 'react-dom';

import type {
  DevelopmentSqlResultColumn,
  DevelopmentSqlRunOutput,
  DevelopmentTaskRunResult,
} from '../../types';

interface SqlResultWorkspaceProps {
  result?: DevelopmentTaskRunResult;
}

interface VisibleColumn {
  column: DevelopmentSqlResultColumn;
  index: number;
  key: string;
}

interface InspectedCell {
  column: DevelopmentSqlResultColumn;
  rowIndex: number;
  value: unknown;
}

const DEFAULT_COLUMN_WIDTH = 180;
const MIN_COLUMN_WIDTH = 96;
const MAX_COLUMN_WIDTH = 520;

const asSqlOutput = (value: Record<string, unknown>): DevelopmentSqlRunOutput =>
  value as DevelopmentSqlRunOutput;

const formatCell = (value: unknown) => {
  if (value === null) return 'NULL';
  if (value === undefined) return '';
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
};

const columnTitle = (column: DevelopmentSqlResultColumn, index: number) =>
  column.label || column.name || `Column ${index + 1}`;

const columnKey = (column: DevelopmentSqlResultColumn, index: number) =>
  `${column.name || column.label || 'column'}:${index}`;

const typeTone = (typeName?: string) => {
  const normalized = typeName?.toUpperCase() || '';
  if (
    /(INT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL|SERIAL|MONEY)/.test(
      normalized,
    )
  ) {
    return 'text-[#3972cf]';
  }
  if (/(DATE|TIME|YEAR)/.test(normalized)) return 'text-[#7657b3]';
  if (/(BOOL|BIT)/.test(normalized)) return 'text-[#0f8279]';
  if (/(CHAR|TEXT|STRING|JSON|XML|CLOB|UUID)/.test(normalized)) {
    return 'text-[#8a5a9f]';
  }
  if (/(BINARY|BLOB|BYTE)/.test(normalized)) return 'text-[#b36b24]';
  return 'text-[#8a94a6]';
};

const isNumericColumn = (column: DevelopmentSqlResultColumn) =>
  /(INT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL|SERIAL|MONEY)/.test(
    column.typeName?.toUpperCase() || '',
  );

const defaultColumnWidth = (column: DevelopmentSqlResultColumn) => {
  const typeName = column.typeName?.toUpperCase() || '';
  if (/(DATE|TIME)/.test(typeName)) return 196;
  if (isNumericColumn(column)) return 136;
  if (/(BOOL|BIT)/.test(typeName)) return 116;
  return DEFAULT_COLUMN_WIDTH;
};

const escapeCsv = (value: string) => {
  if (!/[",\r\n]/.test(value)) return value;
  return `"${value.replace(/"/g, '""')}"`;
};

const copyText = async (text: string) => {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  textarea.remove();
};

const ToolbarButton = ({
  title,
  active,
  children,
  onClick,
}: {
  title: string;
  active?: boolean;
  children: React.ReactNode;
  onClick: () => void;
}) => (
  <button
    type="button"
    title={title}
    aria-label={title}
    onClick={onClick}
    className={[
      'flex h-7 w-7 items-center justify-center rounded-[4px] border transition-colors',
      active
        ? 'border-[#d8e5fb] bg-[#eef5ff] text-[#3972cf]'
        : 'border-transparent text-[#667085] hover:border-[#e7eaf0] hover:bg-[#f7f9fc] hover:text-[#344054]',
    ].join(' ')}
  >
    {children}
  </button>
);

const SqlResultWorkspace = ({ result }: SqlResultWorkspaceProps) => {
  const [query, setQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [columnMenuOpen, setColumnMenuOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);
  const [hiddenColumnKeys, setHiddenColumnKeys] = useState<Set<string>>(
    () => new Set(),
  );
  const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});
  const [inspectedCell, setInspectedCell] = useState<InspectedCell>();

  const output = useMemo(
    () => (result?.output ? asSqlOutput(result.output) : undefined),
    [result],
  );
  const columns = useMemo(
    () => (Array.isArray(output?.columns) ? output.columns : []),
    [output?.columns],
  );
  const rows = useMemo(
    () => (Array.isArray(output?.rows) ? output.rows : []),
    [output?.rows],
  );

  const visibleColumns = useMemo<VisibleColumn[]>(
    () =>
      columns
        .map((column, index) => ({
          column,
          index,
          key: columnKey(column, index),
        }))
        .filter(({ key }) => !hiddenColumnKeys.has(key)),
    [columns, hiddenColumnKeys],
  );

  const filteredRows = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return rows
      .map((row, rowIndex) => ({ row, rowIndex }))
      .filter(({ row }) => {
        if (!normalizedQuery) return true;
        return visibleColumns.some(({ index }) =>
          formatCell(row?.[index]).toLocaleLowerCase().includes(normalizedQuery),
        );
      });
  }, [query, rows, visibleColumns]);

  const flashCopied = () => {
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  };

  const handleCopyResult = async () => {
    const header = visibleColumns
      .map(({ column, index }) => columnTitle(column, index))
      .join('\t');
    const body = filteredRows
      .map(({ row }) =>
        visibleColumns.map(({ index }) => formatCell(row?.[index])).join('\t'),
      )
      .join('\n');
    await copyText([header, body].filter(Boolean).join('\n'));
    flashCopied();
  };

  const handleCopyMessage = async () => {
    await copyText(result?.message || '');
    flashCopied();
  };

  const handleExportCsv = () => {
    const header = visibleColumns
      .map(({ column, index }) => escapeCsv(columnTitle(column, index)))
      .join(',');
    const body = filteredRows
      .map(({ row }) =>
        visibleColumns
          .map(({ index }) => escapeCsv(formatCell(row?.[index])))
          .join(','),
      )
      .join('\r\n');
    const csv = `\uFEFF${[header, body].filter(Boolean).join('\r\n')}`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `yak-sql-result-${Date.now()}.csv`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  };

  const toggleColumn = (key: string) => {
    setHiddenColumnKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const showAllColumns = () => setHiddenColumnKeys(new Set());

  const startColumnResize = (
    event: ReactPointerEvent<HTMLDivElement>,
    key: string,
    column: DevelopmentSqlResultColumn,
  ) => {
    event.preventDefault();
    event.stopPropagation();
    const startX = event.clientX;
    const startWidth = columnWidths[key] ?? defaultColumnWidth(column);
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      const width = Math.min(
        MAX_COLUMN_WIDTH,
        Math.max(MIN_COLUMN_WIDTH, startWidth + moveEvent.clientX - startX),
      );
      setColumnWidths((current) => ({ ...current, [key]: width }));
    };

    const finish = () => {
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };

    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  const renderCellInspector = () => {
    if (!inspectedCell || typeof document === 'undefined') return null;
    const display = formatCell(inspectedCell.value);
    return createPortal(
      <div className="fixed inset-0 z-[1400] flex items-center justify-center bg-black/10 p-6">
        <div className="flex max-h-[70vh] w-full max-w-[760px] flex-col overflow-hidden rounded-[6px] border border-[#dfe4ec] bg-white">
          <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e7eaf0] px-3.5">
            <div className="min-w-0">
              <span className="text-[12px] font-medium text-[#344054]">
                {columnTitle(inspectedCell.column, 0)}
              </span>
              {inspectedCell.column.typeName ? (
                <span
                  className={`ml-2 text-[10px] font-medium ${typeTone(inspectedCell.column.typeName)}`}
                >
                  {inspectedCell.column.typeName}
                </span>
              ) : null}
              <span className="ml-3 text-[10px] text-[#98a2b3]">
                第 {inspectedCell.rowIndex + 1} 行
              </span>
            </div>
            <button
              type="button"
              title="关闭"
              aria-label="关闭单元格详情"
              onClick={() => setInspectedCell(undefined)}
              className="flex h-7 w-7 items-center justify-center rounded-[4px] text-[#667085] hover:bg-[#f5f7fa] hover:text-[#344054]"
            >
              <X size={14} />
            </button>
          </div>
          <pre className="min-h-0 flex-1 overflow-auto whitespace-pre-wrap break-words bg-[#fbfcfe] p-4 text-[12px] leading-6 text-[#344054]">
            {display}
          </pre>
          <div className="flex h-10 shrink-0 items-center justify-end border-t border-[#e7eaf0] px-3">
            <button
              type="button"
              onClick={async () => {
                await copyText(display);
                flashCopied();
              }}
              className="inline-flex h-7 items-center gap-1.5 rounded-[4px] border border-[#dfe4ec] bg-white px-2.5 text-[11px] text-[#475467] hover:bg-[#f7f9fc]"
            >
              {copied ? <Check size={12} /> : <Copy size={12} />}
              {copied ? '已复制' : '复制内容'}
            </button>
          </div>
        </div>
      </div>,
      document.body,
    );
  };

  const renderWorkspace = (expandedView: boolean) => {
    const frameClassName = expandedView
      ? 'fixed inset-4 z-[1300] flex min-h-0 flex-col overflow-hidden rounded-[6px] border border-[#d9dee8] bg-white'
      : 'flex h-full min-h-0 flex-col overflow-hidden bg-white';

    if (!result) {
      return (
        <div className={frameClassName}>
          <div className="flex h-full items-center justify-center text-center">
            <div>
              <div className="text-[13px] font-medium text-[#475467]">
                SQL 运行结果
              </div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">
                点击顶部运行按钮执行当前编辑器中的 SQL
              </div>
            </div>
          </div>
        </div>
      );
    }

    if (result.status === 'RUNNING') {
      return (
        <div className={frameClassName}>
          <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
            <LoaderCircle size={16} className="mr-2 animate-spin text-[#3972cf]" />
            正在执行 SQL…
          </div>
        </div>
      );
    }

    if (result.status !== 'SUCCESS') {
      const cancelled = result.status === 'CANCELLED';
      const timeout = result.status === 'TIMEOUT';
      return (
        <div className={frameClassName}>
          <div className="flex h-full items-center justify-center px-6 text-center">
            <div className="w-full max-w-[720px] rounded-[6px] border border-[#f0d5d2] bg-[#fffafa] px-5 py-4">
              <div className="flex items-center justify-center gap-2 text-[13px] font-medium text-[#b42318]">
                {timeout ? (
                  <TriangleAlert size={15} />
                ) : (
                  <XCircle size={15} />
                )}
                {cancelled
                  ? 'SQL 已取消'
                  : timeout
                    ? 'SQL 执行超时'
                    : 'SQL 执行失败'}
              </div>
              <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
                {result.message || '数据库未返回更多错误信息'}
              </div>
              <div className="mt-3 flex items-center justify-center gap-3 text-[10px] text-[#98a2b3]">
                <span>耗时 {result.durationMs} ms</span>
                {result.message ? (
                  <button
                    type="button"
                    onClick={handleCopyMessage}
                    className="inline-flex items-center gap-1 text-[#667085] hover:text-[#344054]"
                  >
                    {copied ? <Check size={11} /> : <Copy size={11} />}
                    {copied ? '已复制' : '复制错误'}
                  </button>
                ) : null}
                {expandedView ? (
                  <button
                    type="button"
                    onClick={() => setExpanded(false)}
                    className="inline-flex items-center gap-1 text-[#667085] hover:text-[#344054]"
                  >
                    <Shrink size={11} /> 退出全屏
                  </button>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      );
    }

    if (output?.kind === 'UPDATE_COUNT') {
      const affectedRows =
        typeof output.affectedRows === 'number' && output.affectedRows >= 0
          ? output.affectedRows
          : '—';
      return (
        <div className={frameClassName}>
          <div className="flex h-full items-center justify-center text-center">
            <div className="min-w-[300px] rounded-[6px] border border-[#caeadb] bg-[#f7fcf9] px-7 py-5">
              <div className="flex items-center justify-center gap-2 text-[13px] font-medium text-[#067647]">
                <CheckCircle2 size={15} /> SQL 执行完成
              </div>
              <div className="mt-4 grid grid-cols-2 gap-x-8 gap-y-2 text-left text-[11px]">
                <span className="text-[#98a2b3]">影响行数</span>
                <span className="text-right font-medium text-[#344054]">
                  {affectedRows}
                </span>
                <span className="text-[#98a2b3]">执行耗时</span>
                <span className="text-right text-[#475467]">
                  {result.durationMs} ms
                </span>
              </div>
              {expandedView ? (
                <button
                  type="button"
                  onClick={() => setExpanded(false)}
                  className="mt-4 inline-flex items-center gap-1 text-[10px] text-[#667085] hover:text-[#344054]"
                >
                  <Shrink size={11} /> 退出全屏
                </button>
              ) : null}
            </div>
          </div>
        </div>
      );
    }

    const returnedRows = output?.returnedRows ?? rows.length;
    const searchActive = Boolean(query.trim());

    return (
      <div className={frameClassName}>
        <div className="flex h-9 shrink-0 items-center justify-between gap-3 border-b border-[#e7eaf0] bg-white px-3">
          <div className="flex min-w-0 items-center gap-3 text-[10px]">
            <span className="inline-flex shrink-0 items-center gap-1.5 font-medium text-[#067647]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#12b76a]" />
              查询成功
            </span>
            <span className="shrink-0 text-[#667085]">
              {searchActive ? `${filteredRows.length} / ${returnedRows}` : returnedRows} 行
            </span>
            <span className="shrink-0 text-[#667085]">{columns.length} 列</span>
            <span className="shrink-0 text-[#667085]">{result.durationMs} ms</span>
            {output?.truncated ? (
              <span
                className="inline-flex shrink-0 items-center gap-1 text-[#b54708]"
                title="查询结果超过最大返回行数，当前仅展示后端返回的数据"
              >
                <TriangleAlert size={11} /> 结果已截断
              </span>
            ) : null}
          </div>

          <div className="flex shrink-0 items-center gap-1">
            {searchOpen ? (
              <div className="mr-1 flex h-7 w-[190px] items-center rounded-[4px] border border-[#d8deea] bg-white px-2 focus-within:border-[#a9c5ee]">
                <Search size={12} className="shrink-0 text-[#98a2b3]" />
                <input
                  autoFocus
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="搜索当前结果"
                  className="min-w-0 flex-1 border-0 bg-transparent px-1.5 text-[11px] text-[#344054] outline-none placeholder:text-[#b0b7c3]"
                />
                {query ? (
                  <button
                    type="button"
                    title="清空搜索"
                    onClick={() => setQuery('')}
                    className="text-[#98a2b3] hover:text-[#667085]"
                  >
                    <X size={11} />
                  </button>
                ) : null}
              </div>
            ) : null}

            <ToolbarButton
              title={searchOpen ? '关闭搜索' : '搜索结果'}
              active={searchOpen}
              onClick={() => {
                setSearchOpen((value) => !value);
                if (searchOpen) setQuery('');
              }}
            >
              <Search size={13} />
            </ToolbarButton>
            <ToolbarButton title="复制当前结果" onClick={handleCopyResult}>
              {copied ? <Check size={13} /> : <Copy size={13} />}
            </ToolbarButton>
            <ToolbarButton title="导出 CSV" onClick={handleExportCsv}>
              <Download size={13} />
            </ToolbarButton>

            <div className="relative">
              <ToolbarButton
                title="显示或隐藏列"
                active={columnMenuOpen}
                onClick={() => {
                  setColumnMenuOpen((value) => !value);
                  setDetailOpen(false);
                }}
              >
                <Columns3 size={13} />
              </ToolbarButton>
              {columnMenuOpen ? (
                <div className="absolute right-0 top-8 z-50 max-h-[260px] w-[230px] overflow-auto rounded-[5px] border border-[#dfe4ec] bg-white py-1.5">
                  <div className="flex items-center justify-between border-b border-[#eef0f4] px-2.5 pb-1.5 text-[10px] text-[#98a2b3]">
                    <span>显示列</span>
                    {hiddenColumnKeys.size ? (
                      <button
                        type="button"
                        onClick={showAllColumns}
                        className="text-[#3972cf] hover:text-[#245cae]"
                      >
                        全部显示
                      </button>
                    ) : null}
                  </div>
                  {columns.map((column, index) => {
                    const key = columnKey(column, index);
                    const visible = !hiddenColumnKeys.has(key);
                    return (
                      <button
                        type="button"
                        key={key}
                        onClick={() => toggleColumn(key)}
                        className="flex w-full items-center gap-2 px-2.5 py-1.5 text-left text-[11px] text-[#475467] hover:bg-[#f7f9fc]"
                      >
                        <span
                          className={[
                            'flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-[3px] border',
                            visible
                              ? 'border-[#8fb3e8] bg-[#eef5ff] text-[#3972cf]'
                              : 'border-[#d5dae3] bg-white text-transparent',
                          ].join(' ')}
                        >
                          <Check size={10} />
                        </span>
                        <span className="min-w-0 flex-1 truncate">
                          {columnTitle(column, index)}
                        </span>
                        {column.typeName ? (
                          <span className={`shrink-0 text-[9px] ${typeTone(column.typeName)}`}>
                            {column.typeName}
                          </span>
                        ) : null}
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </div>

            <div className="relative">
              <ToolbarButton
                title="结果详情"
                active={detailOpen}
                onClick={() => {
                  setDetailOpen((value) => !value);
                  setColumnMenuOpen(false);
                }}
              >
                <Info size={13} />
              </ToolbarButton>
              {detailOpen ? (
                <div className="absolute right-0 top-8 z-50 w-[230px] rounded-[5px] border border-[#dfe4ec] bg-white p-3 text-[10px]">
                  <div className="font-medium text-[#344054]">结果详情</div>
                  <div className="mt-2 grid grid-cols-[72px_1fr] gap-y-1.5 text-[#667085]">
                    <span className="text-[#98a2b3]">返回行数</span>
                    <span className="text-right">{returnedRows}</span>
                    <span className="text-[#98a2b3]">字段数量</span>
                    <span className="text-right">{columns.length}</span>
                    <span className="text-[#98a2b3]">执行耗时</span>
                    <span className="text-right">{result.durationMs} ms</span>
                    {output?.dataSourceId ? (
                      <>
                        <span className="text-[#98a2b3]">数据源</span>
                        <span
                          className="truncate text-right"
                          title={output.dataSourceId}
                        >
                          {output.dataSourceId}
                        </span>
                      </>
                    ) : null}
                    <span className="text-[#98a2b3]">结果状态</span>
                    <span className="text-right">
                      {output?.truncated ? '已截断' : '完整返回'}
                    </span>
                  </div>
                </div>
              ) : null}
            </div>

            <ToolbarButton
              title={expandedView ? '退出全屏' : '全屏查看'}
              onClick={() => setExpanded(!expandedView)}
            >
              {expandedView ? <Shrink size={13} /> : <Expand size={13} />}
            </ToolbarButton>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-auto bg-white">
          {visibleColumns.length ? (
            <table className="min-w-full table-fixed border-separate border-spacing-0 whitespace-nowrap text-[11px]">
              <thead className="sticky top-0 z-20 bg-[#f7f9fc] text-[#344054]">
                <tr>
                  <th
                    className="sticky left-0 z-30 w-12 border-b border-r border-[#dfe4ec] bg-[#f3f6fa] px-2 py-2 text-right text-[10px] font-medium text-[#98a2b3]"
                    style={{ width: 48 }}
                  >
                    #
                  </th>
                  {visibleColumns.map(({ column, index, key }) => {
                    const width = columnWidths[key] ?? defaultColumnWidth(column);
                    return (
                      <th
                        key={key}
                        title={`${columnTitle(column, index)}${column.typeName ? ` · ${column.typeName}` : ''}${column.nullable ? ' · Nullable' : ''}`}
                        className="relative border-b border-r border-[#dfe4ec] bg-[#f7f9fc] px-2.5 py-2 text-left font-medium"
                        style={{ width, minWidth: width, maxWidth: width }}
                      >
                        <div className="flex min-w-0 items-baseline gap-2 overflow-hidden">
                          <span className="min-w-0 truncate text-[#344054]">
                            {columnTitle(column, index)}
                          </span>
                          {column.typeName ? (
                            <span
                              className={`shrink-0 text-[9px] font-medium ${typeTone(column.typeName)}`}
                            >
                              {column.typeName}
                            </span>
                          ) : null}
                        </div>
                        <div
                          role="separator"
                          aria-orientation="vertical"
                          aria-label={`调整 ${columnTitle(column, index)} 列宽`}
                          onPointerDown={(event) =>
                            startColumnResize(event, key, column)
                          }
                          className="absolute -right-1 top-0 z-10 h-full w-2 cursor-col-resize touch-none after:absolute after:right-1 after:top-1/4 after:h-1/2 after:w-px after:bg-transparent hover:after:bg-[#8fb3e8]"
                        />
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody className="text-[#344054]">
                {filteredRows.map(({ row, rowIndex }) => (
                  <tr
                    key={rowIndex}
                    className="odd:bg-white even:bg-[#fcfdff] hover:!bg-[#f4f8ff]"
                  >
                    <td className="sticky left-0 z-10 border-b border-r border-[#e9edf3] bg-[#fafbfd] px-2 py-1.5 text-right text-[10px] text-[#98a2b3]">
                      {rowIndex + 1}
                    </td>
                    {visibleColumns.map(({ column, index, key }) => {
                      const value = row?.[index];
                      const display = formatCell(value);
                      const width =
                        columnWidths[key] ?? defaultColumnWidth(column);
                      const numeric = isNumericColumn(column) || typeof value === 'number';
                      return (
                        <td
                          key={`${rowIndex}-${key}`}
                          title={display}
                          onDoubleClick={() =>
                            setInspectedCell({ column, rowIndex, value })
                          }
                          className={[
                            'border-b border-r border-[#e9edf3] px-2.5 py-1.5 leading-5',
                            numeric ? 'text-right tabular-nums' : 'text-left',
                            value === null
                              ? 'italic text-[#9aa4b2]'
                              : 'text-[#344054]',
                          ].join(' ')}
                          style={{ width, minWidth: width, maxWidth: width }}
                        >
                          <div className="truncate">{display}</div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          ) : columns.length ? (
            <div className="flex h-full items-center justify-center text-center text-[11px] text-[#98a2b3]">
              <div>
                <div>所有字段都已隐藏</div>
                <button
                  type="button"
                  onClick={showAllColumns}
                  className="mt-2 text-[#3972cf] hover:text-[#245cae]"
                >
                  显示全部字段
                </button>
              </div>
            </div>
          ) : (
            <div className="flex h-full items-center justify-center text-[11px] text-[#98a2b3]">
              SQL 执行成功，结果集没有可展示的字段
            </div>
          )}

          {visibleColumns.length && searchActive && !filteredRows.length ? (
            <div className="absolute inset-x-0 top-20 text-center text-[11px] text-[#98a2b3]">
              当前结果中没有匹配 “{query.trim()}” 的数据
            </div>
          ) : null}
        </div>
        {renderCellInspector()}
      </div>
    );
  };

  if (expanded && typeof document !== 'undefined') {
    return createPortal(renderWorkspace(true), document.body);
  }

  return renderWorkspace(false);
};

export default SqlResultWorkspace;

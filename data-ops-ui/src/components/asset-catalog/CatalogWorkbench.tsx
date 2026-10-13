import { Button, Input, Tooltip } from 'antd';
import { LayoutGrid, List, RotateCw, Search, SlidersHorizontal, X } from 'lucide-react';
import { cloneElement, isValidElement, useId, type CSSProperties, type ReactElement, type ReactNode } from 'react';
import './index.less';

export type CatalogDisplay = 'list' | 'grid';
export interface CatalogFilterChip {
  key: string;
  label: string;
  onRemove: () => void;
}

export function CatalogSearch({
  value,
  placeholder,
  loading,
  onChange,
  onSearch,
  onFilter,
  filterCount = 0,
}: {
  value: string;
  placeholder: string;
  loading: boolean;
  onChange: (value: string) => void;
  onSearch: () => void;
  onFilter: () => void;
  filterCount?: number;
}) {
  return (
    <div className="catalog-search-bar">
      <form
        className="catalog-search"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          onSearch();
        }}
      >
        <Search size={20} aria-hidden />
        <Input
          aria-label="搜索目录"
          value={value}
          placeholder={placeholder}
          allowClear
          onChange={(event) => onChange(event.target.value)}
          variant="borderless"
        />
        <Button type="primary" htmlType="submit" aria-label="搜索" aria-busy={loading}>
          搜索
        </Button>
      </form>
      <Button className="catalog-filter-button" icon={<SlidersHorizontal size={16} />} onClick={onFilter}>
        筛选{filterCount > 0 && <span className="catalog-filter-count">{filterCount}</span>}
      </Button>
    </div>
  );
}

export function CatalogFilters({ chips, onReset }: { chips: CatalogFilterChip[]; onReset: () => void }) {
  if (!chips.length) return null;
  return (
    <div className="catalog-active-filters" aria-label="已选筛选条件">
      <span>已选条件</span>
      {chips.map((chip) => (
        <button type="button" key={chip.key} onClick={chip.onRemove} aria-label={`移除筛选：${chip.label}`}>
          {chip.label}
          <X size={12} aria-hidden />
        </button>
      ))}
      <button type="button" className="catalog-reset" onClick={onReset}>
        清空条件
      </button>
    </div>
  );
}

export function CatalogWorkbench({ sidebar, children }: { sidebar: ReactNode; children: ReactNode }) {
  return (
    <div className="catalog-workbench">
      <aside className="catalog-sidebar">{sidebar}</aside>
      <section className="catalog-results" aria-label="目录检索结果">
        {children}
      </section>
    </div>
  );
}

export function CatalogResultToolbar({
  title,
  count,
  loading,
  failed,
  display,
  onDisplay,
  onRefresh,
  sort,
}: {
  title: string;
  count?: number;
  loading: boolean;
  failed?: boolean;
  display: CatalogDisplay;
  onDisplay: (value: CatalogDisplay) => void;
  onRefresh: () => void;
  sort: ReactNode;
}) {
  return (
    <div className="catalog-result-toolbar">
      <div className="catalog-result-title">
        <h2>{title}</h2>
        <span aria-live="polite">
          {loading ? (
            '读取中…'
          ) : failed ? (
            '读取失败'
          ) : (
            <>
              <strong>{count ?? 0}</strong> 项结果
            </>
          )}
        </span>
      </div>
      <div className="catalog-result-tools">
        {sort}
        <div className="catalog-view-switch" role="group" aria-label="显示方式">
          <Tooltip title="列表视图">
            <button
              type="button"
              aria-label="列表视图"
              aria-pressed={display === 'list'}
              onClick={() => onDisplay('list')}
            >
              <List size={17} />
            </button>
          </Tooltip>
          <Tooltip title="卡片视图">
            <button
              type="button"
              aria-label="卡片视图"
              aria-pressed={display === 'grid'}
              onClick={() => onDisplay('grid')}
            >
              <LayoutGrid size={16} />
            </button>
          </Tooltip>
        </div>
        <Tooltip title="刷新结果">
          <Button
            type="text"
            aria-label="刷新结果"
            disabled={loading}
            icon={<RotateCw size={16} />}
            onClick={onRefresh}
          />
        </Tooltip>
      </div>
    </div>
  );
}

export function CatalogTypeButton({
  label,
  icon,
  active,
  count,
  onClick,
}: {
  label: string;
  icon: ReactNode;
  active: boolean;
  count?: number;
  onClick: () => void;
}) {
  return (
    <button type="button" className="catalog-type-button" aria-pressed={active} onClick={onClick}>
      {icon}
      <span>{label}</span>
      {count != null && <small>{count}</small>}
    </button>
  );
}

export function CatalogObjectIcon({ children, color = '#607086' }: { children: ReactNode; color?: string }) {
  return (
    <span className="catalog-object-icon" style={{ '--object-color': color } as CSSProperties}>
      {children}
    </span>
  );
}

export function CatalogField({ label, children }: { label: string; children: ReactNode }) {
  const id = useId();
  return (
    <div className="catalog-field">
      <span id={id}>{label}</span>
      {isValidElement(children)
        ? cloneElement(children as ReactElement<{ 'aria-labelledby'?: string }>, { 'aria-labelledby': id })
        : children}
    </div>
  );
}

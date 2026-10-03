import type { TreeProps } from 'antd';
import { Input, Spin, Tree } from 'antd';
import { ChevronDown, ChevronLeft, ChevronRight, Search } from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useMemo, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import type { SemanticDomainNode } from '@/services/semantic/types';

export interface SemanticTreeNode {
  key: string;
  title: string;
  searchText: string;
  children?: SemanticTreeNode[];
}

interface ModelTreePaneProps {
  domainTree: SemanticDomainNode[];
  semanticLoading: boolean;
  selectedDomainId?: number;
  width: number;
  collapsed: boolean;
  onSelect: (domainId: number | undefined) => void;
  onResizeStart: (event: ReactPointerEvent) => void;
  onCollapsedChange: (collapsed: boolean) => void;
  onGoSemantic?: () => void;
}

const buildDomainTreeData = (domains: SemanticDomainNode[]): SemanticTreeNode[] =>
  domains
    .map((domain) => ({
      key: `domain:${domain.id}`,
      title: domain.name,
      searchText: domain.name,
      children: buildDomainTreeData(domain.children ?? []),
    }))
    .sort((left, right) => left.title.localeCompare(right.title, 'zh-CN'));

const filterSemanticTreeData = (tree: SemanticTreeNode[], keyword: string): SemanticTreeNode[] => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) {
    return tree;
  }
  const filterNodes = (nodes: SemanticTreeNode[]): SemanticTreeNode[] =>
    nodes.flatMap((node) => {
      const children = node.children ? filterNodes(node.children) : [];
      if (node.searchText.toLowerCase().includes(normalized)) {
        return [{ ...node, children: node.children }];
      }
      return children.length ? [{ ...node, children }] : [];
    });
  return filterNodes(tree);
};

const ModelTreePane = ({
  domainTree,
  semanticLoading,
  selectedDomainId,
  width,
  collapsed,
  onSelect,
  onResizeStart,
  onCollapsedChange,
  onGoSemantic,
}: ModelTreePaneProps) => {
  const [searchValue, setSearchValue] = useState('');

  const treeData = useMemo(() => {
    const built = buildDomainTreeData(domainTree);
    return filterSemanticTreeData(built, searchValue);
  }, [domainTree, searchValue]);

  return (
    <>
      <aside
        className="group relative shrink-0 overflow-hidden bg-white transition-[width] duration-200 ease-out max-md:hidden"
        style={{ width: collapsed ? 0 : width }}
      >
        <div className="flex h-full flex-col overflow-hidden" style={{ width }}>
          <div className="flex h-9 shrink-0 items-center border-b border-[#e5e7eb] bg-[#f7f7f8] px-3">
            <span className="text-[13px] font-semibold text-[#30323b]">模型目录</span>
          </div>

          <div className="flex h-9 shrink-0 items-center border-b border-[#e8e9ec] px-2.5">
            <Input
              allowClear
              size="small"
              variant="filled"
              prefix={<Search size={13} className="text-[#667085]" />}
              placeholder="搜索业务域"
              value={searchValue}
              onChange={(event) => setSearchValue(event.target.value)}
              className="!h-7"
            />
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
            <Spin spinning={semanticLoading} wrapperClassName="block min-h-full">
              {treeData.length ? (
                <Tree
                  blockNode
                  defaultExpandAll
                  autoExpandParent={Boolean(searchValue.trim())}
                  selectedKeys={selectedDomainId ? [`domain:${selectedDomainId}`] : []}
                  treeData={treeData}
                  switcherIcon={<ChevronDown size={12} strokeWidth={1.8} />}
                  onSelect={(keys) => {
                    const key = keys[0];
                    if (!key) {
                      onSelect(undefined);
                      return;
                    }
                    const raw = String(key);
                    if (raw.startsWith('domain:')) {
                      const id = Number(raw.replace('domain:', ''));
                      onSelect(Number.isFinite(id) && id > 0 ? id : undefined);
                    }
                  }}
                  className="modeling-tree bg-transparent"
                />
              ) : (
                <div>
                  <YakEmpty
                    compact
                    title={searchValue.trim() ? '没有匹配的业务域' : '暂无业务域'}
                    description="请先在语义中心配置业务域"
                  />
                  {onGoSemantic ? (
                    <YakButton size="small" className="!mt-2" onClick={onGoSemantic}>
                      去语义中心配置
                    </YakButton>
                  ) : null}
                </div>
              )}
            </Spin>
          </div>
        </div>
      </aside>

      <div
        role="separator"
        aria-label="拖拽调整目录宽度"
        aria-orientation="vertical"
        onPointerDown={collapsed ? undefined : onResizeStart}
        className={[
          'group relative z-20 w-px shrink-0 touch-none max-md:hidden',
          collapsed ? 'cursor-default' : 'cursor-col-resize',
        ].join(' ')}
      >
        <div
          className={[
            'absolute inset-y-0 left-1/2 z-10 w-3 -translate-x-1/2',
            collapsed ? 'cursor-default' : 'cursor-col-resize',
          ].join(' ')}
        />
        <div
          className={[
            'pointer-events-none absolute inset-y-0 left-0 w-px bg-[#dfe3e8]',
            'transition-[width,background-color] duration-150',
            !collapsed
              ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
              : '',
          ].join(' ')}
        />
        <button
          type="button"
          aria-label={collapsed ? '展开目录' : '折叠目录'}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onCollapsedChange(!collapsed)}
          className={[
            'absolute left-px top-1/2 z-20 flex h-7 w-3 -translate-y-1/2 items-center justify-center rounded-r-[3px]',
            'border border-l-0 border-[#dfe3e8] bg-white text-[#7b808a] shadow-[0_1px_2px_rgba(16,24,40,0.04)]',
            'opacity-0 transition-[opacity,color,border-color,box-shadow] duration-150 group-hover:opacity-100 focus:opacity-100',
            'hover:border-[#cfd4dc] hover:text-[#344054] focus:outline-none focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)]',
          ].join(' ')}
        >
          {collapsed ? <ChevronRight size={11} /> : <ChevronLeft size={11} />}
        </button>
      </div>

      <style>{`
        .modeling-tree.ant-tree { color: #344054; }
        .modeling-tree .ant-tree-list-holder-inner { gap: 1px; }
        .modeling-tree .ant-tree-treenode {
          box-sizing: border-box; width: 100%; min-height: 30px; padding: 0 6px !important;
          align-items: center; border-radius: 0; transition: background-color 0.15s ease;
        }
        .modeling-tree .ant-tree-treenode:hover,
        .modeling-tree .ant-tree-treenode:has(.ant-tree-node-selected) { background: #f5f5f5; }
        .modeling-tree .ant-tree-node-content-wrapper {
          display: flex; min-width: 0; height: 30px; flex: 1; align-items: center;
          padding: 0 !important; border-radius: 0 !important; background: transparent !important;
        }
        .modeling-tree .ant-tree-title { display: flex; min-width: 0; flex: 1; }
        .modeling-tree .ant-tree-switcher {
          width: 18px; min-width: 18px; height: 30px; line-height: 30px; color: #98a2b3;
        }
      `}</style>
    </>
  );
};

export default ModelTreePane;

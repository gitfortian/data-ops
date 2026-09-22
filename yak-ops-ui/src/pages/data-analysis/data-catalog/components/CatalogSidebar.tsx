import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { CatalogDataset } from '@/services/data-analysis';
import { Input, Spin, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { ChevronDown, Database, Folder, Search, TableProperties } from 'lucide-react';
import type { CatalogTreeNode } from '../types';

interface CatalogSidebarProps {
  datasets: CatalogDataset[];
  treeData: CatalogTreeNode[];
  selectedKey: string;
  keyword: string;
  isLoading: boolean;
  onKeywordChange: (keyword: string) => void;
  onSelect: (key: string) => void;
}

export function CatalogSidebar({
  datasets,
  treeData,
  selectedKey,
  keyword,
  isLoading,
  onKeywordChange,
  onSelect,
}: CatalogSidebarProps) {
  const renderTreeTitle = (rawNode: DataNode) => {
    const node = rawNode as CatalogTreeNode;
    const dataset = node.datasetId
      ? datasets.find((item) => item.id === node.datasetId)
      : undefined;
    const icon = node.kind === 'dataset'
      ? <TableProperties size={14} className="shrink-0 text-[#667085]" />
      : node.kind === 'root'
        ? <Database size={14} className="shrink-0 text-[#475467]" />
        : <Folder size={14} className="shrink-0 text-[#667085]" />;

    return (
      <div className="flex min-w-0 flex-1 items-center gap-2" title={node.title}>
        {icon}
        <span
          className={[
            'min-w-0 flex-1 truncate text-[14px] leading-8',
            node.kind === 'dataset'
              ? 'font-normal text-[#344054]'
              : 'font-medium text-[#161823]',
          ].join(' ')}
        >
          {node.title}
        </span>
        {dataset ? (
          <span
            className={[
              'h-1.5 w-1.5 shrink-0 rounded-full',
              dataset.status === 'ONLINE' ? 'bg-[#667085]' : 'bg-[#c7cbd1]',
            ].join(' ')}
          />
        ) : typeof node.datasetCount === 'number' ? (
          <span className="shrink-0 text-[12px] text-[#8a8f99]">
            {node.datasetCount}
          </span>
        ) : null}
      </div>
    );
  };

  return (
    <div className="flex h-full flex-col overflow-hidden py-3">
      <div className="flex h-7 shrink-0 items-center justify-between px-4">
        <span className="text-[14px] font-semibold text-[#161823]">目录</span>
        <span className="text-[12px] text-[#8a8f99]">{datasets.length}</span>
      </div>

      <div className="mt-2 shrink-0 px-[14px]">
        <Input
          allowClear
          size="small"
          variant="filled"
          value={keyword}
          prefix={<Search size={14} className="text-[#8a8f99]" />}
          placeholder="搜索目录 / Dataset"
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>

      <div className="mt-2 min-h-0 flex-1 overflow-y-auto px-[14px]">
        <Spin spinning={isLoading} wrapperClassName="block min-h-full">
          {treeData.length ? (
            <Tree
              blockNode
              defaultExpandAll
              autoExpandParent={Boolean(keyword.trim())}
              selectedKeys={[selectedKey]}
              treeData={treeData}
              titleRender={renderTreeTitle}
              switcherIcon={<ChevronDown size={12} strokeWidth={1.8} />}
              onSelect={(keys) => {
                const key = String(keys[0] || '');
                if (key) onSelect(key);
              }}
              className="catalog-tree bg-transparent"
            />
          ) : (
            <div className="flex min-h-[220px] items-center justify-center">
              <YakOpsEmpty
                width={138}
                height={92}
                title={keyword.trim() ? '未找到匹配 Dataset' : '暂无已发布 Dataset'}
                description={
                  keyword.trim()
                    ? '换个关键词后重新搜索。'
                    : '发布 Dataset 后会在目录中展示。'
                }
                showCaption
              />
            </div>
          )}
        </Spin>
      </div>
    </div>
  );
}

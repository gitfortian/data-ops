import { Select, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { listMdmAttributes } from '@/services/mdm/api';
import type { MdmAttributeRecord } from '@/services/mdm/types';
import {
  listDataSourceColumns,
  type DataSourceCatalogColumn,
} from '@/services/data-source/catalog';

export interface MappingTableRef {
  database?: string;
  schema?: string;
  name: string;
}

interface FieldMappingEditorProps {
  datasourceId?: number;
  table?: MappingTableRef | null;
  entityId?: number;
  /** 属性编码 → 源列名 */
  value: Record<string, string>;
  onChange: (mapping: Record<string, string>) => void;
  /** 阻断性问题(如 PK 无列可映射)提示文案;恢复可用时回传 null */
  onIssueChange?: (issue: string | null) => void;
}

/**
 * 来源确认弹窗的字段映射编辑器(55/R0):
 * 属性×源列自动按同名(忽略大小写)预填,可改选;PK 必须映射。
 */
const FieldMappingEditor = ({
  datasourceId,
  table,
  entityId,
  value,
  onChange,
  onIssueChange,
}: FieldMappingEditorProps) => {
  const [attributes, setAttributes] = useState<MdmAttributeRecord[]>([]);
  const [columns, setColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!entityId || !datasourceId || !table?.name) {
      setAttributes([]);
      setColumns([]);
      onIssueChange?.(null);
      return undefined;
    }
    setLoading(true);
    Promise.all([
      listMdmAttributes(entityId).catch(() => [] as MdmAttributeRecord[]),
      listDataSourceColumns(datasourceId, table.database, table.schema, table.name).catch(
        () => [] as DataSourceCatalogColumn[],
      ),
    ])
      .then(([attrs, cols]) => {
        if (cancelled) return;
        setAttributes(attrs);
        setColumns(cols);
        const byLower = new Map(cols.map((column) => [column.name.toLowerCase(), column.name]));
        const seed: Record<string, string> = {};
        attrs.forEach((attribute) => {
          const hit = byLower.get(attribute.code.toLowerCase());
          if (hit) seed[attribute.code] = hit;
        });
        onChange(seed);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // onChange/onIssueChange 为父级稳定回调,不纳入依赖避免循环重建
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityId, datasourceId, table?.database, table?.schema, table?.name]);

  const columnOptions = useMemo(
    () => columns.map((column) => ({ label: column.name, value: column.name })),
    [columns],
  );

  const pkMissing = useMemo(
    () => attributes.some((a) => a.type === 'PK' && !value[a.code]),
    [attributes, value],
  );

  useEffect(() => {
    onIssueChange?.(
      pkMissing ? 'PK 属性尚未映射到源列，加工 SQL 无法生成 master_id，请先选择' : null,
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pkMissing]);

  if (!entityId) {
    return (
      <Typography.Text type="secondary" className="text-[12px]">
        选择归属实体后可配置字段映射
      </Typography.Text>
    );
  }
  if (loading) {
    return (
      <div className="py-2">
        <Spin size="small" /> <span className="text-[12px] text-[#667085]">加载属性与源表列…</span>
      </div>
    );
  }
  if (attributes.length === 0) {
    return (
      <Typography.Text type="secondary" className="text-[12px]">
        该实体暂无属性定义，加工时按「属性编码=源列名」同名回退
      </Typography.Text>
    );
  }
  return (
    <div className="max-h-60 overflow-y-auto rounded-lg border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2">
      {attributes.map((attribute) => {
        const mapped = value[attribute.code];
        return (
          <div key={attribute.code} className="flex items-center gap-2 py-1">
            <div className="w-40 shrink-0 truncate text-[13px]" title={`${attribute.name}（${attribute.code}）`}>
              {attribute.name || attribute.code}
              <span className="text-[#98a2b3]">（{attribute.code}）</span>
            </div>
            {attribute.type === 'PK' && (
              <Tag color="purple" className="!mr-0 shrink-0">
                PK
              </Tag>
            )}
            <div className="min-w-0 flex-1">
              <Select
                size="small"
                className="!w-full"
                showSearch
                allowClear
                placeholder={
                  mapped
                    ? undefined
                    : attribute.type === 'PK'
                      ? 'PK 必须选择源列'
                      : '未匹配到同名列，加工时将回退同名(可能失败)'
                }
                value={mapped || undefined}
                options={columnOptions}
                optionFilterProp="label"
                status={attribute.type === 'PK' && !mapped ? 'error' : undefined}
                onChange={(next?: string) =>
                  onChange({ ...value, [attribute.code]: next ?? '' })
                }
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default FieldMappingEditor;

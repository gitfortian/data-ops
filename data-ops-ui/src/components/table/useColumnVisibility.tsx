import { Button, Checkbox, Popover } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SlidersHorizontal } from 'lucide-react';
import { useState } from 'react';

/** View-only column selection; all fields remain available and queries stay unchanged. */
export function useColumnVisibility<T extends object>(columns: ColumnsType<T>, defaults: string[]) {
  const id = (column: ColumnsType<T>[number], index: number) => String(
    column.key ?? ('dataIndex' in column ? column.dataIndex : undefined) ?? index,
  );
  const [selected, setSelected] = useState(defaults);
  const matched = columns.filter((column, index) => selected.includes(id(column, index)));
  const visible = matched.length ? matched : columns.slice(0, 1);
  const control = <Popover trigger="click" placement="bottomRight" content={
    <div className="flex max-h-[360px] w-[220px] flex-col gap-2 overflow-y-auto p-1">
      <div className="mb-1 text-[13px] font-medium text-[#344054]">显示列</div>
      {columns.map((column, index) => {
        const key = id(column, index);
        const checked = visible.includes(column);
        return <Checkbox key={key} checked={checked}
          disabled={checked && visible.length <= 1}
          onChange={(event) => setSelected(current => event.target.checked ? [...new Set([...current, key])] : current.filter(value => value !== key))}>
          {typeof column.title === 'string' ? column.title : key}
        </Checkbox>;
      })}
      <Button size="small" className="mt-2" onClick={() => setSelected(defaults)}>恢复默认列</Button>
    </div>
  }><Button icon={<SlidersHorizontal size={14} />}>列设置</Button></Popover>;
  return { columns: visible, control,
    scrollWidth: visible.reduce((sum, column) => sum + (typeof column.width === 'number' ? column.width : 180), 0),
  };
}

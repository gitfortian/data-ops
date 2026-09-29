import { Pagination } from 'antd';

interface SecurityPaginationProps {
  current: number;
  pageSize: number;
  total: number;
  disabled?: boolean;
  bordered?: boolean;
  onChange: (current: number, pageSize: number) => void;
}

export default function SecurityPagination({
  current,
  pageSize,
  total,
  disabled = false,
  bordered = true,
  onChange,
}: SecurityPaginationProps) {
  if (total <= 0) return null;

  return (
    <div
      className={[
        'flex w-full shrink-0 items-center justify-end bg-white',
        bordered
          ? 'h-16 rounded-lg border border-slate-200/70 px-6'
          : 'h-12 px-0',
      ].join(' ')}
    >
      <div className="flex flex-wrap items-center justify-end gap-4">
        <span className="text-sm text-slate-500">共 {total} 条</span>

        <Pagination
          current={current}
          pageSize={pageSize}
          total={total}
          disabled={disabled}
          showSizeChanger
          showQuickJumper={false}
          pageSizeOptions={[10, 20, 50, 100]}
          showTotal={undefined}
          onChange={onChange}
        />
      </div>
    </div>
  );
}

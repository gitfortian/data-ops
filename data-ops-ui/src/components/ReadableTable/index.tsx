import { Table, type TableProps } from 'antd';

/** 保留已声明的列宽；空列表不生成无内容的横向滚动区域。 */
export default function ReadableTable<T extends object>(props: TableProps<T>) {
  const empty = !props.dataSource?.length;
  const columnWidth = (props.columns ?? []).reduce(
    (total, column) => total + (typeof column.width === 'number' ? column.width : 160),
    props.rowSelection ? 48 : 0,
  );
  const scroll = empty
    ? undefined
    : props.scroll
      ? {
          ...props.scroll,
          x: props.scroll.x === 'max-content'
            ? columnWidth
            : typeof props.scroll.x === 'number'
              ? Math.max(props.scroll.x, columnWidth)
              : props.scroll.x,
        }
      : undefined;
  return <Table<T> {...props} scroll={scroll} showHeader={empty ? false : props.showHeader} />;
}

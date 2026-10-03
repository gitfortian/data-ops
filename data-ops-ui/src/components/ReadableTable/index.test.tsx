import { fireEvent, render, screen } from '@testing-library/react';
import ReadableTable from './index';

test('空列表只显示空态，不留下超宽表头和滚动表格', () => {
  const { container } = render(<ReadableTable columns={[{ title: '名称', width: 3000 }]} dataSource={[]} scroll={{ x: 3000 }} locale={{ emptyText: '暂无数据产品' }} />);
  expect(screen.getByText('暂无数据产品')).toBeInTheDocument();
  expect(screen.queryByRole('columnheader')).not.toBeInTheDocument();
  expect(container.querySelector('.ant-table-content')).not.toHaveStyle({ overflowX: 'auto' });
});

test('记录保留声明的列宽、右侧操作及点击能力', () => {
  const onOpen = jest.fn();
  const { container } = render(<ReadableTable rowKey="id" dataSource={[{ id: '1', name: '业务规则' }]} scroll={{ x: 'max-content' }} columns={[
    { title: '名称', dataIndex: 'name', width: 260 },
    { title: '操作', width: 160, fixed: 'right', render: () => <button onClick={onOpen}>查看</button> },
  ]} pagination={false} />);
  expect(screen.getByRole('columnheader', { name: '名称' })).toBeInTheDocument();
  expect(container.querySelector('table')).toHaveStyle({ width: '420px' });
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  expect(onOpen).toHaveBeenCalledTimes(1);
});

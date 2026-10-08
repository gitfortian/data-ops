import { fireEvent, render, screen } from '@testing-library/react';
import QueryClarificationCard from './QueryClarificationCard';

const fields = [
  { fieldId: 'paid', displayName: '实付金额', dataType: 'DECIMAL', role: 'MEASURE', description: '<script>raw()</script>' },
  { fieldId: 'due', displayName: '应付金额', dataType: 'DECIMAL', role: 'MEASURE', description: '' },
];
const question = JSON.stringify({ question: '统计实付还是应付？', options: ['实付金额（fieldId=paid）', '应付金额（fieldId=due）'],
  queryContext: { kind: 'FIELD', datasetId: 9, versionNo: 3, fields, truncated: true } });

it('shows source fields as text and only answers on an explicit choice or free-text action', () => {
  const onAnswer = jest.fn(); const { container } = render(<QueryClarificationCard question={question} disabled={false} onAnswer={onAnswer} />);
  expect(screen.getByText(/发现版本 v3/)).toBeInTheDocument();
  expect(screen.getByText(/字段发现范围已截断/)).toBeInTheDocument();
  expect(screen.getByText('<script>raw()</script>')).toBeInTheDocument(); expect(container.querySelector('script')).toBeNull();
  expect(onAnswer).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '实付金额（fieldId=paid）' }));
  expect(onAnswer).toHaveBeenLastCalledWith('实付金额（fieldId=paid）');
  fireEvent.change(screen.getByLabelText('补充问数信息'), { target: { value: '  按支付时间，含退款  ' } });
  fireEvent.click(screen.getByRole('button', { name: '回 答' }));
  expect(onAnswer).toHaveBeenLastCalledWith('按支付时间，含退款');
});

it('locks all answering controls when the original turn or permission blocks them', () => {
  const onAnswer = jest.fn(); const { rerender } = render(<QueryClarificationCard question={question} disabled={false} onAnswer={onAnswer} />);
  fireEvent.change(screen.getByLabelText('补充问数信息'), { target: { value: '保留的补充' } });
  rerender(<QueryClarificationCard question={question} disabled onAnswer={onAnswer} />);
  expect(screen.getByLabelText('补充问数信息')).toBeDisabled();
  screen.getAllByRole('button').forEach(button => expect(button).toBeDisabled());
  fireEvent.click(screen.getByText('实付金额（fieldId=paid）')); expect(onAnswer).not.toHaveBeenCalled();
});

it('rejects broken source and oversize free answers without showing raw JSON', () => {
  const onAnswer = jest.fn(); const { rerender } = render(<QueryClarificationCard question={question} disabled={false} onAnswer={onAnswer} />);
  fireEvent.change(screen.getByLabelText('补充问数信息'), { target: { value: 'x'.repeat(2001) } });
  expect(screen.getByRole('button', { name: '回 答' })).toBeDisabled();
  rerender(<QueryClarificationCard question="{private broken" disabled={false} onAnswer={onAnswer} />);
  expect(screen.getByText(/待答问题暂无法核对/)).toBeInTheDocument(); expect(screen.queryByText('{private broken')).not.toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument(); expect(onAnswer).not.toHaveBeenCalled();
});

it.each(['TIME', 'CALIBER'])('labels %s suggestions without treating them as source definitions', kind => {
  const q = JSON.stringify({ question: '请确认业务范围', options: ['本月'], queryContext: { kind, datasetId: 9, versionNo: 3, fields, truncated: false } });
  render(<QueryClarificationCard question={q} disabled={false} onAnswer={jest.fn()} />);
  expect(screen.getByText(/AI 提供的待确认选项/)).toBeInTheDocument(); expect(screen.getByText(/不修改源定义/)).toBeInTheDocument();
});

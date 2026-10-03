import { act, renderHook, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { getQualityMonitorReport } from '@/services/data-quality';
import { useMonitorDetailPage } from './useMonitorDetailPage';

jest.mock('@/services/data-quality', () => ({
  getQualityMonitorWorkspace: jest.fn().mockResolvedValue({}),
  getQualityMonitorReport: jest.fn().mockResolvedValue({}),
  runQualityMonitor: jest.fn().mockResolvedValue({ executionNo: 'QM-test' }),
  getQualityExecutionStatus: jest.fn().mockResolvedValue({ executionStatus: 'SUCCESS' }),
}));

test('从历史日期测试运行后，报告切换并加载本次执行日期', async () => {
  const { result } = renderHook(() => useMonitorDetailPage('monitor-1'));
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.reportDate).toBe(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
  await act(async () => { await result.current.run(); });
  const today = dayjs().format('YYYY-MM-DD');
  expect(result.current.reportDate).toBe(today);
  expect(getQualityMonitorReport).toHaveBeenCalledWith('monitor-1', today);
});

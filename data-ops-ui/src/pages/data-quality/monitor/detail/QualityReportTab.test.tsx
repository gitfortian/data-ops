import React from 'react';
import { render, screen } from '@testing-library/react';
import QualityReportTab from './QualityReportTab';
import type { MonitorReportView } from '../../types';

const report: MonitorReportView = {
  reportDate: '2026-10-03', trendStartDate: '2026-09-27',
  overview: { totalRules: 1, enabledRules: 1, executedRules: 0, issueRules: 0, errorRules: 0, passRate: 0 },
  dimensions: [{ dimension: '完整性', total: 0, passed: 0, notPassed: 0, errors: 0, passRate: 0 }],
  trend: [], columns: [],
};

it('shows an unexecuted report without claiming zero percent passed', () => {
  render(<QualityReportTab report={report} loading={false} reportDate={report.reportDate} onDateChange={() => {}} />);
  expect(screen.queryByText('0%')).not.toBeInTheDocument();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
  expect(screen.getByText('当日未执行')).toBeInTheDocument();
});

it('keeps a measured zero percent result distinct from no execution', () => {
  render(<QualityReportTab report={{ ...report, overview: { ...report.overview, executedRules: 1 },
    dimensions: [{ ...report.dimensions[0], total: 1, notPassed: 1 }] }}
    loading={false} reportDate={report.reportDate} onDateChange={() => {}} />);
  expect(screen.getByText('0%')).toBeInTheDocument();
  expect(screen.getByText('0.0%')).toBeInTheDocument();
});

import { render, screen } from '@testing-library/react';
import { Plus } from 'lucide-react';

import YakButton from './index';

describe('YakButton', () => {
  it('keeps the Ant Design button API while adding the Yak style class', () => {
    render(
      <YakButton type="primary" icon={<Plus data-testid="button-icon" />}>
        创建数据源
      </YakButton>,
    );

    const button = screen.getByRole('button', { name: /创建数据源/ });
    expect(button).toHaveClass('yak-button', 'ant-btn-primary');
    expect(screen.getByTestId('button-icon')).toBeInTheDocument();
  });

  it('uses the square icon-only treatment and native loading state', () => {
    render(
      <YakButton iconOnly loading aria-label="刷新" />,
    );

    const button = screen.getByRole('button', { name: '刷新' });
    expect(button).toHaveClass('yak-button--icon-only', 'ant-btn-loading');
  });
});

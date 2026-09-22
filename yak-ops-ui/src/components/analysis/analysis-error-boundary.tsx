import YakButton from '@/components/YakButton';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** Changes clear a previous render failure without remounting healthy query state. */
  resetKey?: string;
}

interface State {
  error?: Error;
}

/**
 * Keep a single malformed chart option/spec from taking down the whole Dashboard canvas.
 * Dataset/query errors continue to use the normal AnalysisPreview error state; this boundary
 * is only for unexpected render-time failures.
 */
export class AnalysisErrorBoundary extends Component<Props, State> {
  state: State = {};

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (process.env.NODE_ENV !== 'production') {
      console.error('[BI] Analysis render failed', error, info.componentStack);
    }
  }

  componentDidUpdate(previous: Props) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.setState({ error: undefined });
    }
  }

  private retry = () => this.setState({ error: undefined });

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="flex h-full min-h-[120px] items-center justify-center px-5 py-6 text-center">
        <div className="max-w-[320px]">
          <div className="mx-auto flex h-9 w-9 items-center justify-center rounded-[9px] bg-[#fff4f2] text-[#b42318]">
            <AlertTriangle size={16} />
          </div>
          <div className="mt-2 text-[12px] font-medium text-[#344054]">图表渲染失败</div>
          <div className="mt-1 text-[10px] leading-4 text-[#98a2b3]">
            当前组件出现了未预期的渲染异常，其他图表不会受到影响。
          </div>
          <YakButton
            size="small"
            effect="glass"
            type="text"
            className="mt-3 !h-7 !rounded-[6px] !text-[#667085]"
            icon={<RotateCcw size={11} />}
            onClick={this.retry}
          >
            重试
          </YakButton>
        </div>
      </div>
    );
  }
}

import {
  ScreenRenderer,
  type ScreenRendererProps,
} from '@/components/screen-engine';
import type { ScreenRuntimeState } from './model';

export type ScreenRuntimeProps = Omit<ScreenRendererProps, 'data'> & {
  runtime: Pick<ScreenRuntimeState, 'data'>;
};

/** Digital Screen runtime rendering boundary. Viewer code should depend on this, not ScreenRenderer. */
export function ScreenRuntime({ runtime, ...rendererProps }: ScreenRuntimeProps) {
  return <ScreenRenderer {...rendererProps} data={runtime.data} />;
}

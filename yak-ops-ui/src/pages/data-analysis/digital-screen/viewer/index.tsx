import { YakButton } from '@/components/ui';
import { history, useParams } from '@umijs/max';
import { ArrowLeft, Pencil } from 'lucide-react';
import { ScreenRuntime } from '../runtime/ScreenRuntime';
import { useDigitalScreenViewer } from './hooks/useDigitalScreenViewer';

export default function DigitalScreenViewerPage() {
  const { id } = useParams<{ id: string }>();
  const { screen, template, runtime, loadError, dataError, isLoading } = useDigitalScreenViewer(id);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#070b13] text-[13px] text-white/50">
        正在加载数字化大屏...
      </div>
    );
  }

  if (!screen || !template) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-[#070b13] text-white/70">
        <div className="text-[14px]">{loadError || '数字化大屏尚未发布或模板不存在'}</div>
        <YakButton type="link" onClick={() => history.push('/digital-screen')}>返回大屏列表</YakButton>
      </div>
    );
  }

  const ratio = template.width / template.height;
  const runtimeErrors = Object.values(runtime.errors);

  return (
    <div className="group relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[#070b13]">
      <div
        className="max-h-screen max-w-screen"
        style={{ width: `min(100vw, calc(100vh * ${ratio}))` }}
      >
        <ScreenRuntime template={template} runtime={runtime} />
      </div>

      <div className="absolute left-4 top-4 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
        <YakButton
          className="border-white/10 bg-black/45 text-white backdrop-blur"
          icon={<ArrowLeft size={14} />}
          onClick={() => history.push('/digital-screen')}
        >
          返回
        </YakButton>
        <YakButton
          className="border-white/10 bg-black/45 text-white backdrop-blur"
          icon={<Pencil size={14} />}
          onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
        >
          编辑
        </YakButton>
      </div>

      {runtime.loadingCount ? (
        <div className="absolute right-4 top-4 rounded-[4px] bg-black/40 px-2.5 py-1.5 text-[10px] text-white/55 backdrop-blur">
          正在刷新 {runtime.loadingCount} 个组件的数据...
        </div>
      ) : null}

      {dataError || runtimeErrors.length ? (
        <div className="absolute bottom-4 left-1/2 max-w-[560px] -translate-x-1/2 rounded-[6px] bg-black/55 px-3 py-2 text-[10px] leading-5 text-white/65 opacity-0 backdrop-blur transition-opacity group-hover:opacity-100">
          {dataError || `${runtimeErrors.length} 个组件的数据查询失败：${runtimeErrors[0]}`}
        </div>
      ) : null}

      <div className="absolute bottom-3 right-4 rounded-[4px] bg-black/35 px-2 py-1 text-[10px] text-white/35 opacity-0 transition-opacity group-hover:opacity-100">
        {screen.name} · V{screen.publishedVersionNo}
      </div>
    </div>
  );
}

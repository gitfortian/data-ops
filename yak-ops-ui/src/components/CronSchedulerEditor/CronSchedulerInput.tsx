import YakButton from '@/components/YakButton';
import { ConfigProvider, Input } from 'antd';
import { ChevronDown } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import { createPortal } from 'react-dom';

import CronSchedulerEditor from './index';

const MEGA_NAV_ANIMATION_MS = 750;
const VIEWPORT_GAP = 16;
const PANEL_GAP = 8;
const DEFAULT_PANEL_WIDTH = 760;
/**
 * Ant Design 的 Select / TimePicker 等浮层默认 z-index 为 1050。
 * Mega Panel 必须略低于它，否则这些挂到 body 的 popup 会被面板本身遮住。
 */
const MEGA_PANEL_Z_INDEX = 1040;

interface PanelPosition {
  top: number;
  left: number;
  width: number;
  maxHeight: number;
}

export interface CronSchedulerInputProps {
  value?: string;
  onChange?: (cronExpression: string) => void;
  disabled?: boolean;
  placeholder?: string;
  status?: 'error' | 'warning';
  className?: string;
  panelWidth?: number;
}

function isAntdFloatingLayer(target: EventTarget | null) {
  if (!(target instanceof Element)) return false;

  return Boolean(
    target.closest(
      [
        '.ant-select-dropdown',
        '.ant-picker-dropdown',
        '.ant-dropdown',
        '.ant-popover',
        '.ant-tooltip',
      ].join(', '),
    ),
  );
}

/**
 * 用 Input 触发 CronSchedulerEditor 的大尺寸浮层。
 *
 * 浮层通过 Portal 挂到 body，避免被 EditorSection 的 overflow-hidden 裁剪；
 * 展开动画沿用 yak-ops-website Mega Nav 的 0fr -> 1fr 方案。
 */
export default function CronSchedulerInput({
  value = '',
  onChange,
  disabled = false,
  placeholder = '0 0 2 * * ?',
  status,
  className,
  panelWidth = DEFAULT_PANEL_WIDTH,
}: CronSchedulerInputProps) {
  const triggerRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<number>();
  const animationFrameRef = useRef<number>();
  const openRef = useRef(false);
  const originCronRef = useRef(value);
  const draftCronRef = useRef(value);

  const [displayCron, setDisplayCron] = useState(value);
  const [draftCron, setDraftCron] = useState(value);
  const [mounted, setMounted] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [panelPosition, setPanelPosition] = useState<PanelPosition>({
    top: 0,
    left: 0,
    width: panelWidth,
    maxHeight: 560,
  });

  const updatePanelPosition = useCallback(() => {
    const trigger = triggerRef.current;
    if (!trigger || typeof window === 'undefined') return;

    const rect = trigger.getBoundingClientRect();
    const availableWidth = Math.max(0, window.innerWidth - VIEWPORT_GAP * 2);
    const width = Math.min(panelWidth, availableWidth);
    const left = Math.max(
      VIEWPORT_GAP,
      Math.min(rect.left, window.innerWidth - width - VIEWPORT_GAP),
    );
    const top = rect.bottom + PANEL_GAP;
    const maxHeight = Math.max(
      220,
      Math.min(620, window.innerHeight - top - VIEWPORT_GAP),
    );

    setPanelPosition({ top, left, width, maxHeight });
  }, [panelWidth]);

  const startCloseAnimation = useCallback(() => {
    openRef.current = false;
    setExpanded(false);

    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current);
    }

    closeTimerRef.current = window.setTimeout(() => {
      setMounted(false);
    }, MEGA_NAV_ANIMATION_MS);
  }, []);

  const commitAndClose = useCallback(() => {
    const nextCron = draftCronRef.current.trim();

    draftCronRef.current = nextCron;
    setDraftCron(nextCron);
    setDisplayCron(nextCron);
    onChange?.(nextCron);
    startCloseAnimation();
  }, [onChange, startCloseAnimation]);

  const cancelAndClose = useCallback(() => {
    const originalCron = originCronRef.current;

    draftCronRef.current = originalCron;
    setDraftCron(originalCron);
    setDisplayCron(originalCron);
    onChange?.(originalCron);
    startCloseAnimation();
  }, [onChange, startCloseAnimation]);

  const openPanel = useCallback(() => {
    if (disabled || openRef.current) return;

    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current);
    }
    if (animationFrameRef.current) {
      window.cancelAnimationFrame(animationFrameRef.current);
    }

    const currentCron = displayCron;
    originCronRef.current = currentCron;
    draftCronRef.current = currentCron;
    setDraftCron(currentCron);
    openRef.current = true;
    setMounted(true);
    updatePanelPosition();

    animationFrameRef.current = window.requestAnimationFrame(() => {
      updatePanelPosition();
      setExpanded(true);
    });
  }, [disabled, displayCron, updatePanelPosition]);

  const updateDraftCron = useCallback(
    (nextCron: string) => {
      draftCronRef.current = nextCron;
      setDraftCron(nextCron);
      setDisplayCron(nextCron);
      onChange?.(nextCron);
    },
    [onChange],
  );

  useEffect(() => {
    if (!openRef.current) {
      draftCronRef.current = value;
      setDraftCron(value);
      setDisplayCron(value);
    }
  }, [value]);

  useEffect(() => {
    if (!mounted) return undefined;

    const handleViewportChange = () => updatePanelPosition();
    window.addEventListener('resize', handleViewportChange);
    window.addEventListener('scroll', handleViewportChange, true);

    return () => {
      window.removeEventListener('resize', handleViewportChange);
      window.removeEventListener('scroll', handleViewportChange, true);
    };
  }, [mounted, updatePanelPosition]);

  useEffect(() => {
    if (!expanded) return undefined;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node | null;

      if (
        (target && triggerRef.current?.contains(target)) ||
        (target && panelRef.current?.contains(target)) ||
        isAntdFloatingLayer(event.target)
      ) {
        return;
      }

      startCloseAnimation();
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        cancelAndClose();
      }
    };

    document.addEventListener('pointerdown', handlePointerDown, true);
    document.addEventListener('keydown', handleKeyDown, true);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown, true);
      document.removeEventListener('keydown', handleKeyDown, true);
    };
  }, [cancelAndClose, expanded, startCloseAnimation]);

  useEffect(
    () => () => {
      if (closeTimerRef.current) {
        window.clearTimeout(closeTimerRef.current);
      }
      if (animationFrameRef.current) {
        window.cancelAnimationFrame(animationFrameRef.current);
      }
    },
    [],
  );

  const panel = mounted && typeof document !== 'undefined'
    ? createPortal(
        <div
          aria-hidden={!expanded}
          style={{
            position: 'fixed',
            top: panelPosition.top,
            left: panelPosition.left,
            width: panelPosition.width,
            zIndex: MEGA_PANEL_Z_INDEX,
            pointerEvents: expanded ? 'auto' : 'none',
          }}
        >
          <div
            className={[
              'grid origin-top transition-[grid-template-rows,opacity,transform]',
              'duration-[750ms] ease-[cubic-bezier(0.16,1,0.3,1)]',
              expanded
                ? 'grid-rows-[1fr] translate-y-0 opacity-100'
                : 'grid-rows-[0fr] -translate-y-1 opacity-0',
            ].join(' ')}
          >
            <div className="min-h-0 overflow-hidden">
              <div
                ref={panelRef}
                role="dialog"
                aria-label="Cron 调度配置"
                className="overflow-y-auto rounded-[12.5px] border border-[#eaecf0] bg-white text-[#161823] shadow-[0_12px_36px_rgba(16,24,40,0.14)]"
                style={{ maxHeight: panelPosition.maxHeight }}
              >
                <div className="border-b border-[#f0f1f3] px-6 py-4">
                  <div className="text-[14px] font-semibold text-[#161823]">
                    Cron 调度配置
                  </div>
                </div>

                <div className="px-6 py-5">
                  <ConfigProvider componentSize="small" variant="filled">
                    <CronSchedulerEditor
                      value={draftCron}
                      onChange={updateDraftCron}
                      showTimezoneTip={false}
                      showEffectiveDate={false}
                    />
                  </ConfigProvider>
                </div>

                <div className="sticky bottom-0 flex items-center justify-between gap-3 border-t border-[#f0f1f3] bg-white/95 px-6 py-3 backdrop-blur">
                  <span className="text-[11px] text-[#98a2b3]">
                    
                  </span>
                  <div className="flex shrink-0 items-center gap-2">
                    <YakButton onClick={cancelAndClose}>取消</YakButton>
                    <YakButton type="primary" onClick={commitAndClose}>
                      确认
                    </YakButton>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>,
        document.body,
      )
    : null;

  return (
    <>
      <div ref={triggerRef} className={['relative', className || ''].join(' ')}>
        <Input
          allowClear
          variant="filled"
          value={displayCron}
          disabled={disabled}
          placeholder={placeholder}
          status={status}
          aria-haspopup="dialog"
          aria-expanded={expanded}
          suffix={
            <ChevronDown
              size={14}
              className={[
                'text-[#98a2b3] transition-transform duration-[750ms]',
                'ease-[cubic-bezier(0.16,1,0.3,1)]',
                expanded ? 'rotate-180' : 'rotate-0',
              ].join(' ')}
            />
          }
          onFocus={openPanel}
          onClick={openPanel}
          onChange={(event) => updateDraftCron(event.target.value)}
          onPressEnter={() => {
            if (openRef.current) commitAndClose();
          }}
        />
      </div>
      {panel}
    </>
  );
}

import { YakButton } from '@/components/ui';
import type {
  DataServiceAccessOverviewItem,
  DataServiceConsumer,
} from '@/services/data-service';
import { Pencil, Trash2 } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import ConsumerApiAccessPanel from './ConsumerApiAccessPanel';
import ConsumerIpAccessPanel from './ConsumerIpAccessPanel';
import ConsumerKeyPanel from './ConsumerKeyPanel';

const SECTION_ITEMS = [
  { key: 'consumer-basic', label: '基本信息' },
  { key: 'consumer-keys', label: 'API Key' },
  { key: 'consumer-apis', label: 'API 权限' },
  { key: 'consumer-ip', label: '来源限制' },
] as const;

type SectionKey = (typeof SECTION_ITEMS)[number]['key'];

interface ConsumerManagementPanelProps {
  consumer: DataServiceConsumer;
  apis: DataServiceAccessOverviewItem[];
  onEdit: () => void;
  onDelete: () => void;
  onRefresh: () => void;
  onConsumerChanged: (next: DataServiceConsumer) => void;
}

interface InfoFieldProps {
  label: string;
  children: React.ReactNode;
}

function InfoField({ label, children }: InfoFieldProps) {
  return (
    <div className="grid grid-cols-[116px_minmax(0,1fr)] items-start gap-5 max-md:grid-cols-1 max-md:gap-2">
      <div className="pt-0.5 text-[13px] font-medium text-[#344054]">
        {label}
      </div>
      <div className="min-w-0 text-[13px] leading-6 text-[#667085]">
        {children}
      </div>
    </div>
  );
}

interface SectionNavigatorProps {
  activeKey: SectionKey;
  onSelect: (key: SectionKey) => void;
}

function SectionNavigator({ activeKey, onSelect }: SectionNavigatorProps) {
  return (
    <nav className="rounded-xl bg-white px-3 py-4">
      <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">
        快速定位
      </div>
      <div className="relative">
        <span className="absolute bottom-4 left-[13px] top-4 w-px bg-[#e4e7ec]" />
        <div className="space-y-1">
          {SECTION_ITEMS.map((item) => {
            const active = activeKey === item.key;
            return (
              <button
                key={item.key}
                type="button"
                className={[
                  'group relative flex w-full items-center gap-3 rounded-lg border-0 px-2 py-2 text-left transition-colors',
                  active
                    ? 'bg-[rgba(254,44,85,0.08)]'
                    : 'bg-transparent hover:bg-[#f7f8fa]',
                ].join(' ')}
                onClick={() => onSelect(item.key)}
              >
                <span
                  className={[
                    'relative z-10 h-[11px] w-[11px] shrink-0 rounded-full border transition-all duration-200',
                    active
                      ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color)] shadow-[0_0_0_3px_rgba(254,44,85,0.12)]'
                      : 'border-[#d0d5dd] bg-[#98a2b3]',
                  ].join(' ')}
                />
                <span
                  className={[
                    'text-[12px] leading-5 transition-colors',
                    active
                      ? 'font-semibold text-[var(--yak-brand-color)]'
                      : 'font-normal text-[#667085] group-hover:text-[#344054]',
                  ].join(' ')}
                >
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

export default function ConsumerManagementPanel({
  consumer,
  apis,
  onEdit,
  onDelete,
  onRefresh,
  onConsumerChanged,
}: ConsumerManagementPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const locatingRef = useRef<SectionKey | null>(null);
  const locateTimerRef = useRef<number>();
  const [activeSection, setActiveSection] = useState<SectionKey>('consumer-basic');

  const updateActiveSection = useCallback(() => {
    const container = scrollRef.current;
    if (!container || locatingRef.current) return;

    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    if (maxScrollTop - container.scrollTop <= 16) {
      setActiveSection('consumer-ip');
      return;
    }

    const threshold = container.getBoundingClientRect().top + 110;
    let next: SectionKey = SECTION_ITEMS[0].key;

    SECTION_ITEMS.forEach((item) => {
      const element = document.getElementById(item.key);
      if (element && element.getBoundingClientRect().top <= threshold) {
        next = item.key;
      }
    });

    setActiveSection(next);
  }, []);

  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return undefined;

    let frame = 0;
    const handleScroll = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(updateActiveSection);
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('resize', handleScroll);
    updateActiveSection();

    return () => {
      window.cancelAnimationFrame(frame);
      container.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleScroll);
    };
  }, [updateActiveSection]);

  useEffect(() => () => {
    if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
  }, []);

  const locate = (key: SectionKey) => {
    const container = scrollRef.current;
    const element = document.getElementById(key);
    if (!container || !element) return;

    if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
    locatingRef.current = key;
    setActiveSection(key);

    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    const expectedTop = container.scrollTop + elementRect.top - containerRect.top - 20;

    container.scrollTo({
      top: key === 'consumer-ip'
        ? Math.min(expectedTop, maxScrollTop)
        : Math.max(0, Math.min(expectedTop, maxScrollTop)),
      behavior: 'smooth',
    });

    locateTimerRef.current = window.setTimeout(() => {
      locatingRef.current = null;
      updateActiveSection();
    }, 500);
  };

  return (
    <div className="h-full overflow-hidden bg-[#f7f8fa] text-[#161823]">
      <div ref={scrollRef} className="h-full overflow-y-auto overscroll-contain scroll-smooth">
        <div className="mx-auto grid w-full max-w-[1060px] grid-cols-1 gap-5 px-5 pb-6 pt-5 lg:grid-cols-[minmax(0,1fr)_150px]">
          <main className="min-w-0 space-y-5">
            <section id="consumer-basic" className="scroll-mt-5 rounded-xl bg-white">
              <div className="flex items-center justify-between gap-3 px-7 pt-5">
                <h2 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
                  基本信息
                </h2>
                <YakButton type="text" icon={<Pencil size={14} />} onClick={onEdit}>
                  编辑
                </YakButton>
              </div>
              <div className="space-y-5 px-7 py-6">
                <InfoField label="调用方名称">{consumer.name}</InfoField>
                <InfoField label="说明">{consumer.description || '—'}</InfoField>
                <InfoField label="默认限流">{consumer.defaultRateLimitPerMinute} 次 / 分钟</InfoField>
                <InfoField label="状态">
                  <span className="inline-flex items-center gap-2">
                    <span className={`h-1.5 w-1.5 rounded-full ${consumer.enabled ? 'bg-[#20c77a]' : 'bg-[#b0b5bd]'}`} />
                    {consumer.enabled ? '可调用' : '已停用'}
                  </span>
                </InfoField>
              </div>
            </section>

            <div id="consumer-keys" className="scroll-mt-5">
              <ConsumerKeyPanel consumer={consumer} onChanged={onRefresh} />
            </div>

            <div id="consumer-apis" className="scroll-mt-5">
              <ConsumerApiAccessPanel
                consumer={consumer}
                apis={apis}
                onChanged={onConsumerChanged}
              />
            </div>

            <div id="consumer-ip" className="scroll-mt-5">
              <ConsumerIpAccessPanel consumer={consumer} onChanged={onRefresh} />
            </div>

            <div className="flex justify-end pb-2">
              <YakButton type="text" danger icon={<Trash2 size={14} />} onClick={onDelete}>
                删除调用方
              </YakButton>
            </div>
          </main>

          <aside className="hidden lg:block">
            <div className="sticky top-5">
              <SectionNavigator activeKey={activeSection} onSelect={locate} />
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

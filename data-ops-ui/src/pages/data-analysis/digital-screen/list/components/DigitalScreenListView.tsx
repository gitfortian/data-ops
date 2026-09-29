import { ScreenRenderer } from '@/components/screen-engine';
import { YakButton } from '@/components/ui';
import type { DigitalScreenInstance, DigitalScreenStatus } from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { Input, Popconfirm } from 'antd';
import { Copy, Eye, Monitor, Pencil, Plus, Search, Trash2 } from 'lucide-react';

export type DigitalScreenStatusFilter = 'all' | DigitalScreenStatus;

interface StatusItem {
  key: DigitalScreenStatusFilter;
  label: string;
  count: number;
}

interface DigitalScreenListViewProps {
  screens: DigitalScreenInstance[];
  filteredScreens: DigitalScreenInstance[];
  statusItems: StatusItem[];
  status: DigitalScreenStatusFilter;
  keyword: string;
  isLoading: boolean;
  onStatusChange: (status: DigitalScreenStatusFilter) => void;
  onKeywordChange: (keyword: string) => void;
  onResetFilters: () => void;
  onCreate: () => void;
  onEdit: (screen: DigitalScreenInstance) => void;
  onPreview: (screen: DigitalScreenInstance) => void;
  onDuplicate: (screen: DigitalScreenInstance) => void;
  onDelete: (screen: DigitalScreenInstance) => void;
}

const formatDateTime = (value: string) => value.replace('T', ' ').slice(0, 16);

function DigitalScreenEmptyIllustration() {
  return (
    <svg
      width="392"
      height="244"
      viewBox="0 0 392 244"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className="select-none"
    >
      <defs>
        <linearGradient id="ds-bg-panel" x1="92" y1="50" x2="300" y2="172">
          <stop stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#F7F9FC" />
        </linearGradient>
        <linearGradient id="ds-pink" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#FFA3B6" />
          <stop offset="1" stopColor="#FE2C55" />
        </linearGradient>
        <linearGradient id="ds-blue" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#9AD9FF" />
          <stop offset="1" stopColor="#4D88FF" />
        </linearGradient>
        <linearGradient id="ds-cyan" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#9CEDE7" />
          <stop offset="1" stopColor="#2BB8A8" />
        </linearGradient>
        <linearGradient id="ds-violet" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#C9BCFF" />
          <stop offset="1" stopColor="#8B6EFF" />
        </linearGradient>
        <filter id="ds-main-shadow" x="52" y="24" width="288" height="180" filterUnits="userSpaceOnUse">
          <feDropShadow dx="0" dy="14" stdDeviation="16" floodColor="#1F2937" floodOpacity="0.08" />
        </filter>
        <filter id="ds-card-shadow" x="0" y="0" width="392" height="244" filterUnits="userSpaceOnUse">
          <feDropShadow dx="0" dy="7" stdDeviation="8" floodColor="#1F2937" floodOpacity="0.07" />
        </filter>
      </defs>

      <circle cx="62" cy="66" r="4" fill="#DDE7FF" />
      <circle cx="328" cy="64" r="4" fill="#FBDDE4" />
      <circle cx="74" cy="191" r="4" fill="#EAE1FF" />
      <circle cx="320" cy="190" r="4" fill="#DDF5EC" />
      <circle cx="105" cy="44" r="2.5" fill="#D9E8FF" />
      <circle cx="291" cy="44" r="2.5" fill="#FFE2E9" />
      <path d="M56 107H70" stroke="#D8DEE8" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M63 100V114" stroke="#D8DEE8" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M322 107H336" stroke="#D8DEE8" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M329 100V114" stroke="#D8DEE8" strokeWidth="1.6" strokeLinecap="round" />

      <g filter="url(#ds-main-shadow)">
        <rect x="84" y="44" width="224" height="128" rx="22" fill="url(#ds-bg-panel)" />
        <rect x="84.75" y="44.75" width="222.5" height="126.5" rx="21.25" stroke="#E6E9EF" strokeWidth="1.5" />
        <circle cx="101" cy="61" r="3" fill="#FE2C55" fillOpacity="0.55" />
        <circle cx="112" cy="61" r="3" fill="#F5C451" fillOpacity="0.75" />
        <circle cx="123" cy="61" r="3" fill="#58C38E" fillOpacity="0.75" />
        <rect x="140" y="57.5" width="60" height="7" rx="3.5" fill="#E7EAF0" />
        <rect x="208" y="57.5" width="26" height="7" rx="3.5" fill="#F0F2F5" />
        <rect x="240" y="57.5" width="26" height="7" rx="3.5" fill="#F0F2F5" />
        <rect x="99" y="79" width="44" height="78" rx="13" fill="#FBFCFD" stroke="#ECEFF3" />
        <rect x="108" y="90" width="18" height="5" rx="2.5" fill="#D8DDE5" />
        <rect x="108" y="101" width="28" height="7" rx="3.5" fill="url(#ds-pink)" />
        <rect x="108" y="119" width="26" height="6" rx="3" fill="#EEF2F7" />
        <rect x="108" y="130" width="20" height="6" rx="3" fill="#E4EAF2" />
        <rect x="108" y="141" width="24" height="6" rx="3" fill="#EEF2F7" />
        <rect x="151" y="79" width="102" height="53" rx="14" fill="#FBFCFD" stroke="#ECEFF3" />
        <path d="M163 118H241" stroke="#EFF2F6" strokeWidth="1.2" strokeLinecap="round" />
        <path d="M163 106H241" stroke="#F2F4F7" strokeWidth="1.2" strokeLinecap="round" />
        <path d="M164 117C173 111 180 115 188 107C196 99 204 106 212 96C220 87 229 95 239 88" stroke="url(#ds-blue)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="239" cy="88" r="3.5" fill="#4D88FF" />
        <rect x="151" y="140" width="48" height="17" rx="8.5" fill="#EEF8F7" />
        <rect x="204" y="140" width="49" height="17" rx="8.5" fill="#F0EEFF" />
        <rect x="261" y="79" width="31" height="35" rx="10" fill="#FFF7F9" stroke="#F7E9ED" />
        <rect x="268" y="91" width="6" height="15" rx="3" fill="#FFD1DA" />
        <rect x="278" y="85" width="6" height="21" rx="3" fill="url(#ds-pink)" />
        <rect x="261" y="121" width="31" height="36" rx="10" fill="#F7FBFF" stroke="#E8F0FF" />
        <circle cx="276.5" cy="139" r="10.5" stroke="#D9E7FF" strokeWidth="6" />
        <path d="M276.5 128.5A10.5 10.5 0 1 1 269 147" stroke="url(#ds-blue)" strokeWidth="6" strokeLinecap="round" />
      </g>

      <g filter="url(#ds-card-shadow)">
        <rect x="45" y="99" width="58" height="58" rx="16" fill="#FFFFFF" />
        <rect x="45.75" y="99.75" width="56.5" height="56.5" rx="15.25" stroke="#E6E9EF" strokeWidth="1.5" />
        <path d="M74 113L82 118L84 128L78 138L68 141L60 135L58 124L65 116Z" fill="#F7FBFF" stroke="#D9E8FF" strokeWidth="1.5" />
        <path d="M74 118L79 121L80.5 128L76.5 134L69.5 136L64 132L63 125.5L67.5 120Z" fill="url(#ds-cyan)" fillOpacity="0.16" stroke="url(#ds-cyan)" strokeWidth="1.5" />
        <circle cx="74" cy="128" r="2.5" fill="#2BB8A8" />
      </g>
      <g filter="url(#ds-card-shadow)">
        <rect x="309" y="93" width="40" height="64" rx="14" fill="#FFFFFF" />
        <rect x="309.75" y="93.75" width="38.5" height="62.5" rx="13.25" stroke="#E6E9EF" strokeWidth="1.5" />
        <rect x="319" y="104" width="20" height="4" rx="2" fill="#E7EAF0" />
        <path d="M318 141C322 136 326 142 330 136C334 130 337 134 340 126" stroke="url(#ds-violet)" strokeWidth="2.5" strokeLinecap="round" />
        <circle cx="340" cy="126" r="2.6" fill="#8B6EFF" />
      </g>
      <g filter="url(#ds-card-shadow)">
        <rect x="150" y="185" width="92" height="22" rx="11" fill="#FFFFFF" />
        <rect x="150.75" y="185.75" width="90.5" height="20.5" rx="10.25" stroke="#E6E9EF" strokeWidth="1.5" />
        <circle cx="164" cy="196" r="4" fill="#FE2C55" fillOpacity="0.72" />
        <rect x="173" y="193" width="24" height="6" rx="3" fill="#DCE1E8" />
        <rect x="201" y="193" width="30" height="6" rx="3" fill="#EEF2F6" />
      </g>
    </svg>
  );
}

function DigitalScreenEmptyState() {
  return (
    <div className="flex min-h-[470px] flex-col items-center justify-center pb-8 pt-2 text-center">
      <DigitalScreenEmptyIllustration />
      <div className="mt-1 text-[15px] font-semibold leading-6 text-[#30333B]">还没有数字化大屏</div>
      <div className="mt-1.5 text-[12px] leading-5 text-[#8A9099]">
        做一块更有氛围感的数据大屏，把重点信息直接铺开展示。
      </div>
    </div>
  );
}

function DigitalScreenFilterEmptyState({ onReset }: { onReset: () => void }) {
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center pb-8 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-[14px] bg-[#F5F6F7] text-[#98A2B3]">
        <Monitor size={22} strokeWidth={1.7} />
      </div>
      <div className="mt-3 text-[13px] font-medium text-[#667085]">没有匹配的大屏</div>
      <div className="mt-1 text-[12px] text-[#A3A8B0]">换个关键词或状态试试。</div>
      <YakButton type="link" onClick={onReset} className="mt-1 h-8 px-2 text-[12px]">
        清空筛选
      </YakButton>
    </div>
  );
}

export function DigitalScreenListView({
  screens,
  filteredScreens,
  statusItems,
  status,
  keyword,
  isLoading,
  onStatusChange,
  onKeywordChange,
  onResetFilters,
  onCreate,
  onEdit,
  onPreview,
  onDuplicate,
  onDelete,
}: DigitalScreenListViewProps) {
  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#F6F7F8]">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <div className="flex items-center justify-between gap-6">
          <div className="text-[18px] font-semibold leading-7 text-[#161823]">数字化大屏</div>
          <YakButton type="primary" icon={<Plus size={15} />} onClick={onCreate}>新建大屏</YakButton>
        </div>

        <div className="mt-4 flex min-h-[48px] flex-wrap items-center justify-between gap-3 border-b border-[#eceef1] pb-3">
          <div className="flex items-center gap-1">
            {statusItems.map((item) => {
              const isActive = status === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => onStatusChange(item.key)}
                  className={[
                    'h-8 rounded-[6px] border-0 px-3 text-[13px] transition-colors',
                    isActive
                      ? 'bg-[#f0f1f2] font-semibold text-[#161823]'
                      : 'bg-transparent text-[#8a9099] hover:bg-[#f7f8f9] hover:text-[#444950]',
                  ].join(' ')}
                >
                  {item.label}
                  <span className="ml-1 text-[11px] font-normal text-[#a3a8b0]">{item.count}</span>
                </button>
              );
            })}
          </div>
          <Input
            allowClear
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索大屏"
            className="w-[220px]"
            variant="filled"
          />
        </div>

        {isLoading && screens.length === 0 ? (
          <div className="flex min-h-[470px] items-center justify-center text-[13px] text-[#98A2B3]">正在加载数字化大屏...</div>
        ) : filteredScreens.length === 0 ? (
          keyword.trim() || status !== 'all'
            ? <DigitalScreenFilterEmptyState onReset={onResetFilters} />
            : <DigitalScreenEmptyState />
        ) : (
          <div className="grid grid-cols-1 gap-x-5 gap-y-6 pt-5 xl:grid-cols-2 2xl:grid-cols-3">
            {filteredScreens.map((screen) => {
              const template = resolveScreenTemplateById(screen.templateId);
              return (
                <article key={screen.id} className="group overflow-hidden rounded-[8px] border border-[#e7e9ec] bg-white transition-[border-color,background-color] hover:border-[#d8dce1]">
                  <button type="button" onClick={() => onEdit(screen)} className="relative block w-full overflow-hidden border-0 bg-[#111827] p-0 text-left">
                    {template ? (
                      <ScreenRenderer template={template} className="pointer-events-none" />
                    ) : (
                      <div className="flex aspect-video items-center justify-center text-[12px] text-white/60">模板不存在</div>
                    )}
                    <div className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/20 group-hover:opacity-100">
                      <span className="flex h-8 items-center gap-1.5 rounded-[6px] bg-white/95 px-3 text-[12px] font-medium text-[#161823]">
                        <Pencil size={13} /> 编辑
                      </span>
                    </div>
                  </button>

                  <div className="px-4 py-3.5">
                    <div className="flex min-w-0 items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <button type="button" onClick={() => onEdit(screen)} className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold text-[#161823] hover:underline">
                          {screen.name}
                        </button>
                        <div className="mt-1 flex items-center gap-2 text-[11px] text-[#98a2b3]">
                          <span>{template?.name || '未知模板'}</span>
                          <span className="text-[#d7dade]">·</span>
                          <span>{formatDateTime(screen.updatedAt)}</span>
                        </div>
                      </div>
                      <span className={[
                        'shrink-0 rounded-[4px] px-2 py-1 text-[11px] font-medium',
                        screen.status === 'published' ? 'bg-[#edf8f2] text-[#27845a]' : 'bg-[#f4f5f6] text-[#7b818a]',
                      ].join(' ')}>
                        {screen.status === 'published' ? '已发布' : '草稿'}
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-end gap-1 border-t border-[#f0f1f2] pt-2.5">
                      <YakButton type="text" size="small" icon={<Eye size={13} />} onClick={() => onPreview(screen)}>预览</YakButton>
                      <YakButton type="text" size="small" icon={<Copy size={13} />} onClick={() => onDuplicate(screen)}>复制</YakButton>
                      <Popconfirm
                        title="删除大屏"
                        description={`确定删除“${screen.name}”吗？`}
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() => onDelete(screen)}
                      >
                        <YakButton type="text" size="small" danger icon={<Trash2 size={13} />}>删除</YakButton>
                      </Popconfirm>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

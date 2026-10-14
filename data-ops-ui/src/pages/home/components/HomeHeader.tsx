import { useIntl } from '@umijs/max';
import { Database } from 'lucide-react';

export function HomeHeader() {
  const intl = useIntl();

  return (
    <header className="flex min-h-[100px] items-center px-4 py-4">
      <div className="flex min-w-0 items-center">
        <div className="flex h-[66px] w-[66px] shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/90 bg-gradient-to-br from-[#dde6ef] via-[#a6c8e2] to-[#5e93d4] text-white/95 shadow-[0_2px_4px_rgba(31,35,41,0.04)]">
          <Database size={30} strokeWidth={1.5} />
        </div>

        <div className="ml-4 min-w-0">
          <div className="flex min-h-[22px] flex-wrap items-center gap-y-1">
            <span className="whitespace-nowrap text-sm font-medium leading-[22px] text-[#252830]">
              Data Ops
            </span>
            <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />
            <span className="text-sm font-normal leading-[22px] text-[#667085]">
              {intl.formatMessage({ id: 'pages.home.header.tagline' })}
            </span>
          </div>
        </div>
      </div>
    </header>
  );
}

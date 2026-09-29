import type { HomeOverviewMetric } from '../../types';

export function OverviewMetrics({
  metrics,
}: {
  metrics: HomeOverviewMetric[];
}) {
  return (
    <div className="mt-1 grid grid-cols-2 gap-x-2 gap-y-1 sm:grid-cols-3 lg:grid-cols-6 lg:gap-x-1">
      {metrics.map((metric) => (
        <div
          key={metric.label}
          className="group min-w-0 rounded-[6px] px-3 py-2 transition-colors duration-150 hover:bg-[#f7f8fa] lg:px-2 lg:py-1.5"
        >
          <div className="text-[12px] font-semibold leading-5 text-[#454951]">
            {metric.label}
          </div>
          <div className="mt-0.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <strong className="text-[20px] font-semibold leading-7 tracking-[-0.4px] text-[#272a33]">
              {metric.value}
            </strong>
            <span className="text-[11px] text-[#989ca4]">
              {metric.compareLabel}
              <span
                className={`ml-0.5 font-medium ${
                  metric.tone === 'positive'
                    ? 'text-[#20a464]'
                    : metric.tone === 'negative'
                      ? 'text-[#f04c5a]'
                      : 'text-[#7b8089]'
                }`}
              >
                {metric.compareValue}
              </span>
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

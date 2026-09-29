import type { ReactNode } from 'react';

export interface YakFilterSwitchOption<Value extends string = string> {
  value: Value;
  label: ReactNode;
}

export interface YakFilterSwitchProps<Value extends string = string> {
  value: Value;
  options: readonly YakFilterSwitchOption<Value>[];
  onChange: (value: Value) => void;
  className?: string;
}

/**
 * Compact choice group for list filters.
 *
 * Unlike a content tab or Ant Design Segmented, the control has no shared
 * track. Only the selected option is surfaced, which keeps dense filter bars
 * lightweight while preserving clear state feedback.
 */
export default function YakFilterSwitch<Value extends string = string>({
  value,
  options,
  onChange,
  className,
}: YakFilterSwitchProps<Value>) {
  return (
    <div
      role="group"
      className={[
        'inline-flex h-9 shrink-0 items-center gap-1',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {options.map((option) => {
        const active = option.value === value;

        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={active}
            className={[
              'flex h-8 items-center justify-center rounded-[8px] px-3.5 text-[13px] leading-none',
              'transition-[background-color,color] duration-150 ease-out',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,0.14)]',
              active
                ? 'bg-[#f2f3f5] font-semibold text-[#242731]'
                : 'bg-transparent font-medium text-[#777c86] hover:bg-[#f7f8fa] hover:text-[#3f444e]',
            ].join(' ')}
            onClick={() => {
              if (!active) onChange(option.value);
            }}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

import { getRealtimeStatusStyle } from '../utils';

interface RealtimeSyncStatusBadgeProps {
  state?: string;
  label?: string;
}

const RealtimeSyncStatusBadge = ({
  state,
  label,
}: RealtimeSyncStatusBadgeProps) => {
  const style = getRealtimeStatusStyle(state);

  return (
    <span
      className="inline-flex h-6 items-center gap-1.5 rounded-full border px-2 text-[11px] font-medium"
      style={{
        color: style.text,
        background: style.background,
        borderColor: style.border,
      }}
    >
      <span
        className="h-1.5 w-1.5 rounded-full"
        style={{ background: style.dot }}
      />
      {label || state || '-'}
    </span>
  );
};

export default RealtimeSyncStatusBadge;

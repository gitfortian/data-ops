import { Link } from '@umijs/max';

export default function BrandLogo({ compact }: { compact: boolean }) {
  return (
    <Link
      to="/home"
      aria-label="返回首页"
      className={[
        'mt-3 mb-1.5 flex h-12 w-full items-center border-0 bg-transparent',
        'transition-all duration-200',
        compact ? 'justify-center px-0' : 'justify-start px-5',
      ].join(' ')}
    >
      <img
        src="/dataops-logo.svg"
        alt="DataOps"
        draggable={false}
        className="block h-7 w-11 shrink-0 select-none object-contain"
      />
      {!compact && (
        <span aria-hidden="true" className="ml-2 flex min-w-0 items-center gap-1.5">
          <span className="text-[21px] font-bold tracking-tight text-[#17171b]">DataOps</span>
          <span className="whitespace-nowrap rounded bg-[#17171b] px-1 py-0.5 text-[10px] font-medium leading-none text-white">
            一体化
          </span>
        </span>
      )}
    </Link>
  );
}

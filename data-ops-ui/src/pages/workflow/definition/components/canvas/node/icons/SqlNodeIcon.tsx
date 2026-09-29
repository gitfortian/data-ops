import type { SVGProps } from 'react';

const SqlNodeIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    {...props}
  >
    <ellipse cx="12" cy="6.5" rx="7" ry="3" stroke="currentColor" strokeWidth="1.6" />
    <path
      d="M5 6.5v5c0 1.65 3.13 3 7 3s7-1.35 7-3v-5"
      stroke="currentColor"
      strokeWidth="1.6"
    />
    <path
      d="M5 11.5v5c0 1.65 3.13 3 7 3 1.1 0 2.14-.11 3.06-.32"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
    <path
      d="m16.5 15.5 2 2 2.5-3"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export default SqlNodeIcon;

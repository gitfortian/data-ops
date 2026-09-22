import { history } from '@umijs/max';
import React from 'react';

const NoFoundPage: React.FC = () => {
  return (
    <main className="flex min-h-screen items-center justify-center bg-white px-6">
      <div className="flex items-center text-[#5f6975]">
        <span className="pr-4 text-[24px] font-normal leading-none">
          404
        </span>

        <span className="h-8 w-px bg-[#d8dde3]" />

        <div className="pl-4">
          <p className="m-0 text-[16px] font-normal">
            Not Found
          </p>

          <button
            type="button"
            onClick={() => history.push('/')}
            className="mt-2 border-0 bg-transparent p-0 text-[12px] text-[#9aa3ad] transition-colors hover:text-[#5f6975]"
          >
            返回首页
          </button>
        </div>
      </div>
    </main>
  );
};

export default NoFoundPage;
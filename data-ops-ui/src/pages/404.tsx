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
            页面不存在
          </p>

          <button
            type="button"
            onClick={() => history.push('/home')}
            className="mt-3 rounded border border-[#d0d5dd] bg-white px-3 py-2 text-[13px] text-[#344054] transition-colors hover:bg-[#f2f4f7] focus-visible:outline-2 focus-visible:outline-[#475467]"
          >
            返回首页
          </button>
        </div>
      </div>
    </main>
  );
};

export default NoFoundPage;

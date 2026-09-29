import { history, useSearchParams } from '@umijs/max';
import { Button } from 'antd';
import { ArrowLeft } from 'lucide-react';

import LineageWorkspace from './LineageWorkspace';

/** Only accept same-app absolute paths; never turn returnTo into an open redirect. */
export const safeLineageReturnPath = (value?: string | null) => {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return undefined;
  return value;
};

export default function LineageEntry() {
  const [searchParams] = useSearchParams();
  const returnTo = safeLineageReturnPath(searchParams.get('returnTo'));

  return (
    <>
      <LineageWorkspace />
      {returnTo ? (
        <Button
          size="small"
          icon={<ArrowLeft size={13} />}
          className="!fixed !bottom-5 !left-5 !z-[1100] !h-8 !shadow-sm"
          onClick={() => history.push(returnTo)}
        >
          返回数据开发
        </Button>
      ) : null}
    </>
  );
}

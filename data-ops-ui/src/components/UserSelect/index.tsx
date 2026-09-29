import { Select, message } from 'antd';
import { useCallback, useMemo, useRef, useState } from 'react';

import { pageUsers, type SystemUser } from '@/services/security/users';

interface UserOption {
  value: string;
  label: string;
}

const toOption = (user: SystemUser): UserOption => ({
  value: user.userName,
  label: user.realName?.trim() ? `${user.realName}（${user.userName}）` : user.userName,
});

/**
 * 审批人多选(107):能选择就不填——按用户名/姓名远程搜索系统用户。
 * v1 后端不校验用户存在性,故用 tags 模式允许手输历史账号(仍可保存)。
 */
const UserSelect = ({
  value,
  onChange,
  placeholder = '搜索并选择审批人',
  max = 10,
}: {
  value?: string[];
  onChange?: (value: string[]) => void;
  placeholder?: string;
  max?: number;
}) => {
  const [options, setOptions] = useState<UserOption[]>([]);
  const [fetching, setFetching] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const search = useCallback(async (keyword: string) => {
    setFetching(true);
    try {
      const result = await pageUsers({
        pageNum: 1,
        pageSize: 20,
        userName: keyword || undefined,
      });
      setOptions((result.records ?? []).map(toOption));
    } catch {
      message.error('用户列表加载失败');
    } finally {
      setFetching(false);
    }
  }, []);

  const onSearch = useCallback(
    (keyword: string) => {
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => void search(keyword), 300);
    },
    [search],
  );

  const fallback = useMemo<UserOption[]>(
    () =>
      (value ?? [])
        .filter((name) => !options.some((option) => option.value === name))
        .map((name) => ({ value: name, label: name })),
    [value, options],
  );

  return (
    <Select
      mode="tags"
      showSearch
      allowClear
      value={value}
      placeholder={placeholder}
      filterOption={false}
      loading={fetching}
      onSearch={onSearch}
      onFocus={() => void search('')}
      onChange={(next: string[]) => onChange?.(next.slice(0, max))}
      options={[...options, ...fallback]}
      tokenSeparators={[',', ' ']}
      className="w-full"
    />
  );
};

export default UserSelect;

import dayjs from 'dayjs';

export const getSystemErrorMessage = (
  error: unknown,
  fallback: string,
): string =>
  error instanceof Error && error.message
    ? error.message
    : fallback;

export const formatSystemDateTime = (value?: string): string => {
  if (!value) return '-';

  const date = dayjs(value);
  return date.isValid()
    ? date.format('YYYY-MM-DD HH:mm:ss')
    : value;
};

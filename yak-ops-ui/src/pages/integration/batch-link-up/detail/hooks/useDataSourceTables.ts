import { useCallback, useEffect, useState } from 'react';

import { dataSourceCatalogApi } from '@/services/data-source/legacy';

const TABLE_SEARCH_DEBOUNCE_MS = 250;
const TABLE_SEARCH_LIMIT = 100;

interface UseDataSourceTablesOptions {
  includeViews?: boolean;
}

export const normalizeTableNames = (
  data: any,
  includeViews = true,
): string[] => {
  const values = Array.isArray(data)
    ? data
    : Array.isArray(data?.bizData)
      ? data.bizData
      : Array.isArray(data?.records)
        ? data.records
        : [];

  return Array.from(
    new Set(
      values
        .filter((item: any) => {
          if (includeViews || typeof item === 'string') return true;
          return String(item?.type || '').trim().toUpperCase() !== 'VIEW';
        })
        .map((item: any) =>
          typeof item === 'string'
            ? item
            : item?.name || item?.value || item?.label,
        )
        .filter(Boolean)
        .map(String),
    ),
  );
};

/** Loads a bounded table/collection window and delegates filtering to the backend. */
export default function useDataSourceTables(
  dataSourceId: string,
  database?: string,
  options: UseDataSourceTablesOptions = {},
) {
  const [tables, setTables] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const includeViews = options.includeViews !== false;

  useEffect(() => {
    setKeyword('');
    setTables([]);
  }, [dataSourceId, database]);

  useEffect(() => {
    if (!dataSourceId) {
      setTables([]);
      setLoading(false);
      return undefined;
    }

    let active = true;
    setLoading(true);

    const timer = window.setTimeout(() => {
      dataSourceCatalogApi
        .searchTables(
          dataSourceId,
          keyword.trim() || undefined,
          TABLE_SEARCH_LIMIT,
          database?.trim() || undefined,
        )
        .then((response) => {
          if (!active) return;
          setTables(normalizeTableNames(response?.data, includeViews));
        })
        .catch(() => {
          if (active) setTables([]);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, TABLE_SEARCH_DEBOUNCE_MS);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [dataSourceId, database, includeViews, keyword]);

  const search = useCallback((value: string) => {
    setKeyword(value);
  }, []);

  return { tables, loading, search };
}

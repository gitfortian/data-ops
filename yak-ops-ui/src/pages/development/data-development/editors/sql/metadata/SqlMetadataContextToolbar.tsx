import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { useIntl } from '@umijs/max';
import { Popover, Spin, message } from 'antd';
import { ChevronDown, Database, Layers3, Search, Server } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import {
  getDevelopmentSqlDialectLabel,
  sqlDialectMatchesDataSource,
} from '../../../sqlDatabaseProfiles';
import type { DevelopmentId } from '../../../types';
import {
  enrichSqlDataSourceContext,
  selectSqlDatabaseContext,
  selectSqlDataSourceContext,
  selectSqlSchemaContext,
  useSqlMetadataContext,
} from './sqlMetadataContextStore';
import {
  getSqlDataSourceBinding,
  listSqlDatabases,
  listSqlDataSources,
  listSqlSchemas,
  type SqlDataSourceBinding,
  type SqlDataSourceOption,
} from './sqlMetadataService';
import {
  resolveSqlEffectiveDatabase,
  resolveSqlEffectiveSchema,
  uniqueSqlContextValues,
} from './sqlTaskContextConfig';

interface SqlMetadataContextToolbarProps {
  nodeId: DevelopmentId;
}

interface ContextPickerItem {
  value: string;
  label: string;
  searchText?: string;
  icon?: ReactNode;
}

interface ContextPickerProps {
  ariaLabel: string;
  value?: string;
  displayValue?: string;
  placeholder: string;
  icon: ReactNode;
  items: ContextPickerItem[];
  loading?: boolean;
  disabled?: boolean;
  invalid?: boolean;
  disabledReason?: string;
  invalidReason?: string;
  popupWidth?: number;
  minWidthClassName?: string;
  onSelect: (value: string) => void;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const ContextPicker = ({
  ariaLabel,
  value,
  displayValue,
  placeholder,
  icon,
  items,
  loading = false,
  disabled = false,
  invalid = false,
  disabledReason,
  invalidReason,
  popupWidth = 210,
  minWidthClassName = 'min-w-[108px]',
  onSelect,
}: ContextPickerProps) => {
  const intl = useIntl();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const normalizedKeyword = keyword.trim().toLowerCase();

  const filteredItems = useMemo(
    () =>
      normalizedKeyword
        ? items.filter((item) =>
            `${item.label} ${item.searchText || ''}`
              .toLowerCase()
              .includes(normalizedKeyword),
          )
        : items,
    [items, normalizedKeyword],
  );

  const popup = (
    <div style={{ width: popupWidth }}>
      <div className="flex h-8 items-center gap-1.5 border-b border-[#e5e7eb] px-2.5">
        <Search size={13} strokeWidth={1.8} className="shrink-0 text-[#6b7280]" />
        <input
          autoFocus
          value={keyword}
          placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.search' })}
          onChange={(event) => setKeyword(event.target.value)}
          className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[12px] text-[#30323b] outline-none placeholder:text-[#9ca3af]"
        />
      </div>

      <div className="max-h-[240px] overflow-y-auto p-1">
        {loading ? (
          <div className="flex h-10 items-center justify-center">
            <Spin size="small" />
          </div>
        ) : filteredItems.length ? (
          filteredItems.map((item) => {
            const selected = item.value === value;
            return (
              <button
                key={item.value}
                type="button"
                title={item.label}
                onClick={() => {
                  onSelect(item.value);
                  setOpen(false);
                  setKeyword('');
                }}
                className={[
                  'flex h-8 w-full items-center gap-2 rounded-[2px] px-2 text-left text-[12px] transition-colors hover:bg-[#f5f5f6]',
                  selected ? 'text-[#161823]' : 'text-[#30323b]',
                ].join(' ')}
              >
                <span className="flex h-4 w-4 shrink-0 items-center justify-center">
                  {item.icon || icon}
                </span>
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
              </button>
            );
          })
        ) : (
          <div className="flex h-10 items-center justify-center px-3 text-center text-[11px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.noMatch' })}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <Popover
      trigger="click"
      placement="bottomLeft"
      arrow={false}
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled) return;
        setOpen(nextOpen);
        if (!nextOpen) setKeyword('');
      }}
      overlayClassName="sql-metadata-context-popover"
      content={popup}
    >
      <button
        type="button"
        aria-label={ariaLabel}
        title={invalid ? invalidReason : disabled ? disabledReason : undefined}
        disabled={disabled}
        className={[
          'flex h-6 max-w-[190px] items-center gap-1.5 rounded-[3px] border px-1.5 text-[12px] outline-none transition-colors',
          minWidthClassName,
          invalid
            ? 'border-[#fda29b] bg-[#fff4f2] text-[#b42318]'
            : disabled
              ? 'cursor-not-allowed border-transparent bg-[#f5f6f7] text-[#a4a9b2]'
              : open || displayValue
                ? 'border-transparent bg-[#f1f2f4] text-[#161823]'
                : 'border-transparent text-[#30323b] hover:bg-[#f5f5f6]',
        ].join(' ')}
      >
        <span className="flex h-4 w-4 shrink-0 items-center justify-center">{icon}</span>
        <span
          className={[
            'min-w-0 flex-1 truncate text-left',
            invalid
              ? 'text-[#b42318]'
              : disabled
                ? 'text-[#8f959f]'
                : displayValue
                  ? 'text-[#30323b]'
                  : 'text-[#7b808a]',
          ].join(' ')}
        >
          {displayValue || placeholder}
        </span>
        {!disabled ? (
          <ChevronDown
            size={12}
            strokeWidth={1.8}
            className={[
              'shrink-0 transition-transform duration-150',
              open ? 'rotate-180' : '',
            ].join(' ')}
          />
        ) : null}
      </button>
    </Popover>
  );
};

const SqlMetadataContextToolbar = ({
  nodeId,
}: SqlMetadataContextToolbarProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const context = useSqlMetadataContext(nodeId);
  const [dataSources, setDataSources] = useState<SqlDataSourceOption[]>([]);
  const [dataSourceLoading, setDataSourceLoading] = useState(false);
  const [dataSourceResolved, setDataSourceResolved] = useState(false);
  const [binding, setBinding] = useState<SqlDataSourceBinding>({});
  const [bindingLoading, setBindingLoading] = useState(false);
  const [databases, setDatabases] = useState<string[]>([]);
  const [databaseLoading, setDatabaseLoading] = useState(false);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [schemaLoading, setSchemaLoading] = useState(false);

  const text = (id: string) => intlRef.current.formatMessage({ id });

  useEffect(() => {
    let active = true;
    setDataSourceLoading(true);
    setDataSourceResolved(false);
    listSqlDataSources()
      .then((values) => {
        if (!active) return;
        setDataSources(values || []);
        setDataSourceResolved(true);
      })
      .catch((error) => {
        if (active) {
          message.error(
            errorText(
              error,
              text('pages.dataDevelopment.editor.sqlMetadata.queryDataSourceFailed'),
            ),
          );
        }
      })
      .finally(() => {
        if (active) setDataSourceLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const compatibleDataSources = useMemo(
    () =>
      dataSources.filter((item) =>
        sqlDialectMatchesDataSource(context.dialect, item.dbType),
      ),
    [context.dialect, dataSources],
  );

  const selectedDataSource = useMemo(
    () => dataSources.find((item) => item.value === context.dataSourceId),
    [context.dataSourceId, dataSources],
  );
  const dataSourceInvalid = Boolean(
    dataSourceResolved &&
      context.dataSourceId &&
      (!selectedDataSource ||
        !sqlDialectMatchesDataSource(context.dialect, selectedDataSource.dbType)),
  );

  useEffect(() => {
    if (!context.dataSourceId || !selectedDataSource || dataSourceInvalid) return;
    enrichSqlDataSourceContext(nodeId, {
      id: selectedDataSource.value,
      name: selectedDataSource.label,
    });
  }, [
    context.dataSourceId,
    dataSourceInvalid,
    nodeId,
    selectedDataSource,
  ]);

  useEffect(() => {
    let active = true;
    setBinding({});
    if (!context.dataSourceId || dataSourceInvalid) {
      setBindingLoading(false);
      return () => {
        active = false;
      };
    }

    setBindingLoading(true);
    getSqlDataSourceBinding(context.dataSourceId)
      .then((value) => {
        if (active) setBinding(value || {});
      })
      .catch((error) => {
        if (!active) return;
        message.error(
          errorText(
            error,
            text('pages.dataDevelopment.editor.sqlMetadata.bindingFailed'),
          ),
        );
      })
      .finally(() => {
        if (active) setBindingLoading(false);
      });

    return () => {
      active = false;
    };
  }, [context.dataSourceId, dataSourceInvalid]);

  useEffect(() => {
    let active = true;
    setDatabases([]);
    if (!context.dataSourceId || dataSourceInvalid) {
      setDatabaseLoading(false);
      return () => {
        active = false;
      };
    }

    setDatabaseLoading(true);
    listSqlDatabases(context.dataSourceId)
      .then((values) => {
        if (active) setDatabases(values || []);
      })
      .catch((error) => {
        if (!active) return;
        message.error(
          errorText(
            error,
            text('pages.dataDevelopment.editor.sqlMetadata.queryDatabaseFailed'),
          ),
        );
      })
      .finally(() => {
        if (active) setDatabaseLoading(false);
      });

    return () => {
      active = false;
    };
  }, [context.dataSourceId, dataSourceInvalid]);

  const effectiveDatabase = resolveSqlEffectiveDatabase(
    context.database,
    binding.database,
  );
  const effectiveSchema = resolveSqlEffectiveSchema(context.schema, binding.schema);

  const normalizedDbType = (
    selectedDataSource?.dbType || context.dbType
  )?.trim().toUpperCase();
  const showSchemaPicker = Boolean(
    context.dataSourceId &&
      normalizedDbType &&
      !['MYSQL', 'TIDB', 'GOLDENDB', 'DORIS', 'STARROCKS'].includes(
        normalizedDbType,
      ),
  );

  useEffect(() => {
    let active = true;
    setSchemas([]);
    if (
      !showSchemaPicker ||
      !context.dataSourceId ||
      dataSourceInvalid
    ) {
      setSchemaLoading(false);
      return () => {
        active = false;
      };
    }

    setSchemaLoading(true);
    listSqlSchemas(context.dataSourceId, effectiveDatabase)
      .then((values) => {
        if (active) setSchemas(values || []);
      })
      .catch((error) => {
        if (!active) return;
        message.error(
          errorText(
            error,
            text('pages.dataDevelopment.editor.sqlMetadata.querySchemaFailed'),
          ),
        );
      })
      .finally(() => {
        if (active) setSchemaLoading(false);
      });

    return () => {
      active = false;
    };
  }, [
    context.dataSourceId,
    dataSourceInvalid,
    effectiveDatabase,
    showSchemaPicker,
  ]);

  const dataSourceItems = compatibleDataSources.map((item) => ({
    value: item.value,
    label: `@${item.label}`,
    searchText: item.dbType,
    icon: (
      <Server
        size={13}
        strokeWidth={1.8}
        className="text-[var(--yak-brand-color)]"
      />
    ),
  }));

  const databaseItems = uniqueSqlContextValues([
    context.database,
    binding.database,
    ...databases,
  ]).map((value) => ({
    value,
    label:
      !context.database && binding.database === value
        ? intl.formatMessage(
            { id: 'pages.dataDevelopment.editor.sqlMetadata.connectionDefault' },
            { value },
          )
        : value,
  }));

  const schemaItems = uniqueSqlContextValues([
    context.schema,
    binding.schema,
    ...schemas,
  ]).map((value) => ({
    value,
    label:
      !context.schema && binding.schema === value
        ? intl.formatMessage(
            { id: 'pages.dataDevelopment.editor.sqlMetadata.connectionDefault' },
            { value },
          )
        : value,
  }));

  const databasePlaceholder = !context.dataSourceId
    ? '<database>'
    : bindingLoading
      ? intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.loading' })
      : intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.selectDatabase' });
  const schemaPlaceholder = bindingLoading
    ? intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.loading' })
    : intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.selectSchema' });
  const dataSourcePlaceholder =
    context.dialect === 'GENERIC'
      ? '@datasource'
      : `@${getDevelopmentSqlDialectLabel(context.dialect)}`;
  const dataSourceDisplayValue = dataSourceInvalid
    ? `@${context.dataSourceName || context.dataSourceId}`
    : context.dataSourceName
      ? `@${context.dataSourceName}`
      : undefined;
  const databaseDisplayValue =
    !context.database && binding.database
      ? intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.sqlMetadata.connectionDefault' },
          { value: binding.database },
        )
      : effectiveDatabase;
  const schemaDisplayValue =
    !context.schema && binding.schema
      ? intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.sqlMetadata.connectionDefault' },
          { value: binding.schema },
        )
      : effectiveSchema;

  return (
    <>
      <div
        className="flex min-w-0 shrink-0 items-center gap-1"
        style={BRAND_CSS_VARIABLES}
      >
        <ContextPicker
          ariaLabel={intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.selectDataSource' })}
          value={context.dataSourceId}
          displayValue={dataSourceDisplayValue}
          placeholder={dataSourcePlaceholder}
          invalid={dataSourceInvalid}
          invalidReason={intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.dataSourceInvalid' })}
          icon={
            <Server
              size={13}
              strokeWidth={1.8}
              className="text-[var(--yak-brand-color)]"
            />
          }
          items={dataSourceItems}
          loading={dataSourceLoading}
          popupWidth={230}
          minWidthClassName="min-w-[108px]"
          onSelect={(value) => {
            const selected = compatibleDataSources.find((item) => item.value === value);
            if (!selected) return;
            selectSqlDataSourceContext(nodeId, {
              id: selected.value,
              name: selected.label,
              dbType: selected.dbType,
            });
          }}
        />

        <ContextPicker
          ariaLabel="Database"
          value={effectiveDatabase}
          displayValue={bindingLoading ? undefined : databaseDisplayValue}
          placeholder={databasePlaceholder}
          icon={<Database size={13} strokeWidth={1.8} className="text-[#8f959f]" />}
          items={databaseItems}
          loading={databaseLoading || bindingLoading}
          disabled={!context.dataSourceId || dataSourceInvalid}
          disabledReason={intl.formatMessage({
            id: dataSourceInvalid
              ? 'pages.dataDevelopment.editor.sqlMetadata.dataSourceInvalid'
              : 'pages.dataDevelopment.editor.sqlMetadata.selectDataSourceFirst',
          })}
          minWidthClassName="min-w-[112px]"
          onSelect={(value) => selectSqlDatabaseContext(nodeId, value)}
        />

        {showSchemaPicker ? (
          <ContextPicker
            ariaLabel="Schema"
            value={effectiveSchema}
            displayValue={bindingLoading ? undefined : schemaDisplayValue}
            placeholder={schemaPlaceholder}
            icon={<Layers3 size={13} strokeWidth={1.8} className="text-[#8f959f]" />}
            items={schemaItems}
            loading={schemaLoading || bindingLoading}
            disabled={!context.dataSourceId || dataSourceInvalid}
            disabledReason={intl.formatMessage({
              id: dataSourceInvalid
                ? 'pages.dataDevelopment.editor.sqlMetadata.dataSourceInvalid'
                : 'pages.dataDevelopment.editor.sqlMetadata.selectDataSourceFirst',
            })}
            minWidthClassName="min-w-[104px]"
            onSelect={(value) => selectSqlSchemaContext(nodeId, value)}
          />
        ) : null}
      </div>

      <style>{`
        .sql-metadata-context-popover .ant-popover-inner {
          padding: 0;
          overflow: hidden;
          border: 1px solid #dfe3e8;
          border-radius: 3px;
          box-shadow: 0 4px 12px rgba(16, 24, 40, 0.10);
        }
        .sql-metadata-context-popover .ant-popover-inner-content {
          padding: 0;
        }
      `}</style>
    </>
  );
};

export default SqlMetadataContextToolbar;

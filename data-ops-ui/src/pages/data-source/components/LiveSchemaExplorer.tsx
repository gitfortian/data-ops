import Table from '@/components/ReadableTable';
import { YakButton } from '@/components/ui';
import { listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import { listDataSourceColumns, searchDataSourceTables } from '@/services/data-source/catalog';
import type { DataSourceCatalogColumn } from '@/services/data-source/catalog';
import type { DataSourceCatalogTable } from '@/services/data-source/types';
import type { CollectionSourceScope } from '@/services/metadata/collectionHandoff';
import { Alert, Button, Input, Select, Spin, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { KeyRound, RefreshCw, Search, Table2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { LiveSourceCoordinate } from '@/utils/liveSourceOdsJourney';

const tableKey = (table: DataSourceCatalogTable) => JSON.stringify([table.database, table.schema, table.name]);

/** Source Catalog reads are independent from harvested Metadata; existing discovery caching remains in force. */
export default function LiveSchemaExplorer({
  sourceId,
  canRead,
  onScopeChange,
  onDefineTask,
  onOpenOds,
  initialTable,
  definitionLoading = false,
}: {
  sourceId: string;
  canRead: boolean;
  onScopeChange?: (scope: CollectionSourceScope) => void;
  onDefineTask?: (scope: CollectionSourceScope) => void;
  /** Modeling route access is checked by the parent and rechecked on the destination. */
  onOpenOds?: (source: LiveSourceCoordinate) => void;
  /** Exact source coordinates returned by ODS, never a source authorization. */
  initialTable?: { database: string; schema?: string; table: string };
  definitionLoading?: boolean;
}) {
  const [databases, setDatabases] = useState<string[]>([]);
  const [database, setDatabase] = useState<string | undefined>(initialTable?.database);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [schema, setSchema] = useState<string | undefined>(initialTable?.schema);
  const [databasesReady, setDatabasesReady] = useState(false);
  const [resolvedDatabases, setResolvedDatabases] = useState<{ sourceId: string; version: number } | null>(null);
  const [schemasReady, setSchemasReady] = useState(false);
  const [resolvedScope, setResolvedScope] = useState<{ sourceId: string; database?: string; version: number } | null>(
    null,
  );
  const [databaseError, setDatabaseError] = useState(false);
  const [schemaError, setSchemaError] = useState(false);
  const [version, setVersion] = useState(0);
  const [searchInput, setSearchInput] = useState(initialTable?.table ?? '');
  const [keyword, setKeyword] = useState(initialTable?.table ?? '');
  const restorePending = useRef(Boolean(initialTable));
  const [restoreWarning, setRestoreWarning] = useState('');
  const [tables, setTables] = useState<DataSourceCatalogTable[]>([]);
  const [tablesLoading, setTablesLoading] = useState(true);
  const [tableError, setTableError] = useState(false);
  const [selected, setSelected] = useState<DataSourceCatalogTable | null>(null);
  const [columns, setColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [columnsLoading, setColumnsLoading] = useState(false);
  const [columnError, setColumnError] = useState(false);
  const [columnVersion, setColumnVersion] = useState(0);
  const [page, setPage] = useState(1);

  useEffect(() => {
    onScopeChange?.({
      dataSourceId: sourceId,
      databaseName: selected?.database ?? database,
      schemaName: selected?.schema ?? schema,
      tableName: selected?.name,
    });
  }, [sourceId, database, schema, selected, onScopeChange]);

  useEffect(() => {
    if (!canRead) return;
    let active = true;
    setDatabasesReady(false);
    setResolvedDatabases(null);
    setSchemasReady(false);
    setResolvedScope(null);
    setDatabaseError(false);
    setSelected(null);
    setTables([]);
    setColumns([]);
    void listDataSourceDatabases(sourceId)
      .then((result) => {
        if (!active) return;
        setDatabases(result);
        setDatabase((current) => (current && result.includes(current) ? current : result[0]));
        setDatabasesReady(true);
        setResolvedDatabases({ sourceId, version });
      })
      .catch(() => {
        if (active) setDatabaseError(true);
      });
    return () => {
      active = false;
    };
  }, [sourceId, canRead, version]);

  useEffect(() => {
    if (
      !canRead ||
      !resolvedDatabases ||
      resolvedDatabases.sourceId !== sourceId ||
      resolvedDatabases.version !== version
    )
      return;
    let active = true;
    setSchemasReady(false);
    setResolvedScope(null);
    setSchemaError(false);
    setSelected(null);
    void listDataSourceSchemas(sourceId, database)
      .then((result) => {
        if (!active) return;
        setSchemas(result);
        setSchema((current) => (current && result.includes(current) ? current : undefined));
        setSchemasReady(true);
        setResolvedScope({ sourceId, database, version });
      })
      .catch(() => {
        if (active) setSchemaError(true);
      });
    return () => {
      active = false;
    };
  }, [sourceId, canRead, resolvedDatabases, database, version]);

  useEffect(() => {
    if (searchInput.trim() === keyword) return;
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput, keyword]);

  useEffect(() => {
    if (
      !canRead ||
      !resolvedScope ||
      resolvedScope.sourceId !== sourceId ||
      resolvedScope.database !== database ||
      resolvedScope.version !== version
    )
      return;
    let active = true;
    setTablesLoading(true);
    setTableError(false);
    setTables([]);
    setSelected(null);
    setPage(1);
    void searchDataSourceTables(sourceId, keyword || undefined, { database, schema, limit: 100 })
      .then((result) => {
        if (!active) return;
        if (
          result.some(
            (table) =>
              !table.name ||
              (database && table.database && table.database !== database) ||
              (schema && table.schema && table.schema !== schema),
          )
        ) {
          setTableError(true);
          return;
        }
        setTables(result);
        if (restorePending.current && initialTable) {
          restorePending.current = false;
          const exact = result.find((candidate) =>
            (candidate.database ?? database) === initialTable.database
            && (candidate.schema ?? schema ?? undefined) === (initialTable.schema ?? undefined)
            && candidate.name === initialTable.table);
          if (exact && database === initialTable.database && schema === initialTable.schema) {
            setSelected(exact);
            setRestoreWarning('');
          } else {
            setRestoreWarning('原来源表无法在当前项目及 Schema 精确核验，请重新选择源表；不会自动跳至同名表。');
          }
        }
      })
      .catch(() => {
        if (active) setTableError(true);
      })
      .finally(() => {
        if (active) setTablesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [sourceId, canRead, resolvedScope, database, schema, keyword, version]);

  useEffect(() => {
    if (!selected?.name || !canRead) return;
    let active = true;
    setColumnsLoading(true);
    setColumnError(false);
    setColumns([]);
    void listDataSourceColumns(sourceId, selected.database ?? database, selected.schema ?? schema, selected.name)
      .then((result) => {
        if (active) setColumns(result);
      })
      .catch(() => {
        if (active) setColumnError(true);
      })
      .finally(() => {
        if (active) setColumnsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [sourceId, canRead, selected, database, schema, columnVersion]);

  const tableColumns: ColumnsType<DataSourceCatalogTable> = [
    {
      title: '表 / 视图',
      key: 'name',
      render: (_, table) => (
        <div>
          <Button type="link" size="small" className="source-probe-table-link" onClick={() => { restorePending.current = false; setRestoreWarning(''); setSelected(table); }}>
            {table.name}
          </Button>
          <div className="source-workspace-scope">
            {[table.database ?? database, table.schema ?? schema].filter(Boolean).join(' / ')}
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      key: 'type',
      width: 90,
      render: (_, table) => (
        <Tag>{table.type === 'TABLE' ? '表' : table.type === 'VIEW' ? '视图' : table.type || '—'}</Tag>
      ),
    },
  ];
  const columnColumns: ColumnsType<DataSourceCatalogColumn> = [
    {
      title: '字段',
      dataIndex: 'name',
      width: 120,
      render: (name: string, column) => (
        <span className="source-probe-column-name">
          {column.primaryKey ? <KeyRound size={13} aria-label="主键" /> : null}
          {name}
        </span>
      ),
    },
    { title: '类型', key: 'type', width: 115, render: (_, column) => column.typeName || '—' },
    { title: '长度', dataIndex: 'size', width: 60, render: (size?: number) => size ?? '—' },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 60,
      render: (nullable?: boolean) => (nullable == null ? '—' : nullable ? '是' : '否'),
    },
    { title: '备注', dataIndex: 'remarks', render: (remarks?: string) => remarks || '—' },
  ];
  const loading = !resolvedScope || tablesLoading;
  const scopeError = databaseError || schemaError;
  const reProbe = () => {
    setDatabasesReady(false);
    setSchemasReady(false);
    setTables([]);
    setSelected(null);
    setVersion((value) => value + 1);
  };

  if (!canRead) return <Alert type="warning" showIcon message="当前没有数据源结构读取权限" />;
  return (
    <div className="source-probe">
      <div className="source-probe-toolbar">
        <label className="source-probe-filter">
          <span>数据库</span>
          <Select
            aria-label="探查数据库"
            showSearch
            optionFilterProp="label"
            value={database}
            loading={!databasesReady && !databaseError}
            placeholder={databasesReady ? '默认数据库' : '正在读取数据库'}
            options={databases.map((value) => ({ label: value, value }))}
            onChange={(value) => {
              setSchemasReady(false);
              setSchema(undefined);
              setSelected(null);
              restorePending.current = false;
              setRestoreWarning('');
              setDatabase(value);
            }}
          />
        </label>
        <label className="source-probe-filter">
          <span>Schema</span>
          <Select
            aria-label="探查 Schema"
            showSearch
            allowClear
            optionFilterProp="label"
            value={schema}
            loading={databasesReady && !schemasReady && !schemaError}
            disabled={!databasesReady || !schemasReady}
            placeholder="全部 Schema"
            options={schemas.map((value) => ({ label: value, value }))}
            onChange={(value) => {
              setSchema(value);
              setSelected(null);
              restorePending.current = false;
              setRestoreWarning('');
            }}
          />
        </label>
        <Button icon={<RefreshCw size={15} />} onClick={reProbe}>
          重新探查
        </Button>
        {onDefineTask ? (
          <YakButton
            type="primary"
            loading={definitionLoading}
            disabled={loading || scopeError || tableError}
            onClick={() =>
              onDefineTask({
                dataSourceId: sourceId,
                databaseName: selected?.database ?? database,
                schemaName: selected?.schema ?? schema,
                tableName: selected?.name,
              })
            }
          >
            配置采集
          </YakButton>
        ) : null}
      </div>
      {scopeError ? (
        <Alert
          type="error"
          showIcon
          message={databaseError ? '未能读取源库列表' : '未能读取 Schema 列表'}
          description="请检查连接或源库授权后重新探查。"
          action={<Button onClick={reProbe}>重试</Button>}
        />
      ) : (
        <>
          {restoreWarning ? <Alert type="warning" showIcon message={restoreWarning} /> : null}
          <div className="source-probe-search-row">
            <Input
              className="source-workspace-search"
              variant="borderless"
              allowClear
              prefix={<Search size={16} aria-hidden="true" />}
              aria-label="搜索源库表"
              placeholder="搜索源库表名"
              value={searchInput}
              onChange={(event) => {
                setSearchInput(event.target.value);
                if (!event.target.value) setKeyword('');
              }}
              onPressEnter={() => setKeyword(searchInput.trim())}
            />
            <span className="source-workspace-caption" aria-live="polite">
              {loading ? '正在探查结构…' : tableError ? '探查结果暂不可用' : `本次返回 ${tables.length} 张表 / 视图`}
            </span>
          </div>
          {tableError ? (
            <Alert
              type="error"
              showIcon
              message="源库结构探查失败"
              description="读取失败不代表没有表，请检查连接或源库授权。"
              action={<Button onClick={reProbe}>重试</Button>}
            />
          ) : (
            <Spin spinning={loading}>
              <div className="source-probe-grid">
                <div className="source-probe-list">
                  <Table
                    rowKey={tableKey}
                    size="small"
                    columns={tableColumns}
                    dataSource={tables}
                    rowClassName={(table) =>
                      selected && tableKey(selected) === tableKey(table) ? 'source-probe-selected' : ''
                    }
                    locale={{
                      emptyText: loading ? '正在读取…' : keyword ? '没有匹配的表' : '当前范围没有可读取的表或视图',
                    }}
                    pagination={{
                      current: page,
                      onChange: setPage,
                      pageSize: 8,
                      total: tables.length,
                      showSizeChanger: false,
                      hideOnSinglePage: true,
                    }}
                  />
                  {tables.length >= 100 ? (
                    <p className="source-workspace-caption">最多展示 100 条结果，请通过名称或 Schema 缩小范围。</p>
                  ) : null}
                </div>
                <div className="source-probe-detail">
                  {selected ? (
                    <>
                      <div className="source-probe-detail-heading">
                        <div>
                          <h3>{selected.name}</h3>
                          <p>{selected.remarks || '字段结构'}</p>
                        </div>
                        <Button
                          aria-label="重新读取所选表字段"
                          icon={<RefreshCw size={14} />}
                          onClick={() => setColumnVersion((value) => value + 1)}
                        />
                      </div>
                      <div className="mb-3 flex flex-wrap gap-2">
                        {selected.type === 'TABLE' && onOpenOds ? (
                          <Button
                            type="primary"
                            className="w-full"
                            disabled={columnsLoading || columnError || columns.length === 0
                              || !(selected.database ?? database)}
                            onClick={() => {
                              const sourceDatabase = selected.database ?? database;
                              if (!sourceDatabase) return;
                              onOpenOds({
                                dataSourceId: Number(sourceId),
                                database: sourceDatabase,
                                schema: selected.schema ?? schema,
                                table: selected.name,
                              });
                            }}
                          >
                            打开或创建 ODS
                          </Button>
                        ) : null}
                      </div>
                      {columnError ? (
                        <Alert
                          type="error"
                          showIcon
                          message="未能读取字段结构"
                          action={<Button onClick={() => setColumnVersion((value) => value + 1)}>重试</Button>}
                        />
                      ) : (
                        <Table
                          rowKey="name"
                          size="small"
                          loading={columnsLoading}
                          columns={columnColumns}
                          dataSource={columns}
                          scroll={{ x: 460 }}
                          pagination={false}
                          locale={{ emptyText: columnsLoading ? '正在读取字段…' : '没有返回字段结构' }}
                        />
                      )}
                    </>
                  ) : (
                    <div className="source-probe-placeholder">
                      <Table2 size={30} aria-hidden="true" />
                      <h3>选择一张表查看结构</h3>
                      <p>直接查看字段、类型、主键和备注。</p>
                    </div>
                  )}
                </div>
              </div>
            </Spin>
          )}
        </>
      )}
      <p className="source-probe-note">仅探查来源结构，不创建采集任务，也不写入元数据目录。</p>
    </div>
  );
}

import type { Dispatch, SetStateAction } from "react";
import { useCallback, useReducer } from "react";
import type { ColumnDraft, IndexDraft, PropertyDraft } from "./structureRules";

export interface StructureDraft {
  tableName: string;
  tableComment: string;
  rows: ColumnDraft[];
  primaryKey: string[];
  indexes: IndexDraft[];
  partitionEnabled: boolean;
  partitionType: string | undefined;
  partitionColumns: string[];
  partitionExpression: string;
  properties: PropertyDraft[];
  dirty: boolean;
}
const initialDraft: StructureDraft = {
  tableName: "",
  tableComment: "",
  rows: [],
  primaryKey: [],
  indexes: [],
  partitionEnabled: false,
  partitionType: undefined,
  partitionColumns: [],
  partitionExpression: "",
  properties: [],
  dirty: false,
};

export type DraftAction = {
  [K in keyof StructureDraft]: {
    field: K;
    value: SetStateAction<StructureDraft[K]>;
  };
}[keyof StructureDraft];

export function structureDraftReducer(
  state: StructureDraft,
  action: DraftAction
): StructureDraft {
  const previous = state[action.field];
  const value =
    typeof action.value === "function"
      ? action.value(previous as never)
      : action.value;
  return Object.is(previous, value)
    ? state
    : { ...state, [action.field]: value };
}

/** Structure draft is local to one editor; publication and remote snapshots remain separate. */
export function useModelStructureDraft() {
  const [draft, dispatch] = useReducer(structureDraftReducer, initialDraft);
  const setTableName: Dispatch<SetStateAction<string>> = useCallback(
    (value) => dispatch({ field: "tableName", value }),
    []
  );
  const setTableComment: Dispatch<SetStateAction<string>> = useCallback(
    (value) => dispatch({ field: "tableComment", value }),
    []
  );
  const setRows: Dispatch<SetStateAction<ColumnDraft[]>> = useCallback(
    (value) => dispatch({ field: "rows", value }),
    []
  );
  const setPrimaryKey: Dispatch<SetStateAction<string[]>> = useCallback(
    (value) => dispatch({ field: "primaryKey", value }),
    []
  );
  const setIndexes: Dispatch<SetStateAction<IndexDraft[]>> = useCallback(
    (value) => dispatch({ field: "indexes", value }),
    []
  );
  const setPartitionEnabled: Dispatch<SetStateAction<boolean>> = useCallback(
    (value) => dispatch({ field: "partitionEnabled", value }),
    []
  );
  const setPartitionType: Dispatch<SetStateAction<string | undefined>> =
    useCallback((value) => dispatch({ field: "partitionType", value }), []);
  const setPartitionColumns: Dispatch<SetStateAction<string[]>> = useCallback(
    (value) => dispatch({ field: "partitionColumns", value }),
    []
  );
  const setPartitionExpression: Dispatch<SetStateAction<string>> = useCallback(
    (value) => dispatch({ field: "partitionExpression", value }),
    []
  );
  const setProperties: Dispatch<SetStateAction<PropertyDraft[]>> = useCallback(
    (value) => dispatch({ field: "properties", value }),
    []
  );
  const setDirty: Dispatch<SetStateAction<boolean>> = useCallback(
    (value) => dispatch({ field: "dirty", value }),
    []
  );
  return {
    ...draft,
    setTableName,
    setTableComment,
    setRows,
    setPrimaryKey,
    setIndexes,
    setPartitionEnabled,
    setPartitionType,
    setPartitionColumns,
    setPartitionExpression,
    setProperties,
    setDirty,
  };
}

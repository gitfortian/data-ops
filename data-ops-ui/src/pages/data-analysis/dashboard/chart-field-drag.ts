import type { DragEvent } from 'react';
import { FIELD_DRAG_MIME } from './helpers';
import type { DatasetFieldRole } from './model';

export interface ChartFieldDragPayload {
  field: string;
  role: DatasetFieldRole;
}

export const writeChartFieldDragPayload = (
  event: DragEvent<HTMLElement>,
  payload: ChartFieldDragPayload,
) => {
  const serialized = JSON.stringify(payload);
  event.dataTransfer.effectAllowed = 'copy';
  event.dataTransfer.setData(FIELD_DRAG_MIME, serialized);
  event.dataTransfer.setData('text/plain', serialized);
};

export const readChartFieldDragPayload = (
  event: DragEvent<HTMLElement>,
): ChartFieldDragPayload | undefined => {
  const raw = event.dataTransfer.getData(FIELD_DRAG_MIME)
    || event.dataTransfer.getData('text/plain');
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw) as Partial<ChartFieldDragPayload>;
    if (
      typeof parsed.field !== 'string'
      || (parsed.role !== 'dimension' && parsed.role !== 'metric')
    ) return undefined;
    return { field: parsed.field, role: parsed.role };
  } catch {
    return undefined;
  }
};

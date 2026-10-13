import {
  BadgeCheck,
  Blocks,
  Cable,
  ChartNoAxesCombined,
  Columns3,
  Database,
  FileText,
  FolderTree,
  Gauge,
  Layers3,
  Network,
  PanelsTopLeft,
  Server,
  Table2,
  Workflow,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';

// Presentation fallbacks only: available types and labels still come from the metamodel.
const ICONS: Record<string, LucideIcon> = {
  databaseService: Server,
  database: Database,
  table: Table2,
  tableColumn: Columns3,
  dataModel: Network,
  standardField: BadgeCheck,
  domain: FolderTree,
  metric: Gauge,
  TABLE: Table2,
  METADATA: Table2,
  MODEL: Network,
  METRIC: Gauge,
  DATASET: Layers3,
  DATA_SERVICE: Cable,
  DASHBOARD: PanelsTopLeft,
  CHART: ChartNoAxesCombined,
  TASK: Workflow,
  DOC: FileText,
  MANUAL: FileText,
};

export default function CatalogEntityIcon({
  kind,
  iconUrl,
  size = 18,
}: {
  kind?: string | null;
  iconUrl?: string | null;
  size?: number;
}) {
  const [failedUrl, setFailedUrl] = useState<string>();
  const url = iconUrl?.trim();
  const Icon = ICONS[kind ?? ''] ?? Blocks;
  // Type-defined assets take precedence, including for newly registered entity types.
  if (url && url !== failedUrl && (/^https?:\/\//i.test(url) || /^\/(?!\/)/.test(url))) {
    return (
      <img
        className="catalog-entity-icon"
        src={url}
        alt=""
        aria-hidden
        width={size}
        height={size}
        onError={() => setFailedUrl(url)}
      />
    );
  }
  return <Icon className="catalog-entity-icon" size={size} strokeWidth={1.8} aria-hidden />;
}

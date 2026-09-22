export type QualityDataSourceTreeKey = `data-source:${number}`;

export interface QualityDataSourceNode {
  key: QualityDataSourceTreeKey;
  dataSourceId: number;
  dataSourceName: string;
  dataSourceType: string;
  environment?: string;
}

export interface QualityDataSourceGroup {
  dataSourceType: string;
  nodes: QualityDataSourceNode[];
}

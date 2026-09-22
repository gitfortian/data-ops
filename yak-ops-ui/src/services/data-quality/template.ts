import HttpUtils from '@/utils/HttpUtils';

import { DATA_QUALITY_TEMPLATE_API } from './constants';

export interface QualityTemplateCatalogSummary {
  systemTotal: number;
  customTotal: number;
  systemDimensions: Record<string, number>;
  customDimensions: Record<string, number>;
}

export const getQualityTemplateCatalogSummary =
  (): Promise<QualityTemplateCatalogSummary> =>
    HttpUtils.getData<QualityTemplateCatalogSummary>(
      `${DATA_QUALITY_TEMPLATE_API}/summary`,
    );

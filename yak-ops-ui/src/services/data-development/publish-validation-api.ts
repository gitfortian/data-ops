import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';
import type { DevelopmentTaskPublishValidation } from './publish-validation';

const NODE_API = '/api/v1/data-development/nodes';

export const validateDevelopmentTaskPublish = (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<DevelopmentTaskPublishValidation> =>
  HttpUtils.postData<DevelopmentTaskPublishValidation>(
    `${NODE_API}/${encodeURIComponent(nodeId)}/publish-validation`,
    { draftRevision },
  );

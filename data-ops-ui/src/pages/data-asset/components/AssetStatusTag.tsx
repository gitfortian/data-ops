import { Tag } from 'antd';

import type { AssetStatus } from '@/services/data-asset/types';
import { ASSET_STATUS_COLORS, ASSET_STATUS_LABELS } from '../constants';

const AssetStatusTag = ({ status }: { status: AssetStatus }) => (
  <Tag color={ASSET_STATUS_COLORS[status]}>{ASSET_STATUS_LABELS[status] ?? status}</Tag>
);

export default AssetStatusTag;

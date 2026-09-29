import { history } from '@umijs/max';
import { useEffect } from 'react';

import type { DataServiceApi } from '@/services/data-service';

interface DataServiceDetailNavigatorProps {
  open: boolean;
  service?: DataServiceApi;
  onClose: () => void;
}

/** Navigate the historical drawer entry to the standalone API detail route. */
const DataServiceDetailNavigator = ({
  open,
  service,
  onClose,
}: DataServiceDetailNavigatorProps) => {
  useEffect(() => {
    if (
      !open ||
      service?.id === undefined ||
      service.id === null
    ) {
      return;
    }

    onClose();
    history.push(
      `/data-service/api/${encodeURIComponent(String(service.id))}`,
    );
  }, [onClose, open, service?.id]);

  return null;
};

export default DataServiceDetailNavigator;

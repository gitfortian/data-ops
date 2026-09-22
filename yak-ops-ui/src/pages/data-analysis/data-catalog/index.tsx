import { history } from '@umijs/max';
import { useEffect } from 'react';

const LegacyDataCatalogRedirect = () => {
  useEffect(() => {
    history.replace(`/dataset${window.location.search}`);
  }, []);

  return null;
};

export default LegacyDataCatalogRedirect;

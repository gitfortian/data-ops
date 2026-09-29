import { history } from '@umijs/max';
import { useEffect } from 'react';

const LegacyChartAnalysisRedirect = () => {
  useEffect(() => {
    history.replace(`/dashboard${window.location.search}`);
  }, []);

  return null;
};

export default LegacyChartAnalysisRedirect;

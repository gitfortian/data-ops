import { homeCockpitApi, type HomeCockpitOverview } from '@/services/home';
import { useEffect, useState } from 'react';

interface HomeCockpitState {
  data?: HomeCockpitOverview;
  loading: boolean;
  failed: boolean;
}

export function useHomeCockpit(): HomeCockpitState {
  const [state, setState] = useState<HomeCockpitState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;
    void homeCockpitApi
      .overview()
      .then((response) => {
        if (!active) return;
        setState({ data: response.data, loading: false, failed: false });
      })
      .catch(() => {
        if (!active) return;
        setState({ loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

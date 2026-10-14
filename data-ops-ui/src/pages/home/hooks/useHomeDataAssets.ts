import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { readSharedHomeAssetOverview } from './readSharedHomeAssetOverview';
import {
  fetchDashboardOverview,
  type DashboardSummary,
} from '@/services/dashboard';
import {
  listDataServices,
  type DataServiceApi,
} from '@/services/data-service';
import {
  listDigitalScreens,
  type DigitalScreenInstance,
} from '@/services/digital-screen';
import {
  type HomeAssetDatasetItem,
} from '@/services/home';
import { useEffect, useState } from 'react';

const PREVIEW_LIMIT = 5;

export interface HomeDataAssetListState<T> {
  items: T[];
  loading: boolean;
  failed: boolean;
}

const timestamp = (value?: string | null) => {
  if (!value) return 0;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
};

const recentFirst = <T,>(
  items: T[],
  resolveTime: (item: T) => string | null | undefined,
) =>
  [...items]
    .sort((left, right) => timestamp(resolveTime(right)) - timestamp(resolveTime(left)))
    .slice(0, PREVIEW_LIMIT);

export function useHomeDataAssets() {
  const { currentProject } = useSecurityProject();
  const projectId = currentProject?.id == null ? undefined : String(currentProject.id);
  const [datasets, setDatasets] = useState<
    HomeDataAssetListState<HomeAssetDatasetItem>
  >({ items: [], loading: true, failed: false });
  const [dashboards, setDashboards] = useState<
    HomeDataAssetListState<DashboardSummary>
  >({ items: [], loading: true, failed: false });
  const [screens, setScreens] = useState<
    HomeDataAssetListState<DigitalScreenInstance>
  >({ items: [], loading: true, failed: false });
  const [services, setServices] = useState<
    HomeDataAssetListState<DataServiceApi>
  >({ items: [], loading: true, failed: false });

  useEffect(() => {
    let active = true;
    if (!projectId) {
      setDatasets({ items: [], loading: false, failed: false });
      setDashboards({ items: [], loading: false, failed: false });
      setScreens({ items: [], loading: false, failed: false });
      setServices({ items: [], loading: false, failed: false });
      return () => { active = false; };
    }
    setDatasets({ items: [], loading: true, failed: false });
    setDashboards({ items: [], loading: true, failed: false });
    setScreens({ items: [], loading: true, failed: false });
    setServices({ items: [], loading: true, failed: false });

    void readSharedHomeAssetOverview(projectId)
      .then((response) => {
        if (!active) return;
        setDatasets({
          items: (response.data?.dataset?.recentDatasets || []).slice(
            0,
            PREVIEW_LIMIT,
          ),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setDatasets({ items: [], loading: false, failed: true });
      });

    void fetchDashboardOverview(PREVIEW_LIMIT)
      .then((overview) => {
        if (!active) return;
        setDashboards({
          items: (overview.recentDashboards || []).slice(0, PREVIEW_LIMIT),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setDashboards({ items: [], loading: false, failed: true });
      });

    void listDigitalScreens()
      .then((items) => {
        if (!active) return;
        setScreens({
          items: recentFirst(items, (item) => item.updatedAt),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setScreens({ items: [], loading: false, failed: true });
      });

    void listDataServices()
      .then((items) => {
        if (!active) return;
        setServices({
          items: recentFirst(items, (item) => item.updateTime || item.createTime),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setServices({ items: [], loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, [projectId]);

  return {
    datasets,
    dashboards,
    screens,
    services,
  };
}

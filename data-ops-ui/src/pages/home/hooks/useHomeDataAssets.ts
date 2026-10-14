import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { pulseScopeKey } from '../components/projectPulseContract';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { useModel } from '@umijs/max';
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
  scope?: string | null;
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
  const { initialState } = useModel('@@initialState');
  const user = initialState?.currentUser;
  const scope = pulseScopeKey(projectId, user?.userid, user?.permissionCodes, user?.menuCodes);
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
    if (!projectId || !scope) {
      setDatasets({ scope, items: [], loading: false, failed: false });
      setDashboards({ scope, items: [], loading: false, failed: false });
      setScreens({ scope, items: [], loading: false, failed: false });
      setServices({ scope, items: [], loading: false, failed: false });
      return () => { active = false; };
    }
    setDatasets({ scope, items: [], loading: true, failed: false });
    setDashboards({ scope, items: [], loading: true, failed: false });
    setScreens({ scope, items: [], loading: true, failed: false });
    setServices({ scope, items: [], loading: true, failed: false });

    void readSharedHomeAssetOverview(scope)
      .then((response) => {
        if (!active) return;
        if (response.code !== API_SUCCESS_CODE || !response.data) {
          setDatasets({ scope, items: [], loading: false, failed: true });
          return;
        }
        setDatasets({
          scope,
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
        setDatasets({ scope, items: [], loading: false, failed: true });
      });

    void fetchDashboardOverview(PREVIEW_LIMIT)
      .then((overview) => {
        if (!active) return;
        setDashboards({
          scope,
          items: (overview.recentDashboards || []).slice(0, PREVIEW_LIMIT),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setDashboards({ scope, items: [], loading: false, failed: true });
      });

    void listDigitalScreens()
      .then((items) => {
        if (!active) return;
        setScreens({
          scope,
          items: recentFirst(items, (item) => item.updatedAt),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setScreens({ scope, items: [], loading: false, failed: true });
      });

    void listDataServices()
      .then((items) => {
        if (!active) return;
        setServices({
          scope,
          items: recentFirst(items, (item) => item.updateTime || item.createTime),
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setServices({ scope, items: [], loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, [projectId, scope]);

  // Mask prior-project/identity snapshots before passive effects start new reads.
  const visible = <T,>(value: HomeDataAssetListState<T>): HomeDataAssetListState<T> =>
    value.scope === scope ? value : { items: [], loading: Boolean(scope), failed: false, scope };
  return {
    datasets: visible(datasets),
    dashboards: visible(dashboards),
    screens: visible(screens),
    services: visible(services),
  };
}

import {
  homeDataCenterApi,
  type HomeDataCenterOverview,
  type HomeRecentTask,
  type HomeScheduleItem,
} from '@/services/home';
import { useEffect, useState } from 'react';

import type {
  HomeDataCenterPeriodKey,
  HomeDataCenterTabKey,
} from '../types';

export function useHomeDataCenter() {
  const [activeTab, setActiveTab] =
    useState<HomeDataCenterTabKey>('overview');
  const [periodKey, setPeriodKey] = useState<HomeDataCenterPeriodKey>('7d');
  const [overview, setOverview] = useState<HomeDataCenterOverview>();
  const [recentTasks, setRecentTasks] = useState<HomeRecentTask[]>([]);
  const [scheduleItems, setScheduleItems] = useState<HomeScheduleItem[]>([]);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [overviewFailed, setOverviewFailed] = useState(false);
  const [recentLoading, setRecentLoading] = useState(false);
  const [recentFailed, setRecentFailed] = useState(false);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [scheduleFailed, setScheduleFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setOverviewLoading(true);
    setOverviewFailed(false);
    void homeDataCenterApi
      .overview(periodKey)
      .then((response) => {
        if (!active) return;
        setOverview(response.data);
        setOverviewLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setOverview(undefined);
        setOverviewLoading(false);
        setOverviewFailed(true);
      });
    return () => {
      active = false;
    };
  }, [periodKey]);

  useEffect(() => {
    if (activeTab !== 'recent') return undefined;
    let active = true;
    setRecentLoading(true);
    setRecentFailed(false);
    void homeDataCenterApi
      .recent()
      .then((response) => {
        if (!active) return;
        setRecentTasks(response.data?.items || []);
        setRecentLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setRecentTasks([]);
        setRecentLoading(false);
        setRecentFailed(true);
      });
    return () => {
      active = false;
    };
  }, [activeTab]);

  useEffect(() => {
    if (activeTab !== 'schedule') return undefined;
    let active = true;
    setScheduleLoading(true);
    setScheduleFailed(false);
    void homeDataCenterApi
      .schedule(periodKey)
      .then((response) => {
        if (!active) return;
        setScheduleItems(response.data?.items || []);
        setScheduleLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setScheduleItems([]);
        setScheduleLoading(false);
        setScheduleFailed(true);
      });
    return () => {
      active = false;
    };
  }, [activeTab, periodKey]);

  return {
    activeTab,
    setActiveTab,
    periodKey,
    setPeriodKey,
    overview,
    recentTasks,
    scheduleItems,
    overviewLoading,
    overviewFailed,
    recentLoading,
    recentFailed,
    scheduleLoading,
    scheduleFailed,
  };
}

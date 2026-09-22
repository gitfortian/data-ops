import { history, useParams } from '@umijs/max';
import { useEffect } from 'react';

/**
 * 兼容旧的 `/dashboard/:id` 查看地址。
 * 仪表盘查看与编辑统一复用 fullscreen editor，避免维护两套重复页面。
 */
export default function DashboardViewerRedirect() {
  const { id } = useParams<{ id?: string }>();

  useEffect(() => {
    if (!id) {
      history.replace('/dashboard');
      return;
    }
    history.replace(`/dashboard/${id}/edit?preview=1`);
  }, [id]);

  return null;
}

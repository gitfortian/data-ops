import { parseConsumptionReviewReturnPath } from '@/config/consumer-source-navigation';
import { history, useParams, useSearchParams } from '@umijs/max';
import { useEffect } from 'react';

/**
 * Keep the previous governed-consumption context through the legacy
 * /dashboard/:id → fullscreen preview redirect. Do not accept arbitrary
 * return URLs from query parameters.
 */
export default function DashboardViewerRedirect() {
  const { id } = useParams<{ id?: string }>();
  const [searchParams] = useSearchParams();
  const returnTo = parseConsumptionReviewReturnPath(searchParams.get('returnTo'));

  useEffect(() => {
    if (!id) {
      history.replace('/dashboard');
      return;
    }
    const query = new URLSearchParams({ preview: '1' });
    if (returnTo) query.set('returnTo', returnTo);
    history.replace(`/dashboard/${id}/edit?${query.toString()}`);
  }, [id, returnTo]);

  return null;
}

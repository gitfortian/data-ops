import { history, useParams } from '@umijs/max';
import { useEffect } from 'react';

/** Metadata detail deep link: reuse the canonical entity drawer owned by Asset catalog. */
export default function MetadataDetailRedirect() {
  const { id } = useParams<{ id: string }>();

  useEffect(() => {
    if (id && /^\d+$/.test(id)) {
      history.replace(`/data-asset/catalog?view=entity&entityId=${encodeURIComponent(id)}`);
    } else {
      history.replace('/data-asset/catalog?view=entity');
    }
  }, [id]);

  return null;
}

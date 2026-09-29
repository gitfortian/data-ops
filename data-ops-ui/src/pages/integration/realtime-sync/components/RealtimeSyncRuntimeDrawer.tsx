import type {
  RealtimeEvent,
  RealtimeJob,
} from '@/services/realtime-sync';
import { Drawer } from 'antd';

import RealtimeRuntimeDetail from '../RealtimeRuntimeDetail';

interface RealtimeSyncRuntimeDrawerProps {
  job?: RealtimeJob;
  events: RealtimeEvent[];
  onClose: () => void;
}

const RealtimeSyncRuntimeDrawer = ({
  job,
  events,
  onClose,
}: RealtimeSyncRuntimeDrawerProps) => (
  <Drawer
    width={960}
    title={job?.name}
    open={Boolean(job)}
    onClose={onClose}
  >
    {job ? <RealtimeRuntimeDetail job={job} events={events} /> : null}
  </Drawer>
);

export default RealtimeSyncRuntimeDrawer;

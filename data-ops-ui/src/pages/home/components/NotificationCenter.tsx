import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  markMessageRead,
  notifyMessageCountChanged,
  pageMessages,
  safeMessageActionPath,
  type SecurityMessage,
} from '@/services/security/messages';
import { history, useIntl } from '@umijs/max';
import { Bell, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

interface NotificationState {
  items: SecurityMessage[];
  loading: boolean;
  failed: boolean;
}

const formatMessageDate = (value?: string | number) => {
  if (value === undefined || value === null || value === '') return '--';

  const date = new Date(value);

  if (!Number.isFinite(date.getTime())) {
    return String(value);
  }

  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
};

function NotificationRow({
  item,
  onOpen,
}: {
  item: SecurityMessage;
  onOpen: (item: SecurityMessage) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      className="
        group
        flex
        h-8
        w-full
        items-center
        gap-4
        border-0
        bg-transparent
        px-2
        text-left
        transition-colors
        duration-150
        hover:bg-[#f5f6f8]
      "
    >
      <span
        className="
          min-w-0
          flex-1
          truncate
          text-[14px]
          font-normal
          leading-5
          text-[#30333b]
          transition-colors
          duration-150
          group-hover:text-[#17191f]
        "
      >
        {item.title}
      </span>

      <span
        className="
          shrink-0
          text-[12px]
          leading-5
          text-[#858a94]
        "
      >
        {formatMessageDate(item.createTime)}
      </span>
    </button>
  );
}

export default function NotificationCenter() {
  const intl = useIntl();
  const { projects, currentProject } = useSecurityProject();

  const [state, setState] = useState<NotificationState>({
    items: [],
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    if (projects.length > 0 && !currentProject) {
      setState({
        items: [],
        loading: true,
        failed: false,
      });

      return () => {
        active = false;
      };
    }

    setState({
      items: [],
      loading: true,
      failed: false,
    });

    pageMessages({
      pageNum: 1,
      pageSize: 3,
      status: 'UNREAD',
      projectId: currentProject?.id,
    })
      .then((result) => {
        if (!active) return;

        setState({
          items: result.records || [],
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;

        setState({
          items: [],
          loading: false,
          failed: true,
        });
      });

    return () => {
      active = false;
    };
  }, [currentProject?.id, projects.length]);

  const openNotification = async (item: SecurityMessage) => {
    const actionPath =
      safeMessageActionPath(item.actionPath) || '/system/messages';

    try {
      await markMessageRead(item.id);
      notifyMessageCountChanged();
    } catch {
      // Reading a notification must never block the user from opening its target.
    } finally {
      history.push(actionPath);
    }
  };

  return (
    <section className="min-w-0 rounded-[22px] bg-white px-6 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4 border-b border-[#eceef1] pb-3">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({
            id: 'pages.home.notification.title',
          })}
        </h2>

        <button
          type="button"
          onClick={() => history.push('/system/messages')}
          className="
            flex
            shrink-0
            items-center
            gap-0.5
            border-0
            bg-transparent
            p-0
            text-[12px]
            text-[#666b75]
            transition-colors
            duration-150
            hover:text-[#252832]
          "
        >
          {intl.formatMessage({
            id: 'pages.home.common.viewMore',
          })}

          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      </header>

      <div className="min-h-[120px] pt-2">
        {state.items.length > 0 ? (
          <div>
            {state.items.map((item) => (
              <NotificationRow
                key={item.id}
                item={item}
                onOpen={openNotification}
              />
            ))}
          </div>
        ) : state.loading || state.failed ? (
          <div className="flex min-h-[120px] items-center justify-center text-[11px] text-[#9da1a8]">
            {intl.formatMessage({
              id: state.loading
                ? 'pages.home.notification.loading'
                : 'pages.home.notification.failed',
            })}
          </div>
        ) : (
          <HomeEmptyState
            icon={Bell}
            title={intl.formatMessage({
              id: 'pages.home.notification.empty',
            })}
            size="small"
            className="min-h-[120px]"
          />
        )}
      </div>
    </section>
  );
}
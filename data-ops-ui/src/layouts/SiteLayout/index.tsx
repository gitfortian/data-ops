import {
  getActiveNavigationGroupPath,
  getActiveNavigationId,
  getMainNavigationGroups,
  getQuickCreateRoutes,
  getRouteMetadata,
  getStandaloneNavigationRoutes,
  type NavigationIconKey,
} from "@/config/navigation";
import { RouteAccessBoundary } from "@/components/security";
import SecurityProjectSwitcher from "@/components/security/SecurityProjectSwitcher";
import { SecurityProjectProvider, useSecurityProject } from "@/contexts/SecurityProjectContext";
import { logout } from "@/services/security/account";
import { history, Link, Outlet, useLocation, useModel } from "@umijs/max";
import { Badge, ConfigProvider, Drawer, Dropdown, type MenuProps } from "antd";
import { MotionConfig } from "framer-motion";
import {
  Activity,
  ArrowLeftRight,
  Bell,
  BookOpen,
  Braces,
  ChartLine,
  ChartPie,
  ChevronDown,
  CircleCheck,
  CircleHelp,
  Database,
  FileText,
  Folder,
  History,
  House,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Plug,
  Server,
  Settings,
  Sparkles,
  SquarePlus,
  Workflow,
} from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  getUnreadMessageCount,
  MESSAGE_COUNT_CHANGED_EVENT,
} from "@/services/security/messages";
import { recordRecentVisit } from "@/utils/recent-visits";

import SidebarNavigation from './SidebarNavigation';

const HEADER_HEIGHT = 48;
const SIDEBAR_WIDTH = 240;
const COLLAPSED_SIDEBAR_WIDTH = 64;

const NAVIGATION_ICON_SIZE = 17;
const NAVIGATION_ICON_STROKE_WIDTH = 1.8;

const navigationIcons: Record<NavigationIconKey, ReactNode> = {
  home: (
    <House
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  database: (
    <Database
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  sync: (
    <ArrowLeftRight
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  realtime: (
    <Activity
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  client: (
    <Server
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  connector: (
    <Plug
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  workflow: (
    <Workflow
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  project: (
    <Folder
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  instance: (
    <History
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  quality: (
    <CircleCheck
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  report: (
    <FileText
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  monitor: (
    <ChartLine
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  alarm: (
    <Bell
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  knowledge: (
    <BookOpen
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  api: (
    <Braces
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  insight: (
    <ChartPie
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
  system: (
    <Settings
      size={NAVIGATION_ICON_SIZE}
      strokeWidth={NAVIGATION_ICON_STROKE_WIDTH}
    />
  ),
};

interface HeaderActionProps {
  icon: ReactNode;
  label: string;
  badge?: boolean;
  badgeCount?: number;
  onClick?: () => void;
}

function HeaderAction({
  icon,
  label,
  badge = false,
  badgeCount,
  onClick,
}: HeaderActionProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className="
        group relative flex h-12 min-w-11 max-md:h-9 max-md:min-w-8 flex-col
        items-center justify-center border-0 bg-transparent
        px-2 text-[12px] text-[rgba(35,35,35,0.6)]
        transition-colors duration-150
        hover:text-[rgba(35,35,35,0.9)]
      "
    >
      <span
        className="
          relative flex h-6 w-6 items-center
          justify-center text-[17px]
        "
      >
        <Badge count={badgeCount} size="small" overflowCount={99}>{icon}</Badge>

        {badge && !badgeCount && (
          <span
            className="
              absolute right-0 top-0 h-1.5 w-1.5
              rounded-full bg-[#fe2c55]
              ring-2 ring-white
            "
          />
        )}
      </span>

      <span
        className="
          mt-0.5 whitespace-nowrap text-[12px] max-md:hidden
          leading-3
        "
      >
        {label}
      </span>
    </button>
  );
}

function BrandLogo({ compact }: { compact: boolean }) {
  return (
    <Link
      to="/home"
      aria-label="返回首页"
      className={[
        "mt-3 mb-1.5 flex h-12 w-full items-center border-0 bg-transparent",
        "transition-all duration-200",
        compact ? "justify-center px-0" : "justify-start px-5",
      ].join(" ")}
    >
      {compact ? (
        <span className="relative block h-9 w-9 shrink-0 overflow-hidden">
          <img
            src="/logo.png"
            alt="Data Ops"
            draggable={false}
            className="
              absolute left-[-3px] top-1/2
              h-9 max-w-none -translate-y-1/2
              select-none object-contain
            "
          />
        </span>
      ) : (
        <img
          src="/logo.png"
          alt="Data Ops 一体化数字平台"
          draggable={false}
          className="
            block h-8 w-auto max-w-full
            select-none object-contain object-left
          "
        />
      )}
    </Link>
  );
}

function SiteLayoutContent() {
  const location = useLocation();
  const { initialState, setInitialState } = useModel("@@initialState");
  const currentUser = initialState?.currentUser;
  const permissionCodes = currentUser?.permissionCodes;
  const menuCodes = currentUser?.menuCodes;

  // Navigation metadata remains the single source of truth. Recalculate every
  // authorization-derived collection together whenever the signed-in identity changes.
  const { standaloneRoutes, navigationGroups, quickCreateRoutes } = useMemo(
    () => ({
      standaloneRoutes: getStandaloneNavigationRoutes(permissionCodes, menuCodes),
      navigationGroups: getMainNavigationGroups(permissionCodes, menuCodes),
      quickCreateRoutes: getQuickCreateRoutes(permissionCodes, menuCodes),
    }),
    [permissionCodes, menuCodes],
  );
  const quickCreateRef = useRef<HTMLDivElement>(null);

  const [collapsed, setCollapsed] = useState(false);

  const [viewportCompact, setViewportCompact] = useState(false);
  const [viewportMobile, setViewportMobile] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const commercialUi = !location.pathname.startsWith('/ai-agent');

  useEffect(() => {
    if (commercialUi) document.body.dataset.yakWorkspace = 'commercial';
    else delete document.body.dataset.yakWorkspace;
    return () => { delete document.body.dataset.yakWorkspace; };
  }, [commercialUi]);

  const [quickCreateOpen, setQuickCreateOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);

  const { clearProject } = useSecurityProject();

  useEffect(() => {
    let active = true;
    const refresh = () => getUnreadMessageCount().then((count) => { if (active) setUnreadCount(count); }).catch(() => undefined);
    refresh();
    window.addEventListener(MESSAGE_COUNT_CHANGED_EVENT, refresh);
    return () => { active = false; window.removeEventListener(MESSAGE_COUNT_CHANGED_EVENT, refresh); };
  }, [currentUser?.userid]);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      clearProject();
      await setInitialState((state) => ({
        ...state,
        currentUser: undefined,
        currentProject: undefined,
        securityProject: undefined,
      }));
      history.replace("/login");
    }
  };

  const userMenuItems: MenuProps["items"] = [
    {
      key: "identity",
      disabled: true,
      label: (
        <div className="min-w-48 py-1">
          <div className="font-semibold text-[#161823]">
            {currentUser?.name ?? currentUser?.userName ?? "当前用户"}
          </div>
          <div className="mt-0.5 text-xs text-[rgba(22,24,35,0.5)]">
            {currentUser?.email ?? currentUser?.title ?? "Yak Security 用户"}
          </div>
        </div>
      ),
    },
    { type: "divider" },
    {
      key: "settings",
      icon: <Settings className="h-4 w-4" />,
      label: "设置",
      onClick: () => history.push("/settings"),
    },
    { type: "divider" },
    {
      key: "logout",
      danger: true,
      icon: <LogOut className="h-4 w-4" />,
      label: loggingOut ? "正在退出…" : "退出登录",
      disabled: loggingOut,
      onClick: handleLogout,
    },
  ];

  useEffect(() => {
    const mediaQuery = window.matchMedia("(max-width: 1080px)");
    const mobileQuery = window.matchMedia("(max-width: 767px)");

    const syncViewport = () => {
      setViewportCompact(mediaQuery.matches);
      setViewportMobile(mobileQuery.matches);
    };

    syncViewport();

    mediaQuery.addEventListener("change", syncViewport);
    mobileQuery.addEventListener("change", syncViewport);

    return () => {
      mediaQuery.removeEventListener("change", syncViewport);
      mobileQuery.removeEventListener("change", syncViewport);
    };
  }, []);

  useEffect(() => {
    setQuickCreateOpen(false);
    setMobileMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target as Node;

      if (quickCreateRef.current && !quickCreateRef.current.contains(target)) {
        setQuickCreateOpen(false);
      }
    };

    document.addEventListener("mousedown", handleOutsideClick);

    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
    };
  }, []);

  const compact = collapsed || viewportCompact;

  const sidebarWidth = viewportMobile ? 0 : compact ? COLLAPSED_SIDEBAR_WIDTH : SIDEBAR_WIDTH;

  const activeNavigationId = getActiveNavigationId(
    location.pathname,
    permissionCodes,
    menuCodes,
  );

  const routeMetadata = getRouteMetadata(location.pathname);

  const pageTitle = routeMetadata?.title ?? "Data Ops";

  useEffect(() => {
    if (!routeMetadata || routeMetadata.id === "home") return;
    recordRecentVisit({
      path: `${location.pathname}${location.search}`,
      title: routeMetadata.title,
    });
  }, [
    location.pathname,
    location.search,
    routeMetadata?.id,
    routeMetadata?.title,
  ]);

  const userInitial = useMemo(() => {
    return currentUser?.name?.trim().slice(0, 1).toUpperCase() || "Y";
  }, [currentUser?.name]);

  return (
    <ConfigProvider theme={commercialUi ? { token: { colorPrimary: '#292c35', colorLink: '#344054', colorInfo: '#175cd3', borderRadius: 8, controlHeight: 36, fontSize: 14 } } : undefined}>
    <MotionConfig reducedMotion={commercialUi ? 'user' : 'never'}>
    <div
      data-yak-workspace={commercialUi ? 'commercial' : undefined}
      className="
        h-screen overflow-hidden
        bg-[#f7f8f9] text-[#161823]
      "
    >
      <aside
        className="
          fixed inset-y-0 left-0 z-40
          hidden md:flex flex-col overflow-hidden
          bg-[linear-gradient(180deg,#f2f2f7_0%,#f5f5f5_100%)]
          transition-[width] duration-200
          ease-[cubic-bezier(0.62,0.05,0.36,0.95)]
        "
        style={{
          width: sidebarWidth,
        }}
      >
        <BrandLogo compact={compact} />

        <div
          ref={quickCreateRef}
          className={[
            "relative z-50 shrink-0",
            quickCreateRoutes.length === 0 ? "hidden" : "",
            compact ? "mx-3 mb-4 mt-4" : "mx-6 mb-4 mt-4",
          ].join(" ")}
        >
          <button
            type="button"
            aria-label="快速创建"
            aria-expanded={quickCreateOpen}
            title={compact ? "快速创建" : undefined}
            onClick={() => setQuickCreateOpen((current) => !current)}
            className={[
              "flex h-10 items-center border-0 text-white",
              "bg-[linear-gradient(102deg,#fe516e_0%,#fe2c55_100%)]",
              "shadow-[0_6px_16px_rgba(254,44,85,0.22)]",
              "transition-all duration-200",
              "ease-[cubic-bezier(0.62,0.05,0.36,0.95)]",
              "hover:brightness-[0.97] active:brightness-95",
              compact
                ? "w-10 justify-center rounded-full px-0"
                : "w-full justify-start rounded-md px-3",
            ].join(" ")}
          >
            <SquarePlus className="h-4 w-4 shrink-0" strokeWidth={2} />

            {!compact && (
              <>
                <span
                  className="
                    ml-2 min-w-0 flex-1
                    truncate text-left text-[14px]
                    font-semibold
                  "
                >
                  快速创建
                </span>

                <ChevronDown
                  className={[
                    "h-3 w-3 shrink-0",
                    "transition-transform duration-200",
                    quickCreateOpen ? "rotate-180" : "rotate-0",
                  ].join(" ")}
                />
              </>
            )}
          </button>

          {quickCreateOpen && (
            <div
              className={[
                "absolute z-[100]",
                "rounded-lg bg-white p-2",
                "shadow-[0_4px_24px_rgba(0,0,0,0.12)]",
                "ring-1 ring-black/[0.04]",
                compact
                  ? ["left-[48px] top-0", "w-44"].join(" ")
                  : ["left-0 right-0", "top-[48px]"].join(" "),
              ].join(" ")}
            >
              {quickCreateRoutes.length > 0 ? (
                quickCreateRoutes.map((route) => (
                  <Link
                    key={route.id}
                    to={route.path}
                    onClick={() => setQuickCreateOpen(false)}
                    className="
                        flex h-9 w-full items-center
                        rounded-md border-0 bg-transparent
                        px-2.5 text-left text-[14px]
                        text-[#1c1f23]
                        transition-colors duration-150
                        hover:bg-[#f5f5f6]
                      "
                  >
                    <span
                      className="
                          mr-2 flex h-5 w-5
                          shrink-0 items-center
                          justify-center
                          text-[14px]
                          text-[rgba(22,24,35,0.55)]
                        "
                    >
                      {route.iconKey ? (
                        navigationIcons[route.iconKey]
                      ) : (
                        <SquarePlus className="h-4 w-4" strokeWidth={1.8} />
                      )}
                    </span>

                    <span className="truncate">
                      {route.quickCreateLabel ?? route.title}
                    </span>
                  </Link>
                ))
              ) : (
                <div
                  className="
                    px-2 py-2 text-[12px]
                    text-[rgba(22,24,35,0.4)]
                  "
                >
                  暂无可创建内容
                </div>
              )}
            </div>
          )}
        </div>

        <nav aria-label="主导航" className={[
          'min-h-0 flex-1 overflow-y-auto overflow-x-hidden py-2',
          compact ? 'px-3' : 'px-4',
        ].join(' ')}>
          <SidebarNavigation
            groups={navigationGroups}
            standaloneRoutes={standaloneRoutes}
            icons={navigationIcons}
            compact={compact}
            activeId={activeNavigationId}
            activeGroupPath={getActiveNavigationGroupPath(location.pathname, permissionCodes, menuCodes)}
          />
        </nav>

        <div
          className={["shrink-0 pb-4 pt-2", compact ? "px-3" : "px-6"].join(
            " "
          )}
        >
          <button
            type="button"
            title={compact ? "帮助中心" : undefined}
            className={[
              "flex h-10 w-full items-center",
              "border-0 bg-transparent",
              "text-[rgba(22,24,35,0.5)]",
              "transition-colors duration-150",
              "hover:text-[#161823]",
              compact ? "justify-center px-0" : "justify-start px-0",
            ].join(" ")}
          >
            <CircleHelp className="h-4 w-4" strokeWidth={1.8} />

            {!compact && (
              <span
                className="
                  ml-3 text-[14px]
                  font-medium
                "
              >
                帮助中心
              </span>
            )}
          </button>
        </div>
      </aside>

      <Drawer title="导航" placement="left" width={300} open={mobileMenuOpen} onClose={() => setMobileMenuOpen(false)} rootClassName="yak-mobile-navigation">
        <nav aria-label="手机主导航">
          <SidebarNavigation groups={navigationGroups} standaloneRoutes={standaloneRoutes} icons={navigationIcons} compact={false} activeId={activeNavigationId} activeGroupPath={getActiveNavigationGroupPath(location.pathname, permissionCodes, menuCodes)} />
        </nav>
        {quickCreateRoutes.length > 0 && <div className="mt-5 border-t border-[#eaecf0] pt-4">
          <div className="mb-2 text-xs font-medium text-[#667085]">快速创建</div>
          {quickCreateRoutes.map(route => <Link key={route.id} to={route.path} className="block rounded-md px-3 py-2 text-sm text-[#344054]">{route.quickCreateLabel ?? route.title}</Link>)}
        </div>}
      </Drawer>

      <header
        className="
          fixed right-0 top-0 z-30
          flex items-center
          transition-[left] duration-200
          ease-[cubic-bezier(0.62,0.05,0.36,0.95)]
        "
        style={{
          left: sidebarWidth,
          height: HEADER_HEIGHT,
        }}
      >
        <div
          className="
            flex h-full min-w-0 flex-1
            items-center px-4
          "
        >
          <button
            type="button"
            aria-label={compact ? "展开菜单" : "收起菜单"}
            aria-expanded={viewportMobile ? mobileMenuOpen : !compact}
            onClick={() => viewportMobile ? setMobileMenuOpen(true) : setCollapsed((current) => !current)}
            className="
              flex h-9 w-9 shrink-0 items-center
              justify-center rounded-md
              border-0 bg-transparent
              text-[16px]
              text-[rgba(22,24,35,0.5)]
              transition-colors duration-150
              hover:bg-[#f5f5f6]
              hover:text-[#161823]
              md:flex
            "
          >
            {compact ? (
              <PanelLeftOpen className="h-4 w-4" strokeWidth={1.8} />
            ) : (
              <PanelLeftClose className="h-4 w-4" strokeWidth={1.8} />
            )}
          </button>

          <div
            className="
              ml-2 min-w-0
              border-l border-[rgba(28,31,35,0.08)]
              pl-4
            "
          >
            <div
              className="
                truncate text-[14px]
                font-semibold leading-5
                text-[#1c1f23]
              "
            >
              {pageTitle}
            </div>
          </div>
        </div>

        <div
          className="
            flex h-full shrink-0
            items-center pr-2 md:pr-5
          "
        >
          <HeaderAction
            icon={<Bell className="h-[17px] w-[17px]" strokeWidth={1.8} />}
            label="通知"
            badge={unreadCount > 0}
            badgeCount={unreadCount}
            onClick={() => history.push("/system/messages")}
          />

          <HeaderAction
            icon={<Sparkles className="h-[17px] w-[17px]" strokeWidth={1.8} />}
            label="智能助手"
            onClick={() => history.push("/ai-agent")}
          />

          <SecurityProjectSwitcher />

          <div
            className="
              ml-1 md:ml-4 flex items-center
              border-l border-[rgba(28,31,35,0.08)]
              pl-1 md:pl-4
            "
          >
            <Dropdown menu={{ items: userMenuItems }} trigger={["click"]}>
              <button
                type="button"
                aria-label="打开用户菜单"
                className="flex items-center gap-2 border-0 bg-transparent p-0"
              >
                {currentUser?.avatar ? (
                  <img
                    src={currentUser.avatar}
                    alt={currentUser.name ?? "当前用户"}
                    className="h-8 w-8 rounded-full object-cover"
                  />
                ) : (
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#e5e7eb] text-[12px] font-semibold text-[#475569]">
                    {userInitial}
                  </span>
                )}
                <ChevronDown className="h-3 w-3 text-[rgba(22,24,35,0.4)]" />
              </button>
            </Dropdown>
          </div>
        </div>
      </header>

      <main
        className="
          h-screen overflow-hidden
          bg-[#f7f8f9]
          transition-[padding] duration-200
          ease-[cubic-bezier(0.62,0.05,0.36,0.95)]
        "
        style={{
          paddingLeft: sidebarWidth,
          paddingTop: HEADER_HEIGHT,
        }}
      >
        <div
          className="
            h-full w-full overflow-auto
            px-4  pt-4
          "
        >
          <div
            className="
              min-h-full min-w-0 w-full
              overflow-hidden rounded-md
              bg-white
            "
          >
            <RouteAccessBoundary
              permissionCodes={permissionCodes}
              menuCodes={menuCodes}
            >
              <Outlet />
            </RouteAccessBoundary>
          </div>
        </div>
      </main>
    </div>
    </MotionConfig>
    </ConfigProvider>
  );
}

export default function SiteLayout() {
  return <SecurityProjectProvider><SiteLayoutContent /></SecurityProjectProvider>;
}

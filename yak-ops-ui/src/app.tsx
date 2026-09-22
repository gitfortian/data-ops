import { AvatarDropdown, AvatarName, Footer } from "@/components";
import type { Settings as LayoutSettings } from "@ant-design/pro-components";
import { SettingDrawer } from "@ant-design/pro-components";
import "@ant-design/v5-patch-for-react-19";
import type { RequestConfig, RunTimeLayoutConfig } from "@umijs/max";
import { getLocale, history, useModel } from "@umijs/max";
import "d3-transition";
import { useEffect } from "react";

import defaultSettings from "../config/defaultSettings";
import { GlobalSearch, Knowledge } from "./components/RightContent";
import SidebarMenuLink from "./components/SidebarMenuLink";
import { getCurrentUser } from "./services/security/account";
import { toCurrentUser } from "./services/security/currentIdentity";
import { AUTHENTICATION_INVALIDATED_EVENT } from "./utils/security/authentication";
import { applyCurrentProjectHeader } from "./utils/security/projectContext";
import {
  getCurrentReturnTo,
  getSafeReturnTo,
  isLoginPath,
} from "./utils/security/redirect";

const isDev = process.env.NODE_ENV === "development";
const loginPath = "/login";

const syncDocumentLocale = () => {
  const currentLocale = getLocale();
  const locale = currentLocale.toLowerCase().startsWith("zh") ? "zh-CN" : "en-US";

  document.documentElement.dataset.yakLocale = locale;
  document.documentElement.lang = locale;
};

/**
 * Keep @umijs/max requests on the same Project Space contract as the shared
 * umi-request client in utils/request.tsx. Some feature services (including
 * Workflow) still use the Max request client, so without this interceptor a
 * selected workspace would be visible in the header but absent from the HTTP
 * request sent to PROJECT_REQUIRED endpoints.
 */
export const request: RequestConfig = {
  requestInterceptors: [
    (config) => ({
      ...config,
      headers: applyCurrentProjectHeader(
        config?.url ?? "",
        config?.headers as HeadersInit | undefined,
      ) as typeof config.headers,
    }),
  ],
};

const redirectAnonymousUser = () => {
  if (isLoginPath(window.location.pathname)) return;

  const returnTo = getCurrentReturnTo();
  history.replace(`${loginPath}?returnTo=${encodeURIComponent(returnTo)}`);
};

const AuthenticationStateSync = () => {
  const { setInitialState } = useModel("@@initialState");

  useEffect(() => {
    const clearAuthenticationState = () => {
      void setInitialState((state) => ({
        ...state,
        currentUser: undefined,
        currentProject: undefined,
        securityProject: undefined,
        currentUserLoadError: false,
      }));
    };

    window.addEventListener(
      AUTHENTICATION_INVALIDATED_EVENT,
      clearAuthenticationState,
    );
    return () => {
      window.removeEventListener(
        AUTHENTICATION_INVALIDATED_EVENT,
        clearAuthenticationState,
      );
    };
  }, [setInitialState]);

  return null;
};

/**
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 * */
export async function getInitialState(): Promise<{
  settings?: Partial<LayoutSettings>;
  currentUser?: API.CurrentUser;
  loading?: boolean;
  currentProject?: unknown;
  securityProject?: unknown;
  currentUserLoadError?: boolean;
  fetchUserInfo?: () => Promise<API.CurrentUser | undefined>;
}> {
  syncDocumentLocale();

  const fetchUserInfo = async () => toCurrentUser(await getCurrentUser());
  const onLoginPage = isLoginPath(window.location.pathname);

  // Application bootstrap only probes cookie-backed authentication state. An
  // anonymous first visit is expected, so keep this probe silent on every route
  // and redirect to login without showing a misleading session-expired notice.
  // Post-login refreshes still use fetchUserInfo and normal error handling.
  let currentUser: API.CurrentUser | undefined;
  let currentUserLoadError = false;
  try {
    currentUser = toCurrentUser(
      await getCurrentUser({ skipErrorHandler: true }),
    );
  } catch (error) {
    currentUserLoadError = true;

    // 当前用户获取失败时，至少先让登录页面正常展示，
    // 避免一直停留在初始化动画。
    redirectAnonymousUser();
  }
  if (currentUser && onLoginPage) {
    const requested = new URLSearchParams(window.location.search).get(
      "returnTo"
    );
    history.replace(getSafeReturnTo(requested));
  }
  return {
    fetchUserInfo,
    currentUser,
    currentUserLoadError,
    settings: defaultSettings as Partial<LayoutSettings>,
  };
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({
  initialState,
  setInitialState,
}) => {
  return {
    menuProps: {
      defaultOpenKeys: ["/sync"],
    },
    menuItemRender: (menuItemProps, defaultDom) => (
      <SidebarMenuLink path={menuItemProps.path}>
        {defaultDom}
      </SidebarMenuLink>
    ),
    actionsRender: () => [
      <GlobalSearch key="globalsearch" />,
      // <OpenAPI key="open-api" />,
      <Knowledge key="knowledge" />,
      // <BI key="bi" />,
      // <ThemeSwitch key="theme-switch" />,
      // <SelectLang key="SelectLang" />,
    ],
    avatarProps: {
      src: initialState?.currentUser?.avatar,
      title: <AvatarName />,
      render: (_, avatarChildren) => {
        return <AvatarDropdown>{avatarChildren}</AvatarDropdown>;
      },
    },
    waterMarkProps: {
      content: initialState?.currentUser?.name,
    },
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      // 如果没有登录，重定向到 login
      if (
        !initialState?.currentUser &&
        !initialState?.currentUserLoadError &&
        location.pathname !== loginPath
      ) {
        redirectAnonymousUser();
      }
    },
    bgLayoutImgList: [
      {
        src: "https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/D2LWSqNny4sAAAAAAAAAAAAAFl94AQBr",
        left: 85,
        bottom: 100,
        height: "303px",
      },
      {
        src: "https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/C2TWRpJpiC0AAAAAAAAAAAAAFl94AQBr",
        bottom: -68,
        right: -45,
        height: "303px",
      },
      {
        src: "https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/F6vSTbj8KpYAAAAAAAAAAAAAFl94AQBr",
        bottom: 0,
        left: 0,
        width: "331px",
      },
    ],
    links: [],
    menuHeaderRender: undefined,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => {
      // if (initialState?.loading) return <PageLoading />;
      return (
        <>
          <AuthenticationStateSync />
          {children}
          {isDev && (
            <SettingDrawer
              disableUrlParams
              enableDarkTheme
              settings={initialState?.settings}
              onSettingChange={(settings) => {
                setInitialState((preInitialState) => ({
                  ...preInitialState,
                  settings,
                }));
              }}
            />
          )}
        </>
      );
    },
    ...initialState?.settings,
  };
};

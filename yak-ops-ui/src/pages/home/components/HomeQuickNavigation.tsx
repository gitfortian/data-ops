import { history, useIntl } from "@umijs/max";
import type { SVGProps } from "react";

interface QuickNavigationItem {
  key: string;
  labelId: string;
  path: string;
  icon: React.ComponentType<SVGProps<SVGSVGElement>>;
}

/**
 * Temporary icons.
 *
 * These SVGs only reserve the icon size and visual structure.
 * Replace them with the final Yak Ops SVG assets later.
 */
function DigitalScreenIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="8441"
      width="24"
      height="24"
    >
      <path
        d="M803.7 803.8C737 870.5 557.6 788 396.8 627.2 236 466.4 153.6 286.9 220.3 220.3c66.7-66.7 246.1 15.8 406.9 176.6C788 557.6 870.4 737.1 803.7 803.8z m-22.6-22.7c48-48.1-27.6-212.7-176.6-361.7S290.9 194.8 242.8 242.8c-48.1 48 27.6 212.7 176.6 361.7s313.7 224.7 361.7 176.6z"
        fill="#FFA243"
        p-id="8442"
      ></path>
      <path
        d="M220.3 803.8c-66.7-66.7 15.8-246.1 176.6-406.9C557.6 236 737.1 153.6 803.7 220.3c66.7 66.7-15.8 246.1-176.6 406.9C466.5 788 286.9 870.4 220.3 803.8z m22.6-22.7c48 48 212.7-27.6 361.7-176.6S829.2 291 781.1 242.9s-212.7 27.6-361.7 176.6C270.5 568.4 194.8 733.1 242.9 781.1z"
        fill="#FFA243"
        p-id="8443"
      ></path>
      <path
        d="M496.7 104.8v331h32v-331h-32z m30.6 842.4V574.8h-32v372.4h32z"
        fill="#FFA243"
        p-id="8444"
      ></path>
      <path
        d="M507.1 234.2c-49.4 0-89.4-40-89.5-89.4 0-49.4 40-89.4 89.4-89.5 49.4 0 89.4 40.1 89.4 89.5 0.2 49.4-39.9 89.4-89.3 89.4z m0 734.5c-49.4 0-89.4-40-89.5-89.4 0-49.4 40-89.4 89.4-89.5 49.4 0 89.4 40.1 89.4 89.5 0.2 49.3-39.9 89.3-89.3 89.4zM127.6 496h309.9v32H127.6v-32z m768.8 2.8H588.3v32h308.1v-32z"
        fill="#FFA243"
        p-id="8445"
      ></path>
      <path
        d="M879.2 601.4c-49.4 0-89.4-40-89.5-89.4s40-89.4 89.4-89.5c49.4 0 89.4 40.1 89.4 89.5 0.1 49.4-39.9 89.4-89.3 89.4z m-734.4 0c-49.4 0-89.4-40-89.5-89.4 0-49.4 40-89.4 89.4-89.5 49.4 0 89.4 40.1 89.4 89.5 0.1 49.4-39.9 89.4-89.3 89.4z m367.2 0c-49.4 0-89.4-40-89.5-89.4s40-89.4 89.4-89.5c49.4 0 89.4 40.1 89.4 89.5 0.1 49.4-39.9 89.4-89.3 89.4z m0-32c31.7 0 57.4-25.7 57.4-57.4s-25.7-57.4-57.4-57.4-57.4 25.7-57.4 57.4 25.7 57.4 57.4 57.4z"
        fill="#FFA243"
        p-id="8446"
      ></path>
    </svg>
  );
}

function DataCatalogIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="52074"
      width="24"
      height="24"
    >
      <path
        d="M64 64m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#4A90E2"
        p-id="52075"
      ></path>
      <path
        d="M64 384m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#4A90E2"
        p-id="52076"
      ></path>
      <path
        d="M64 704m64 0l704 0q64 0 64 64l0 128q0 64-64 64l-704 0q-64 0-64-64l0-128q0-64 64-64Z"
        fill="#94C5FF"
        p-id="52077"
      ></path>
      <path
        d="M192 128h128v128H192zM192 448h128v128H192zM192 768h128v128H192z"
        fill="#FFFFFF"
        p-id="52078"
      ></path>
    </svg>
  );
}

function DashboardIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="40987"
      width="24"
      height="24"
    >
      <path
        d="M51.2 512h153.6v256H51.2v-256z m-51.2 358.4h1024v102.4H0v-102.4z m307.2-460.8h153.6v358.4H307.2V409.6z m256-358.4h153.6v716.8h-153.6V51.2z m256 256h153.6v460.8h-153.6V307.2z"
        fill="#033294"
        p-id="40988"
      ></path>
    </svg>
  );
}

function DataServiceIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 1024 1024"
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      p-id="44853"
      width="24"
      height="24"
    >
      <path
        d="M128 1024h768a128 128 0 0 0 128-128V128a128 128 0 0 0-128-128H128a128 128 0 0 0-128 128v768a128 128 0 0 0 128 128z"
        fill="#39C19D"
        p-id="44854"
      ></path>
      <path
        d="M230.08 449.92L132.288 686.72l-1.856 5.184a56.768 56.768 0 0 0 54.336 73.28h165.632l-120.32-315.136zM599.872 256h-102.4l172.16 457.856c11.136 29.696 39.36 49.472 71.04 49.92l107.264 1.344-176.192-459.648A76.992 76.992 0 0 0 599.872 256zM375.168 256h-102.4l172.16 457.856c11.136 29.696 39.296 49.472 71.04 49.92l107.264 1.344-176.128-459.648A76.992 76.992 0 0 0 375.168 256z"
        fill="#FFFFFF"
        p-id="44855"
      ></path>
    </svg>
  );
}

function MoreArrowIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      {...props}
    >
      <path
        d="M6 3.5 10.5 8 6 12.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const QUICK_NAVIGATION_ITEMS: QuickNavigationItem[] = [
  {
    key: "digital-screen",
    labelId: "pages.home.quickNavigation.digitalScreen",
    path: "/digital-screen",
    icon: DigitalScreenIcon,
  },
  {
    key: "data-catalog",
    labelId: "pages.home.quickNavigation.dataCatalog",
    path: "/data-analysis/data-catalog",
    icon: DataCatalogIcon,
  },
  {
    key: "dashboard",
    labelId: "pages.home.quickNavigation.dashboard",
    path: "/dashboard",
    icon: DashboardIcon,
  },
  {
    key: "data-service",
    labelId: "pages.home.quickNavigation.dataService",
    path: "/data-service",
    icon: DataServiceIcon,
  },
];

export default function HomeQuickNavigation() {
  const intl = useIntl();

  const handleNavigate = (path: string) => {
    history.push(path);
  };

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({
            id: "pages.home.quickNavigation.title",
          })}
        </h2>

        {/*
         * 暂时没有独立的“快捷导航列表页”，
         * 所以这里先保留截图中的视觉结构，不做错误跳转。
         * 后续有统一入口后直接改成 button 即可。
         */}
        <div className="flex shrink-0 items-center gap-0.5 text-[12px] text-[#666b75]">
          <span>
            {intl.formatMessage({
              id: "pages.home.common.viewMore",
            })}
          </span>

          <MoreArrowIcon className="h-3.5 w-3.5" />
        </div>
      </header>

      <div className="mt-4 grid grid-cols-4 gap-1">
        {QUICK_NAVIGATION_ITEMS.map((item) => {
          const Icon = item.icon;

          return (
            <button
              key={item.key}
              type="button"
              onClick={() => handleNavigate(item.path)}
              className="
                group
                flex
                min-w-0
                flex-col
                items-center
                justify-center
                gap-2
                border-0
                bg-transparent
                px-1
                py-2
                text-center
                outline-none
                transition-colors
                duration-150
                hover:bg-[#f7f8fa]
                focus-visible:bg-[#f7f8fa]
              "
            >
              <div
                className="
                  flex
                  h-11
                  w-11
                  shrink-0
                  items-center
                  justify-center
                  rounded-[9px]
                  border
                  border-[#eceef1]
                  bg-white
                  text-[#5b6270]
                  shadow-[0_1px_3px_rgba(31,35,41,0.04)]
                  transition-all
                  duration-150
                  group-hover:border-[#e2e5e9]
                "
              >
                <Icon className="h-[22px] w-[22px]" />
              </div>

              <span
                className="
                  block
                  w-full
                  truncate
                  text-[12px]
                  font-normal
                  leading-5
                  text-[#30333b]
                "
              >
                {intl.formatMessage({
                  id: item.labelId,
                })}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

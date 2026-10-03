import LoginPanel from "./LoginPanel";
import { useIntl } from '@umijs/max';

export default function LoginPage() {
  const intl = useIntl();
  return (
    <main className="yak-login-page h-screen overflow-y-auto bg-[#fbfbfa] text-[#171717]">
      <div className="mx-auto flex min-h-screen w-full max-w-[1540px] flex-col px-6 py-4 sm:px-10 lg:px-12 lg:pb-6 lg:pt-5 xl:px-16">
        <header className="flex h-11 shrink-0 items-center">
          <img
            src="/logo1.png"
            alt="Data Ops 一体化"
            className="h-9 w-auto select-none object-contain sm:h-9"
            draggable={false}
          />
        </header>

        <div className="flex flex-1 items-center pt-6 lg:pt-0">
          <section className="flex min-w-0 flex-1 items-center justify-center py-8 lg:py-10">
            <div className="w-full max-w-[620px]">
              <div
                className="mb-10 text-center"
                style={{ fontFamily: "'YakOps', Inter, sans-serif" }}
              >
                <h1 className="m-0 text-[36px] font-normal leading-[1.1] tracking-[-0.045em] text-[#171717] sm:text-[60px] lg:text-[64px]">
                  {intl.formatMessage({ id: 'pages.login.title', defaultMessage: '让数据工作更简单' })}
                </h1>

                <p className="mt-5 text-[16px] leading-7 text-[#555] sm:text-[17px]">
                  {intl.formatMessage({ id: 'pages.login.subtitle', defaultMessage: '在一个工作空间中完成数据开发、治理与消费。' })}
                </p>
              </div>

              <div className="mx-auto w-full max-w-[430px]">
                <LoginPanel />
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}

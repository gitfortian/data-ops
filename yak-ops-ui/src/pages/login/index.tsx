import LoginPanel from "./LoginPanel";

export default function LoginPage() {
  return (
    <main className="yak-login-page h-screen overflow-y-auto bg-[#fbfbfa] text-[#171717]">
      <div className="mx-auto flex min-h-screen w-full max-w-[1540px] flex-col px-6 py-4 sm:px-10 lg:px-12 lg:pb-6 lg:pt-5 xl:px-16">
        <header className="flex h-11 shrink-0 items-center">
          <img
            src="/logo1.png"
            alt="Yak Ops 一体化"
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
                <h1 className="m-0 text-[46px] font-normal leading-[1.01] tracking-[-0.045em] text-[#171717] sm:text-[60px] lg:text-[64px]">
                  Data ops,  &nbsp; simplified.
                </h1>

                <p className="mt-5 text-[16px] leading-7 text-[#555] sm:text-[17px]">
                  One workspace for your data.
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

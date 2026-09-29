function AtmosphereBand() {
  return (
    <div className="absolute left-[330px] right-3 top-[1px] h-[106px] overflow-hidden rounded-[999px] bg-[linear-gradient(90deg,rgba(247,248,250,0)_0%,rgba(245,248,251,0.72)_15%,rgba(230,240,246,0.90)_50%,rgba(245,248,251,0.72)_85%,rgba(247,248,250,0)_100%)] opacity-90">
      <div className="absolute inset-0 opacity-[0.30] [background-image:radial-gradient(circle,rgba(255,255,255,0.95)_0.8px,transparent_0.9px)] [background-size:7px_7px] [mask-image:linear-gradient(90deg,transparent_0%,#000_15%,#000_85%,transparent_100%)]" />
      <div className="absolute left-1/2 top-1/2 h-[170%] w-[42%] -translate-x-1/2 -translate-y-1/2 bg-[radial-gradient(ellipse_at_center,rgba(186,220,237,0.57)_0%,rgba(210,231,241,0.31)_45%,rgba(255,255,255,0)_78%)] blur-[20px]" />
      <div className="absolute inset-x-[12%] top-0 h-[34%] bg-gradient-to-b from-white/60 to-transparent blur-[5px]" />
      <div className="absolute inset-y-0 left-0 w-[18%] bg-gradient-to-r from-[#f7f8fa] via-[#f7f8fa]/55 to-transparent" />
      <div className="absolute inset-y-0 right-0 w-[18%] bg-gradient-to-l from-[#f7f8fa] via-[#f7f8fa]/55 to-transparent" />
    </div>
  );
}

export function HomeBackground() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <AtmosphereBand />
      <div className="absolute -right-[40px] -top-[185px] h-[390px] w-[68%] bg-[radial-gradient(ellipse_at_center,rgba(158,215,239,0.22)_0%,rgba(190,224,239,0.10)_46%,rgba(255,255,255,0)_74%)] blur-[24px]" />
      <div className="absolute -bottom-[190px] -right-[170px] h-[420px] w-[620px] bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.28),rgba(255,255,255,0)_70%)] blur-[20px]" />
      <div className="absolute right-[2%] top-0 h-[102px] w-[62%] opacity-[0.08] [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)] [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_70%,transparent_100%)]" />
    </div>
  );
}

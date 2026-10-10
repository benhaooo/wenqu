/** 登录页左侧的产品介绍面板。 */
export function BrandPanel() {
  return (
    <section
      aria-label="产品介绍"
      className="flex flex-[1_1_480px] flex-col justify-between gap-12 bg-brand-deep px-16 py-14 text-brand-deep-foreground max-sm:px-6"
    >
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-9 items-center justify-center rounded-[9px] bg-brand-deep-foreground text-lg font-bold text-brand-deep"
        >
          渠
        </span>
        <span className="text-xl font-bold tracking-[0.08em]">问渠</span>
      </div>

      <div className="flex max-w-[520px] flex-col gap-7">
        <h1 className="text-[44px] leading-tight font-bold text-white max-sm:text-[32px]">
          团队的文档，
          <br />
          有问必有据。
        </h1>
        <p className="text-base leading-[1.8] text-brand-deep-foreground/80">
          一起写文档、导入资料，AI 只依据团队自己的内容回答，每个结论都能追溯到原文。
        </p>
        <div
          aria-hidden="true"
          className="flex flex-col gap-2.5 rounded-xl bg-brand-deep-raised px-5 py-[18px] leading-[1.7]"
        >
          <span className="text-xs text-brand-deep-muted">问：新人第一周要完成哪些事？</span>
          <span className="text-white">
            配置开发环境、跑通本地服务、提交第一个合并请求
            <CiteMark>1</CiteMark>
          </span>
          <span className="flex items-center gap-2 text-xs text-brand-deep-muted">
            <CiteMark>1</CiteMark>新人入门 / 第一周清单
          </span>
        </div>
      </div>

      <span className="text-xs text-brand-deep-muted">问渠那得清如许，为有源头活水来。</span>
    </section>
  )
}

function CiteMark({ children }: { children: string }) {
  return (
    <span className="ml-1 rounded-sm bg-cite px-1.5 text-xs font-semibold text-cite-foreground">
      {children}
    </span>
  )
}

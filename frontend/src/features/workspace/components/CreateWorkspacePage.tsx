import { BrandMark } from '@/shared/ui/brand-mark'
import { CreateWorkspaceForm } from './CreateWorkspaceForm'

/** 首次使用：创建团队空间。 */
export function CreateWorkspacePage() {
  return (
    <div className="flex min-h-svh flex-col items-center gap-10 bg-canvas px-6 pt-10 pb-16">
      <header className="flex w-full max-w-[720px] items-center gap-3">
        <BrandMark className="size-8 rounded-lg text-base" />
        <span className="text-lg font-bold tracking-[0.06em]">问渠</span>
      </header>
      <main className="w-full max-w-[720px]">
        <section
          aria-labelledby="create-title"
          className="flex flex-col gap-6 rounded-2xl border border-border-subtle bg-background px-10 py-9 max-sm:px-5"
        >
          <div className="flex flex-col gap-1.5">
            <h1 id="create-title" className="text-[28px] font-bold">
              创建团队空间
            </h1>
            <p className="text-muted-foreground">
              空间是团队共享文档的范围，创建后可以邀请成员加入。
            </p>
          </div>
          <CreateWorkspaceForm />
        </section>
      </main>
    </div>
  )
}

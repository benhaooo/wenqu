import { useGetSystemHealth } from '@/shared/api/generated/system/system'
import { cn } from '@/shared/lib/cn'

export function HomePage() {
  const { data, isPending, isError } = useGetSystemHealth({
    query: { select: (result) => result.data },
  })

  return (
    <main className="mx-auto max-w-xl px-4 py-16">
      <h1 className="text-3xl font-semibold">问渠</h1>
      <p className="mt-2 text-neutral-500">团队知识库</p>

      <section className="mt-10 rounded-lg border border-neutral-200 p-4">
        <h2 className="font-medium">系统状态</h2>
        {isPending && <p className="mt-2 text-sm text-neutral-500">检查中…</p>}
        {isError && <p className="mt-2 text-sm text-red-600">无法连接后端</p>}
        {data && (
          <ul className="mt-2 space-y-1 text-sm">
            {data.components.map((component) => (
              <li key={component.name} className="flex justify-between">
                <span>{component.name}</span>
                <span className={cn(component.status === 'UP' ? 'text-green-600' : 'text-red-600')}>
                  {component.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

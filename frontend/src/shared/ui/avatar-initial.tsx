import { cn } from '@/shared/lib/cn'

/** 头像底色，按用户 id 固定分配；均满足白字 4.5:1 对比度。 */
const COLORS = ['#18211e', '#b4531b', '#2f5c8a', '#1d6b57', '#6b4c9a', '#5b6661']

/** 以昵称首字作为头像。 */
export function Avatar({ name, id, className }: { name: string; id: number; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-[30px] shrink-0 items-center justify-center rounded-full text-[13px] font-semibold text-white',
        className,
      )}
      style={{ background: COLORS[Math.abs(id) % COLORS.length] }}
    >
      {name.slice(0, 1)}
    </span>
  )
}

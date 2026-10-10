import { cn } from '@/shared/lib/cn'

/** 品牌标识「渠」字方块。 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-7 shrink-0 items-center justify-center rounded-[7px] bg-primary text-[15px] font-bold text-primary-foreground',
        className,
      )}
    >
      渠
    </span>
  )
}

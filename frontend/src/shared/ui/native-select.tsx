import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

/** 原生下拉框：选项少、无需搜索时使用，键盘和读屏支持最好。 */
export function NativeSelect({ className, ...props }: ComponentProps<'select'>) {
  return (
    <select
      className={cn(
        'h-9 rounded-md border border-input bg-background px-2.5 text-foreground outline-none focus-visible:border-primary focus-visible:ring-3 focus-visible:ring-primary-soft',
        className,
      )}
      {...props}
    />
  )
}

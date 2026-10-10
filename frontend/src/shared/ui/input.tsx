import * as React from 'react'
import { cn } from '@/shared/lib/cn'

function Input({ className, type, ...props }: React.ComponentProps<'input'>) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        'h-11 w-full min-w-0 rounded-md border border-input bg-background px-3.5 text-sm text-foreground transition-[border-color,box-shadow] outline-none placeholder:text-muted-foreground focus-visible:border-primary focus-visible:ring-3 focus-visible:ring-primary-soft disabled:cursor-not-allowed disabled:bg-canvas disabled:opacity-60 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive-soft',
        className,
      )}
      {...props}
    />
  )
}

export { Input }

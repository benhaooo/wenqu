import type { ReactNode } from 'react'

/** 表单内的错误提示条。 */
export function InlineAlert({ children }: { children: ReactNode }) {
  return (
    <p
      role="alert"
      className="rounded-md bg-destructive-soft px-3 py-2.5 text-[13px] text-destructive-strong"
    >
      {children}
    </p>
  )
}
